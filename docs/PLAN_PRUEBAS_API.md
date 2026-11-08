# Plan de pruebas de la API

## Objetivo y preparación

Validar el contrato HTTP, los permisos y las reglas del flujo de eventos de
`LoginController` e `InterviewController`. Los resultados esperados se basan
en los DTO, servicios y manejador de excepciones del proyecto.

La colección ejecutable está en
[postman/Interview.postman_collection.json](postman/Interview.postman_collection.json).
Las [instrucciones de ejecución](../README.md#postman) explican la importación,
el environment local y el uso de Newman. Ejecutar sus 110 peticiones en orden,
empezando en L01, sobre la API local con los usuarios de `interview.sql`:

| Usuario | Rol | Operaciones |
| --- | --- | --- |
| `reportante1`, `reportante2` | `reportante` | Crear eventos; listar y consultar los propios. |
| `revisor1` | `revisor` | Consultar todos; cambiar estados; crear y completar acciones. |

El login no requiere contraseña. El token es un mock Base64 de
`{"scheme":"login","username":"reportante1","role":"reportante"}`.
La API consulta el rol actual en BD en cada operación. Se aceptan
`Authorization: Bearer <token>` y `Authorization: <token>`.

## Observar el comportamiento con logs

La aplicación escribe en consola y en `logs/interview.log` con nivel `INFO`
por defecto. `APP_LOG_FILE` permite cambiar el archivo. Los archivos rotan al
alcanzar 10 MB y se conservan siete períodos de historial de rotación.

Para ver también el detalle de autenticación, permisos, alcance de consultas,
bloqueos de escritura, validaciones e historial, iniciar la API con `DEBUG`:

```powershell
$env:APP_LOG_LEVEL = 'DEBUG'
.\mvnw.cmd spring-boot:run
```

En otra terminal, desde la raíz del proyecto, seguir el archivo mientras se
ejecuta la colección de Postman:

```powershell
Get-Content .\logs\interview.log -Tail 50 -Wait
```

| Nivel | Información |
| --- | --- |
| `INFO` | Inicio y fin HTTP con duración; entrada y resultado en controladores; login, consultas y escrituras en servicios, con usuario, IDs, estados y cantidades. |
| `DEBUG` | Identidad actual de BD, permisos, filtro por propietario, bloqueos, reglas de negocio e historial. |
| `WARN` | Rechazos esperados con método, ruta, estado HTTP y motivo: `400`, `401`, `403`, `404`, `405`, `409` y `415`. Incluye errores detectados antes de entrar al controlador, como JSON mal formado. |
| `ERROR` | Fallos internos con método, ruta, estado HTTP y stack trace. |

Cada petición recibe un UUID generado por el servidor en el header de respuesta
`X-Request-ID`. Ese valor aparece como `requestId` en consola y archivo, y une
los mensajes del filtro HTTP, controlador, servicio y manejador de errores.
El filtro registra `HTTP iniciado` antes de leer el JSON y `HTTP finalizado`
con el estado de respuesta y `durationMs`, también para peticiones rechazadas.
Si una excepción escapa del procesamiento HTTP, registra `HTTP fallido` en
`ERROR` con su stack trace. El contexto se limpia al terminar cada petición.

Para localizar una petición desde su header de respuesta en Postman:

```powershell
Select-String -Path .\logs\interview.log -SimpleMatch 'requestId=<X-Request-ID>'
```

Para el flujo F10 → W01 → W11 → W14, buscar `eventId=<id>` para seguir el
avance a revisión, la creación de la acción, su finalización y el cierre.
`actionId` identifica la acción; `previousStatus` y `nextStatus` muestran
cada transición. W03 debe producir un `WARN` con `status=409` por acciones
pendientes, sin un mensaje `Solicitud completada`. Su mensaje
`HTTP finalizado` debe mostrar `status=409`.

Los mensajes de escritura del servicio indican que se guardaron los cambios
dentro de la transacción. El mensaje `Solicitud completada` del controlador
aparece cuando el servicio retorna después del commit; si falla el commit,
el manejador registra el rechazo o error. `HTTP finalizado` se escribe después
del procesamiento y la serialización de la respuesta. Los logs operativos
complementan el historial de BD y no lo sustituyen.

Los loggers de la aplicación no imprimen tokens, el header `Authorization`
ni cuerpos completos de solicitudes o respuestas. Para volver al nivel
habitual, usar `$env:APP_LOG_LEVEL = 'INFO'` y reiniciar la aplicación.

## Endpoints y JSON

Salvo login, todos requieren `Authorization`. Los cuerpos JSON requieren
`Content-Type: application/json`. `PATCH .../complete` no necesita cuerpo.

| Método | Endpoint | Cuerpo | Respuesta exitosa |
| --- | --- | --- | --- |
| POST | `/api/login` | `{"username":"reportante1"}` | `200`, `{"token":"..."}`. |
| GET | `/api/events` | Sin cuerpo. | `200`, arreglo de resúmenes. |
| POST | `/api/create-event` | Evento del ejemplo siguiente. | `201`, texto `Event created successfully`. |
| GET | `/api/events/{eventId}` | Sin cuerpo. | `200`, detalle con `actions` e `history`. |
| PATCH | `/api/events/{eventId}/status` | `{"status":"en revisión"}` o `{"status":"cerrado"}`. | `204`, cuerpo vacío. |
| POST | `/api/events/{eventId}/actions` | Acción del ejemplo siguiente. | `201`, acción creada con `actionId` y estado `pendiente`. |
| PATCH | `/api/events/{eventId}/actions/{actionId}/complete` | Sin cuerpo. | `204`, cuerpo vacío. |

Evento de ejemplo; sustituir la fecha por una que no sea futura:

```json
{
  "title": "Derrame de aceite en bodega",
  "description": "Se detectó aceite junto a la zona de carga.",
  "date": "2026-11-01",
  "area": "Bodega"
}
```

Acción de ejemplo; la fecha objetivo debe ser igual o posterior al día del evento:

```json
{
  "description": "Limpiar el área y revisar el origen del derrame",
  "responsible": "Persona QA",
  "targetDate": "2026-11-15T00:00:00"
}
```

Ejemplo de detalle al crear un evento; los identificadores y la fecha del
historial se generan en cada ejecución:

```json
{
  "eventId": 5,
  "title": "Derrame de aceite en bodega",
  "description": "Se detectó aceite junto a la zona de carga.",
  "date": "2026-11-01",
  "area": "Bodega",
  "status": "reportado",
  "reporter": "reportante1",
  "actions": [],
  "history": [
    {
      "changeHistoryId": 7,
      "changeDate": "2026-11-08T15:00:00",
      "actor": "reportante1",
      "description": "Evento creado en estado: reportado"
    }
  ]
}
```

Ejemplo de error al omitir `username`, con `Content-Type: application/problem+json`:

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "El nombre de usuario es obligatorio.",
  "instance": "/api/login"
}
```

La serialización actual omite `type` cuando su valor es `about:blank`.
La colección acepta esa omisión y valida `type` si está presente.
Los casos negativos comprueban el estado, formato y estructura del error,
incluyendo que `instance` corresponda a la ruta y que no se expongan acciones,
historial ni campos de excepción. El texto de `detail` debe ser legible y no
vacío; su redacción exacta no forma parte de esas aserciones.

## Reglas que deben cumplirse

| Regla | Criterio esperado |
| --- | --- |
| Nombre para login | Obligatorio y no compuesto solo por espacios; usuario desconocido devuelve `401`. |
| Token | Base64 de JSON con `scheme=login`, usuario existente y rol coincidente con BD. |
| Rol insuficiente | `403`; credencial inválida o ausente, `401`. |
| Acceso a detalle ajeno | Reportante recibe `404`, igual que ante un ID inexistente. |
| Identidad | El query `username`, el campo extra `role` y el campo extra `reporter` no conceden permisos ni cambian el propietario. |
| Título | De 5 a 120 caracteres, sin valor vacío ni solo espacios. |
| Descripción de evento | De 1 a 2000 caracteres, sin valor vacío ni solo espacios. |
| Fecha de evento | Obligatoria, formato `yyyy-MM-dd`, no futura respecto a UTC. |
| Área | `Planta Norte`, `Planta Sur`, `Bodega` o `Administración`, respetando escritura. |
| Transiciones | Solo `reportado → en revisión → cerrado`; saltos, repeticiones y retrocesos devuelven `409`. |
| Descripción de acción | De 5 a 500 caracteres, sin valor vacío ni solo espacios. |
| Responsable | De 1 a 30 caracteres, sin valor vacío ni solo espacios. |
| Fecha objetivo | Obligatoria, fecha y hora sin zona; su día no es anterior al del evento. |
| Acciones | Solo en revisión; pertenecen al evento de la URL. |
| Completar acción | Solo pendiente; repetir genera `409`. |
| Cierre | `409` si hay acciones pendientes; permitido si no hay acciones. |
| Auditoría | Cada operación exitosa agrega actor, fecha y descripción en la misma transacción. Los rechazos conservan el historial. |

## Orden y criterio de aceptación

Las carpetas 01–03 preparan identidades y prueban entradas inválidas. La 04
crea dos eventos de distintos propietarios y captura los IDs. Las carpetas
05–07 ejecutan el flujo y comprueban la auditoría. La 08 cubre límites mínimos.
Los máximos válidos se cubren en E03 y W01. Se ejercitan las cuatro áreas.

El flujo principal debe terminar con una acción completada, evento cerrado
y exactamente cinco entradas de historial: creación, avance a revisión,
creación de acción, finalización de acción y cierre. El evento del segundo
reportante termina cerrado, sin acciones, con tres entradas de historial.

La ejecución se acepta cuando todas las aserciones pasan. Cada caso lleva un
ID estable para registrar fallos. Guardar el reporte de Newman con la versión
o commit probado y la dirección de ejecución permite reproducir el resultado.

Verificación de trazabilidad: el 8 de noviembre de 2026 se ejecutó la colección
con Newman 6.2.1 contra una instancia local en `http://127.0.0.1:18089`, con
Java 21 y H2. Pasaron **110 peticiones y 246 aserciones, sin fallos**. El reporte
generado está en `target/trazabilidad-newman-report.json`. Se cruzaron sus
110 headers `X-Request-ID` con `target/trazabilidad-api.log`, en nivel `DEBUG`:
todos tenían inicio, fin, estado correcto y duración. Los 75 rechazos tenían
`WARN` y no tenían `Solicitud completada`; no se encontraron credenciales ni
cuerpos completos de entrada en ese archivo.

También pasaron **119 pruebas Java, sin fallos**, incluidas las 10 de
`RequestLoggingIntegrationTests`, que comprueban correlación entre capas,
errores anteriores al controlador, ausencia de datos sensibles, IDs distintos,
restauración del contexto y excepciones que escapan del filtro. El resultado
de Maven está en `target/trazabilidad-tests.log`. Los reportes y logs de esta
verificación son archivos temporales dentro de `target/`.

Verificación adicional de E06: la colección incluye ahora `baseUrl` y los
tres nombres de usuario predeterminados para poder usarla sin el archivo de
environment. Pasaron nuevamente 110 peticiones y 246 aserciones en cada modo:
con `Interview - Local` y sin ese archivo. Los reportes están en
`target/postman-e06-environment-report.json` y
`target/postman-e06-defaults-report.json`. Cinco comprobaciones del script
con Newman confirmaron que acepta eventos propios, detecta un propietario
ajeno, explica una variable ausente o vacía y respeta la sobrescritura del
environment; el resumen está en `target/postman-e06-script-checks.json`.

## Pruebas complementarias

Estas condiciones requieren cambiar la BD, introducir fallos o coordinar
peticiones. No se incluyen como peticiones HTTP de la colección porque la
API no ofrece endpoints de administración o de inyección de errores.

| Condición | Comprobación | Cobertura disponible |
| --- | --- | --- |
| Cambio de propietario en BD | El antiguo dueño deja de ver el evento y el nuevo puede consultarlo en la siguiente petición. | `EventWorkflowIntegrationTests.ownershipChangesInTheDatabaseImmediatelyAffectListsAndDetails`. |
| Cambio de rol de revisor | El token antiguo devuelve `401`; un nuevo login refleja el nuevo rol y sus restricciones. | `EventWorkflowIntegrationTests.demotingAReviewerDoesNotLeaveAccessThroughAnOldTokenOrANewLogin`. |
| Eliminación y restauración de rol | Acceso denegado con `403` mientras falta el rol; se recalcula al restaurarlo. | `EventWorkflowIntegrationTests.removingAndRestoringTheDatabaseRoleChangesAuthorizationImmediately`. |
| Eliminación de usuario | Un token emitido previamente deja de servir y devuelve `401`. | `EventWorkflowIntegrationTests.aDeletedUserCannotContinueUsingAnIssuedToken`. |
| Fallo al guardar historial | La transacción revierte la creación del evento y no deja datos parciales. | `EventWorkflowIntegrationTests.aHistoryWriteFailureRollsBackTheEventCreation`. |
| Cierre y creación de acción simultáneos | Si la acción se crea primero, el cierre devuelve `409`; si el cierre termina primero, la acción devuelve `409`. Nunca queda un evento cerrado con acción pendiente. | Caso adicional a ejecutar con dos clientes coordinados; no automatizado en esta colección. |
| Completar una acción simultáneamente | Una operación devuelve `204` y la otra `409`; se guarda una sola entrada de finalización. | Caso adicional a ejecutar con dos clientes coordinados; no automatizado en esta colección. |

Las pruebas Java se ejecutan desde la raíz con `.\mvnw.cmd test`.
La colección no pretende validar que Base64 impida falsificar identidades:
el mecanismo implementado es un login simulado sin firma, según el contrato
del proyecto.

## Catálogo ejecutable

En las rutas de la tabla, `{{eventId}}`, `{{otherEventId}}`, `{{actionId}}` y
`{{minActionEventId}}` son variables capturadas automáticamente. El cuerpo JSON
completo de cada caso está en su petición de Postman, incluidos los casos
negativos. Los campos largos se generan en L01 con su longitud exacta.

<!-- Catálogo de las peticiones incluidas en la colección. -->

### 01 - Login

Credenciales semilla, contrato de token, datos inválidos y contrato HTTP. L01 inicia una ejecución independiente.

| Caso | Método y endpoint | Comportamiento | HTTP |
| --- | --- | --- | --- |
| L01 | `POST /api/login` | Login de reporter1Username | 200 |
| L02 | `POST /api/login` | Login de reporter2Username | 200 |
| L03 | `POST /api/login` | Login de reviewerUsername | 200 |
| L04 | `POST /api/login` | Username ausente | 400 |
| L05 | `POST /api/login` | Username null | 400 |
| L06 | `POST /api/login` | Username vacío | 400 |
| L07 | `POST /api/login` | Username con espacios | 400 |
| L08 | `POST /api/login` | Usuario inexistente | 401 |
| L09 | `POST /api/login` | JSON mal formado | 400 |
| L10 | `POST /api/login` | Sin cuerpo | 400 |
| L11 | `POST /api/login` | Cuerpo null | 400 |
| L12 | `POST /api/login` | Content-Type no soportado | 415 |
| L13 | `GET /api/login` | Método no permitido | 405 |
| L14 | `POST /api/login` | El campo role no eleva permisos | 200 |

### 02 - Autorización

Identidad simulada Base64, rechazo de tokens y listado por rol.

| Caso | Método y endpoint | Comportamiento | HTTP |
| --- | --- | --- | --- |
| A01 | `GET /api/events` | Sin Authorization | 401 |
| A02 | `GET /api/events` | Token no Base64 | 401 |
| A03 | `GET /api/events` | Base64 con JSON inválido | 401 |
| A04 | `GET /api/events` | Scheme incorrecto | 401 |
| A05 | `GET /api/events` | Rol ausente | 401 |
| A06 | `GET /api/events` | Rol distinto del almacenado en BD | 401 |
| A07 | `GET /api/events` | Token de usuario inexistente | 401 |
| A08 | `GET /api/events` | Token con username vacío | 401 |
| A09 | `GET /api/events` | Base64 con null | 401 |
| A10 | `GET /api/events` | Se acepta el token sin prefijo Bearer | 200 |
| A11 | `GET /api/events` | Reportante 2 ve únicamente sus eventos | 200 |
| A12 | `GET /api/events` | Revisor ve los eventos de ambos reportantes | 200 |

### 03 - Validación de eventos

Casos negativos aislados; cada petición cambia un campo de un cuerpo válido. No deben crear eventos.

| Caso | Método y endpoint | Comportamiento | HTTP |
| --- | --- | --- | --- |
| V01 | `POST /api/create-event` | Título ausente | 400 |
| V02 | `POST /api/create-event` | Título de 4 caracteres | 400 |
| V03 | `POST /api/create-event` | Título de 121 caracteres | 400 |
| V04 | `POST /api/create-event` | Título en blanco | 400 |
| V05 | `POST /api/create-event` | Descripción ausente | 400 |
| V06 | `POST /api/create-event` | Descripción en blanco | 400 |
| V07 | `POST /api/create-event` | Descripción de 2001 caracteres | 400 |
| V08 | `POST /api/create-event` | Fecha ausente | 400 |
| V09 | `POST /api/create-event` | Fecha null | 400 |
| V10 | `POST /api/create-event` | Fecha futura | 400 |
| V11 | `POST /api/create-event` | Formato de fecha inválido | 400 |
| V12 | `POST /api/create-event` | Área fuera del catálogo | 400 |
| V13 | `POST /api/create-event` | Área con mayúsculas incorrectas | 400 |
| V14 | `POST /api/create-event` | Área ausente | 400 |
| V15 | `POST /api/create-event` | Revisor no puede crear eventos | 403 |
| V16 | `POST /api/create-event` | Crear sin token | 401 |

### 04 - Creación y acceso

Crea dos eventos propios de esta ejecución. Recupera sus IDs por un título único porque create-event devuelve texto.

| Caso | Método y endpoint | Comportamiento | HTTP |
| --- | --- | --- | --- |
| E01 | `POST /api/create-event` | Crear evento e intentar suplantar al reportante | 201 |
| E02 | `GET /api/events` | Recuperar eventId del listado propio | 200 |
| E03 | `POST /api/create-event` | Crear evento de reportante 2 con máximos válidos | 201 |
| E04 | `GET /api/events` | Recuperar otherEventId sin ver el evento ajeno | 200 |
| E05 | `GET /api/events` | Revisor ve ambos eventos nuevos | 200 |
| E06 | `GET /api/events?username={{reviewerUsername}}&role=revisor` | El query username no cambia la identidad | 200 |
| E07 | `GET /api/events/{{eventId}}` | Detalle propio e historial inicial | 200 |
| E08 | `GET /api/events/{{eventId}}` | Reportante 2 no puede consultar el evento de reportante 1 | 404 |
| E09 | `GET /api/events/{{otherEventId}}` | Reportante 1 no puede consultar el evento de reportante 2 | 404 |
| E10 | `GET /api/events/{{otherEventId}}` | Revisor puede consultar cualquier detalle | 200 |
| E11 | `GET /api/events/0` | ID cero | 400 |
| E12 | `GET /api/events/-1` | ID negativo | 400 |
| E13 | `GET /api/events/abc` | ID no numérico | 400 |
| E14 | `GET /api/events/2147483647` | Evento inexistente | 404 |
| E15 | `GET /api/events/{{eventId}}` | Detalle sin token | 401 |

### 05 - Estados y permisos

El evento principal comienza reportado. Los rechazos deben conservar estado e historial.

| Caso | Método y endpoint | Comportamiento | HTTP |
| --- | --- | --- | --- |
| F01 | `PATCH /api/events/{{eventId}}/status` | Reportante no puede cambiar el estado | 403 |
| F02 | `POST /api/events/{{eventId}}/actions` | Reportante no puede crear acciones | 403 |
| F03 | `PATCH /api/events/{{eventId}}/actions/2147483647/complete` | Reportante no puede completar acciones | 403 |
| F04 | `POST /api/events/{{eventId}}/actions` | No se crean acciones en reportado | 409 |
| F05 | `PATCH /api/events/{{eventId}}/status` | No se puede saltar de reportado a cerrado | 409 |
| F06 | `PATCH /api/events/{{eventId}}/status` | Estado desconocido | 400 |
| F07 | `PATCH /api/events/{{eventId}}/status` | Estado ausente | 400 |
| F08 | `PATCH /api/events/{{eventId}}/status` | Estado null | 400 |
| F09 | `GET /api/events/{{eventId}}` | Los rechazos conservan el historial inicial | 200 |
| F10 | `PATCH /api/events/{{eventId}}/status` | Avanzar a en revisión | 204 |
| F11 | `PATCH /api/events/{{eventId}}/status` | Repetir el estado actual genera conflicto | 409 |
| F12 | `PATCH /api/events/{{eventId}}/status` | No se permite retroceder a reportado | 409 |
| F13 | `GET /api/events/{{eventId}}` | Solo el avance exitoso agrega historial | 200 |
| F14 | `PATCH /api/events/{{eventId}}/status` | Cambiar estado sin token | 401 |
| F15 | `PATCH /api/events/2147483647/status` | Cambiar estado de evento inexistente | 404 |
| F16 | `PATCH /api/events/0/status` | Cambiar estado con ID cero | 400 |

### 06 - Validación de acciones

Ejecutar con el evento en revisión. La fecha objetivo admite el mismo día del evento, sin zona horaria.

| Caso | Método y endpoint | Comportamiento | HTTP |
| --- | --- | --- | --- |
| C01 | `POST /api/events/{{eventId}}/actions` | Descripción ausente | 400 |
| C02 | `POST /api/events/{{eventId}}/actions` | Descripción de 4 caracteres | 400 |
| C03 | `POST /api/events/{{eventId}}/actions` | Descripción de 501 caracteres | 400 |
| C04 | `POST /api/events/{{eventId}}/actions` | Descripción en blanco | 400 |
| C05 | `POST /api/events/{{eventId}}/actions` | Responsable ausente | 400 |
| C06 | `POST /api/events/{{eventId}}/actions` | Responsable en blanco | 400 |
| C07 | `POST /api/events/{{eventId}}/actions` | Responsable de 31 caracteres | 400 |
| C08 | `POST /api/events/{{eventId}}/actions` | Fecha objetivo ausente | 400 |
| C09 | `POST /api/events/{{eventId}}/actions` | Fecha objetivo anterior al evento | 400 |
| C10 | `POST /api/events/{{eventId}}/actions` | Formato de fecha objetivo inválido | 400 |
| C11 | `POST /api/events/{{eventId}}/actions` | Crear acción sin token | 401 |
| C12 | `GET /api/events/{{eventId}}` | Validaciones no agregan acciones ni historial | 200 |

### 07 - Acciones y cierre

Acción pendiente, bloqueo del cierre, pertenencia al evento, finalización y cierre con auditoría.

| Caso | Método y endpoint | Comportamiento | HTTP |
| --- | --- | --- | --- |
| W01 | `POST /api/events/{{eventId}}/actions` | Crear acción con máximos válidos y fecha igual al evento | 201 |
| W02 | `GET /api/events/{{eventId}}` | Creación de acción registrada | 200 |
| W03 | `PATCH /api/events/{{eventId}}/status` | Cierre con acción pendiente | 409 |
| W04 | `GET /api/events/{{eventId}}` | Cierre rechazado conserva estado e historial | 200 |
| W05 | `PATCH /api/events/{{otherEventId}}/status` | Poner el segundo evento en revisión | 204 |
| W06 | `PATCH /api/events/{{otherEventId}}/actions/{{actionId}}/complete` | Acción no pertenece al evento de la URL | 404 |
| W07 | `PATCH /api/events/{{eventId}}/actions/2147483647/complete` | Acción inexistente | 404 |
| W08 | `PATCH /api/events/{{eventId}}/actions/0/complete` | ID de acción cero | 400 |
| W09 | `PATCH /api/events/{{eventId}}/actions/{{actionId}}/complete` | Completar sin token | 401 |
| W10 | `GET /api/events/{{otherEventId}}` | Acción ajena rechazada sin historial en el otro evento | 200 |
| W11 | `PATCH /api/events/{{eventId}}/actions/{{actionId}}/complete` | Completar acción pendiente | 204 |
| W12 | `PATCH /api/events/{{eventId}}/actions/{{actionId}}/complete` | Completar la misma acción otra vez | 409 |
| W13 | `GET /api/events/{{eventId}}` | Acción completada y un único cambio de historial | 200 |
| W14 | `PATCH /api/events/{{eventId}}/status` | Cerrar con todas las acciones completadas | 204 |
| W15 | `PATCH /api/events/{{eventId}}/status` | No se puede reabrir un evento cerrado | 409 |
| W16 | `POST /api/events/{{eventId}}/actions` | No se pueden crear acciones en cerrado | 409 |
| W17 | `PATCH /api/events/{{eventId}}/actions/{{actionId}}/complete` | No se pueden completar acciones en cerrado | 409 |
| W18 | `GET /api/events/{{eventId}}` | Auditoría final con cinco operaciones exitosas | 200 |
| W19 | `PATCH /api/events/{{otherEventId}}/status` | Se puede cerrar un evento sin acciones | 204 |
| W20 | `GET /api/events/{{otherEventId}}` | Segundo evento cerrado sin acciones | 200 |

### 08 - Límites mínimos válidos

Casos positivos adicionales. Crean datos nuevos y verifican los límites inferiores de longitud.

| Caso | Método y endpoint | Comportamiento | HTTP |
| --- | --- | --- | --- |
| B01 | `POST /api/create-event` | Evento con título de 5, descripción de 1 y fecha de hoy UTC | 201 |
| B02 | `POST /api/create-event` | Crear otro evento para límites de acción | 201 |
| B03 | `GET /api/events` | Recuperar ID del evento de límites | 200 |
| B04 | `PATCH /api/events/{{minActionEventId}}/status` | Poner el evento de límites en revisión | 204 |
| B05 | `POST /api/events/{{minActionEventId}}/actions` | Acción con descripción de 5 y responsable de 1 | 201 |
