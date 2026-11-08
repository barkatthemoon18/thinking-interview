Thinking SpA Interview

<div style="padding: 15px; border: 1px solid #e0b4b4; background-color: #fdf2f2; border-left: 5px solid #f87171; border-radius: 4px;">
    <strong>Aviso:</strong> El presente proyecto NO es apto para producción.
</div>

Aplicación local del caso práctico. Requiere Java 17 o superior.

```powershell
.\mvnw.cmd spring-boot:run
```

La base H2 está en memoria y se carga con datos ficticios al iniciar.

## Login simulado

La identidad es un mock para esta prueba local: se selecciona un usuario sin
contraseña. No es autenticación apta para producción.

| Usuario | Rol en BD |
| --- | --- |
| reportante1 | reportante |
| reportante2 | reportante |
| revisor1 | revisor |

Enviar `POST /api/login` con `Content-Type: application/json`:

```json
{"username":"reportante1"}
```

La respuesta tiene la forma `{"token":"..."}`. El token es este JSON en UTF-8
codificado en Base64 estándar, sin firma:

```json
{"scheme":"login","username":"reportante1","role":"reportante"}
```

Enviar el valor de `token` en `Authorization` para las operaciones de eventos.
Se acepta tanto `Authorization: <token>` como `Authorization: Bearer <token>`.
No es un JWT: es el formato de identidad simulada solicitado para el caso.

En cada petición el servicio vuelve a consultar el usuario y su rol en la BD.
Un token inválido o cuyo rol ya no coincide devuelve `401`; una operación que
el rol no permite devuelve `403`. El mock supone un único rol por usuario.

`GET /api/events` usa el usuario del header: los reportantes ven sus eventos y
el revisor ve todos. El parámetro `username` ya no selecciona la identidad.
`POST /api/create-event` permite solo reportantes y asigna el reportante desde
el header. `EventDto` no contiene `reporter`; enviar un campo extra con ese
nombre no cambia el propietario. `UserDto` solo recibe el nombre para el login.

## Eventos y acciones

Todas las operaciones consultan el usuario y su rol actual en la BD, sin
conservar permisos de una petición anterior. Las comprobaciones están en los
servicios, también cuando se invocan directamente.

| Operación | Endpoint | Resultado |
| --- | --- | --- |
| Crear evento | `POST /api/create-event` | `201`, solo reportante |
| Listar | `GET /api/events` | `200`, propios para reportantes y todos para revisor |
| Consultar detalle | `GET /api/events/{id}` | `200`, con `actions` e `history` |
| Cambiar estado | `PATCH /api/events/{id}/status` | `204`, solo revisor |
| Crear acción | `POST /api/events/{id}/actions` | `201`, devuelve la acción y su identificador |
| Completar acción | `PATCH /api/events/{id}/actions/{actionId}/complete` | `204`, solo revisor |

Los reportantes reciben `404` al consultar un identificador ajeno, igual que
si no existiera. La consulta a BD filtra por identificador y propietario antes
de cargar acciones o historial. Una acción debe pertenecer al evento de la URL.
Si cambia el propietario en BD, el acceso se recalcula en la siguiente petición.

Ejemplo de creación (la fecha es obligatoria):

```json
{"title":"Evento ficticio","description":"Descripcion del evento","date":"2026-11-01","area":"Bodega"}
```

El cambio de estado recibe `{"status":"en revisión"}` o `{"status":"cerrado"}`.
Solo se permite `reportado -> en revisión -> cerrado`, sin saltos ni retrocesos.
El cierre devuelve `409` si quedan acciones pendientes.

Las acciones se crean y completan únicamente durante revisión. Su creación
recibe descripción de 5 a 500 caracteres, responsable y fecha objetivo:

```json
{"description":"Accion correctiva ficticia","responsible":"Nombre Ficticio","targetDate":"2026-11-15T00:00:00"}
```

Cada operación exitosa registra actor, fecha/hora y cambio en la misma
transacción. Los cambios rechazados no agregan historial. Las escrituras
bloquean el evento para coordinar acciones y cierre concurrentes.

## Supuestos y ambigüedades

Se adoptaron los siguientes supuestos de implementación y decisiones para
aspectos que el enunciado no detalla. Incluyen las ambigüedades declaradas en
[USO_IA.md](USO_IA.md).

- **Un único rol por usuario:** se asume que cada usuario tiene exactamente
  un rol válido, `reportante` o `revisor`. El servidor consulta el rol actual
  en la BD en cada operación; un usuario sin un único rol válido recibe `403`.
- **Reportante enviado en el JSON:** el enunciado asigna la propiedad al
  usuario que creó el evento, pero no especifica si rechazar un campo
  `reporter` agregado manualmente. Se decidió ignorarlo y aceptar la creación
  usando el usuario identificado por el token y validado contra la BD,
  aunque el JSON indique otro reportante.
- **Historial inicial del evento:** no se exige expresamente una entrada
  al crear un evento. Se decidió registrar también su creación en estado
  `reportado`, con actor, fecha y hora, dentro de la misma transacción.
- **Códigos HTTP ante rechazos:** el enunciado exige impedir el acceso ajeno
  sin fijar un código concreto. Se usa `404` al consultar un evento ajeno o
  inexistente y `403` cuando el rol no permite una operación. Los conflictos
  de estado usan `409`, por ejemplo una transición inválida o un cierre con
  acciones pendientes.
- **Longitud del responsable:** se mantuvo el máximo de 30 caracteres del
  esquema existente. El responsable es texto libre obligatorio y debe tener
  entre 1 y 30 caracteres; este límite corresponde al modelo de datos usado.
- **Formato y comparación de la fecha objetivo:** `targetDate` conserva el
  formato con fecha y hora, sin zona horaria. Para validar que no sea anterior
  al evento se compara únicamente el día; se acepta el mismo día del evento.
- **Reloj y zona horaria:** se utiliza UTC para determinar si la fecha del
  evento es futura y para registrar la fecha y hora del historial. El reloj
  es inyectable para que las pruebas puedan fijarlo.
- **Finalización repetida de una acción:** el enunciado no especifica la
  respuesta al repetir la operación. Se decidió devolver `409` si la acción
  ya está completada, sin modificarla ni agregar otra entrada al historial.
- **Cierre de eventos sin acciones:** se interpreta que un evento en revisión
  puede cerrarse aunque no tenga acciones, ya que no existen acciones
  pendientes que impidan el cierre.

## Pruebas

### Pruebas Java

```powershell
.\mvnw.cmd test
```

Hay pruebas unitarias con repositorios que cambian sus respuestas entre
llamadas y pruebas de integración con H2 que modifican roles y propietarios
entre peticiones. Se comprueban asignación desde el token, IDOR entre ambos
reportantes, revocación de permisos, flujo completo, cierre con pendientes,
pertenencia de acciones y reversión si falla el historial.

### Postman

La colección contiene **110 peticiones**, con sus JSON de entrada y aserciones
automáticas. El [plan de pruebas](docs/PLAN_PRUEBAS_API.md) detalla los casos,
los endpoints y los resultados esperados.

#### Ejecutar desde Postman

1. Iniciar la API desde la raíz del proyecto con Java 17 o superior:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

2. Importar la colección y, opcionalmente, su environment local en Postman:
   - [Interview.postman_collection.json](docs/postman/Interview.postman_collection.json).
   - [Interview.local.postman_environment.json](docs/postman/Interview.local.postman_environment.json).
3. Seleccionar **Interview - Local** si se importó, o ejecutar sin environment.
   La colección incluye `baseUrl=http://localhost:8080` y los tres usuarios de
   la semilla como valores predeterminados. Un environment puede sobrescribir
   esos cuatro valores; la dirección no debe incluir `/api` ni barra final.
4. Abrir **Run collection**, seleccionar todas las carpetas, mantener el orden,
   usar una iteración y ejecutar. No requiere archivo de datos.
5. Revisar las aserciones en los resultados de la ejecución. Un fallo indica
   el ID del caso, el estado esperado o el comportamiento que no se cumplió.

La primera petición, **L01**, genera títulos únicos y fechas en UTC, limpia
los identificadores anteriores y comienza a guardar los tokens. Las fechas
se calculan automáticamente para evitar que los ejemplos caduquen. No crear
variables de environment con los mismos nombres que las variables internas
de la colección: Postman les daría prioridad.

E06 consulta con el token de `reportante1` y envía `username=revisor1` y
`role=revisor` en el query. Debe recibir únicamente eventos de `reportante1`.
La aserción compara cada `reporter` con `reporter1Username`; un fallo informa
el ID del evento, su propietario recibido y el esperado. Si esa variable está
vacía, informa que falta configurarla. En colecciones importadas antes de
añadir los valores predeterminados, seleccionar **Interview - Local** o
agregar `reporter1Username=reportante1` en las variables de la colección evita
comparar contra una variable ausente. Al reimportar, comprobar que esos
valores queden disponibles y volver a ejecutar desde L01.

#### Ejecución desde terminal con Newman

Con la API iniciada y Node.js/npm disponibles, ejecutar desde la raíz:

```powershell
npx.cmd --yes --package=newman@6.2.1 newman run docs/postman/Interview.postman_collection.json -e docs/postman/Interview.local.postman_environment.json
```

En Linux/macOS se usa `npx` en lugar de `npx.cmd`. Newman devuelve un código
distinto de cero cuando fallan las aserciones. La primera ejecución puede
descargar Newman en la caché de npm.

Para otra dirección, agregar al comando:

```text
--env-var baseUrl=http://localhost:8081
```

Para guardar un reporte JSON, agregar:

```text
--reporters cli,json --reporter-json-export target/postman-newman-report.json
```

#### Datos y orden de ejecución

| Variable | Uso |
| --- | --- |
| `baseUrl` | Dirección de la API; configurable en el environment. |
| `reporter1Username` | `reportante1`, usuario semilla. |
| `reporter2Username` | `reportante2`, usuario semilla. |
| `reviewerUsername` | `revisor1`, usuario semilla. |
| `reporter1Token`, `reporter2Token`, `reviewerToken` | Se guardan al ejecutar L01–L03. |
| `eventId`, `otherEventId` | Se recuperan del listado por el título único de esta ejecución. |
| `actionId` | Se guarda al crear la acción en W01. |
| `eventDate`, `targetDate` | Ayer UTC y ese mismo día a las 00:00:00. |
| `today`, `futureDate`, `beforeEventDate` | Fechas para límites y casos negativos. |
| `minActionEventId` | Evento adicional para los límites mínimos de las acciones. |

Cada ejecución crea **cuatro eventos y dos acciones**. Los dos eventos del
flujo principal terminan cerrados; los otros prueban límites de campos.
No hay endpoint de eliminación. Se puede repetir la colección completa sin
reiniciar: los eventos del flujo se identifican por títulos nuevos y no se
asumen IDs concretos ni cantidades fijas de datos semilla. Reiniciar la API
restablece la base H2 y elimina también los demás cambios locales.

Los usuarios deben conservar los roles de la semilla. Los tokens manipulados
de A04–A08 usan los nombres originales de esa semilla de forma deliberada.
La carpeta de login puede ejecutarse sola; las demás carpetas requieren
los pasos anteriores. Para repetir el flujo de estados, empezar otra vez en
L01: no se puede volver a poner en revisión un evento ya cerrado.

El login de este proyecto es simulado: recibe solo `username` y entrega un
JSON codificado en Base64, sin firma. Las peticiones usan el header
`Authorization`; no configurar Bearer Auth adicional en Postman. La colección
incluye expresamente una petición con el token sin el prefijo `Bearer`.

#### Qué se valida

- Login de los tres usuarios y contenido decodificado del token.
- Errores `400`, `401`, `403`, `404`, `405`, `409` y `415`, con cuerpo
  `application/problem+json` y los campos de `ProblemDetail`.
- Visibilidad por propietario, permisos por rol e intentos de suplantación.
- Límites mínimos y máximos de campos, fechas y catálogo de áreas.
- Transiciones `reportado → en revisión → cerrado` y bloqueo por pendientes.
- Pertenencia de acciones al evento, finalización y rechazo de duplicados.
- Historial, actor y orden temporal; los intentos rechazados no agregan cambios.

Las pruebas que cambian directamente roles o propietarios en la base,
provocan un fallo transaccional o necesitan concurrencia se describen por
separado en el plan.

## Uso de IA

La declaración de uso de IA está en [USO_IA.md](USO_IA.md).

## Preguntas y Supuestos

- ¿Como reemplazaría la identidad simulada por una autenticación real y qué validaría con el cliente antes de producción?
  - Básicamente, reemplazar el Bearer Token simulado por un Bearer Token de JWT firmado criptográficamente siguiendo las normas de FIPS y las recomendaciones de NIST. Del mismo modo, que este token esté validado también bajo un estricto control de roles para evitar que sea manipulado o reutilizado (debe incluir expiración). Esto último suponiendo que fuera un escenario clásico. De lo contrario, implementar SSO con MFA. Token de autorización con revocaciones en corto tiempo, entre otros mecanismos de seguridad preventiva.
  - Por otro lado, agregar controles de seguridad como revisión de secretos en código fuente/repositorios, SAST, SCA (para dependencias como Spring Boot o SLFJ4). Por otro lado, también incluir validaciones contra XSS (en el caso del Frontend), SQLi y vulnerabilidades con crecimiento CSRF y SSRF (sobre todo esta última). Idealmente, utilizar un equipo de Pentesting para evaluar dinámicamente los comportamientos.
  - Cifrado de BD.
- Tratamiento de Datos: Con base en la nueva ley de protección de datos, habría que clasificar aquellos que pudieran ser expuestos sin generar riesgos, y clasificar aquellos que tienen cierto riesgo para procesarlos según corresponda:
  - Tokenización
  - Enmascaramiento
  - Cifrado en tránsito y reposo (esto también incluiría cifrado de payload si lo transportado es sumamente sensible).
- Manejo de secretos: En bóvedas de seguridad (CyberArk por ejemplo, o las bóvedas de nubes como Azure KeyVault).
- Observabilidad: Implementar mecanismos de registros de traza y observabilidad posterior al despliegue a través de herramientas como Dynatrace o ELK (ElasticSearch, Kibana).
- Respuesta de Incidentes: Tener un sistema de ciberseguridad que monitoree los endpoints constantemente (EDR/XDR) en búsqueda de anomalías para limitar el impacto ante ataques dirigidos.
- Implementar un proceso seguro de integración y despliegue (CI/CD).
- Tener lineamientos y directrices de seguridad básicos para el desarrollo (Desarollo Seguro + DevSecOps).
- Infraestructura basada en el principio Zero Trust. 