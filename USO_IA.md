# USO_IA.md

Máximo dos páginas en total (la bitácora ocupa como máximo una). Sé concreto: nombres de archivos, pruebas y comandos valen más que adjetivos. No incluyas información confidencial, datos personales reales ni credenciales. No se piden conversaciones completas ni acceso a tus cuentas.

## 1. Herramientas y modelos

Ej.: ChatGPT (GPT-x), Claude, Copilot, Cursor. Si no conoces el modelo, indica solo la herramienta.

- Se utilizó [start.spring.io](https://start.spring.io) para generar el boilerplate del proyecto llamado "interview" en Java 17 (JAR-generated).
- Se utilizó ChatGPT 6 en razonamiento Alto para formalizar requerimientos a partir de lo descrito en el PDF (generar tabla de requerimientos).
- Se utilizó Codex con GPT-6.1 Sol para apoyar en el desarrollo y simplificar la implementación de servicios. 

## 2. Etapas en las que usé IA

- [X] Aclarar requisitos
- [ ] Diseño (modelo, reglas, estructura)
- [X] Generar código
- [X] Pruebas
- [ ] Depuración
- [X] Documentación

## 3. Interacciones representativas (3 a 5)

| # | Objetivo | Prompt o extracto relevante | Resultado | Qué hice con él |
| --- | --- | --- | --- | --- |
| 1 | Ordenar requerimientos | Puedes analizar los requerimientos de este caso práctico? (PDF adjunto) | Generó un plan arquitectónico con la estructura general de lo que se debía hacer. Mencionó los endpoint principales y directrices generales | Abstraer el análisis y comenzar a diseñar el modelo de Base de Datos |
| 2 | Generar código y pruebas (TDD) | Extracto relevante 1: Generar los endpoint en InterviewController y el GlobalExceptionHandler para distintas excepciones<br/><br/> Extracto Relevante 2: A partir de lo generado por mí en el EventServiceImpl, apoyar con la generación de otros servicios como CorrectiveActionImpl, ChangeHistoryImpl, etc.<br/><br/>Extracto Relevante 3: Diseñar las pruebas unitarias para validación y generar un plan de pruebas en Postman para corroborar comportamiento.| Completitud de la API REST, con validadores implementados para evitar vulnerabilidades comunes como IDOR/BOLA. Se generó en la documentación el plan de pruebas | Revisar el código generado para ver si lo que había generado tenía sentido con lo requerido y posteriormente se realizaron pruebas manuales (con Angular 22 + API Local en H2) y a través de Postman para validar cumplimiento en ámbito dinámico |
| 3 | Generación básico de Frontend con Angular 22 | Prompt 1: Revisa estos documentos y extrae los endpoint asignados, de modo que se puedan realizar pruebas funcionales. Haz los cambios primero y luego levanto el servidor con DB H2 para validar si todo funciona correctamente<br/><br/>Prompt 2: Puedes agregar una landing page que sea de login (esperando un textbox de username) y se genere ahí la llamada a /api/login para poder procesar el token y posteriormente se dirija a eventos (si es reportante) o al portal de revisiones si es revisor? La idea es que se separen bien los roles | Se generó un sitio en local, el cual tenía su adecuado landing page para login (con los usuarios semilla) que se conectaban a la API y retornaban el token de sesión que identificaba al usuario. Posteriormente se podía interactuar en la plataforma conforme lo esperado. | Validar el resultado generado para el lado Frontend. La página cumplía con lo básico y se podía dar completitud al flujo, evidenciando los guards desde el Frontend para límites máximos y mínimos (respaldados en el Backend), y el flujo esperado también (Reportado -> En Revisión -> Cerrado). |

## 4. Propuestas de IA que corregí, rechacé o tuve que comprobar

- Durante la idea de manejar las sesiones, mi propuesta consistía en una variante de Bearer Token simplificado que con base en el username obtenía el rol que sería validado contra la BD. Pero la IA quería generar un boilerplate con firmas criptográficas, complicando innecesariamente el proceso de login que no era lo relevante aquí.<br/>Tras un nuevo prompt, se abstuvo de ello y simplemente quedó como ejercicio el siguiente modelo: { "schema": "login", "username": "reportante1", "role": "reporter" } validando contra la BD en el caso que el atributo role fuera alterado (como sucede con los JWT).

## 5. Qué verifiqué personalmente y cómo

- Tras la generación de los endpoint para el InterviewController, verifiqué que estos cumplieran con lo que inicialmente ChatGPT había propuesto, así como también velar que cumplieran con los flujos. Los procesos estaban bien implementados.
- Con respecto al manejo de la sesión, analicé cómo se realizaba la validación de los roles, porque una cosa era generar el atributo y otra que efectivamente recurriera a la BD y contrastara el atributo del Bearer Token contra lo que obtenía en la BD a partir del username. Se corroboró todo finalmente con Postman.
- Realicé las pruebas documentadas de Postman por parte de Codex, analizando los resultados y los tests results de los scripts para corroborar que todo cumpliera. En sus casos de pruebas incluían las restricciones y potenciales modificaciones que cumplieron su cometido, siendo rechazadas o manteniendo sin alterar el flujo esperado.
- Por último con el Frontend, realicé pruebas dinámicas, validando las ejecuciones de Postman y contrastando con el sitio para verificar que el flujo se mantenía uniforme entre ambos alcances. Por último realicé pruebas de concepto exclusivas en el sitio, donde todo resultó como debía.

| Qué verifiqué | Mediante qué prueba o acción |
| --- | --- |
| Endpoints de Controlador | Análisis de código generado + Pruebas Postman |
| Manejo de Sesiones por JWT simulado | Análisis de código fuente sobre el proceso de obtención + contraste con BD |
| Pruebas funcionales con Postman (API Requests) | Postman Collections (JSON) |
| Ejecutar el sitio y realizar interacciones sobre el, evaluando límites y flujo de la aplicación | Sitio con Angular 22 + API REST (Spring Boot) con H2 (BD) activa | 

## 6. Limitaciones y problemas abiertos

- No hubo limitaciones. 
- Sí hubo ambigüedad en algunos conceptos:
  - No se especificó si al crear un nuevo evento (diferente de la semilla), este debía incluir el historial con el estado reportado. Como contexto del PDF se consideró que sí y se agregó.
  - Ante un cambio en la solicitud de crear evento (si se agregara manualmente el reportante) como parte del JSON con un token válido, esto podría fallar (por agregar un atributo indebido) o considerar el reportante del token. Ante esto, se consideró que se aceptaba la creación usando el reportante del token (quien inició sesión), independiente del atributo del JSON.
  - Discrepancia sobre los códigos de HTTP que se debían utilizar al impedir el acceso ajeno. Se utilizaron para este fin 404 (not found) y 409 (conflict).
  - Si se repetía la finalización de una acción, se generaba un 409 para representar un conflicto de estado (si ya fue cerrado, sólo fallaba con el código, sin mayor acción).

## 7. Tiempo aproximado dedicado

- Total: _6_ horas (entre diseño, generación de código y pruebas)

## 8. Bitácora de proceso (4 a 6 hitos, máximo una página)

| # | Fecha y hora aprox. | Qué decidí o intenté | Qué cambió | Cómo lo verifiqué | Resultado o problema pendiente |
| --- | --- | --- | --- | --- | --- |
| 1 | 07/11/2026 03:00 | Comenzar el diseño de la BD + Boilerplate Spring Boot | Se agregó lo mínimo para el maquetado del proyecto | Ejecución básica para validar BD y Spring Boot Runtime | Funcionando correctamente |
| 2 | 08/11/2026 05:00 | Realizar prompts a Codex para generación de Controlador y Servicios a partir de la base mía | Se añadió toda la lógica de servicios pendientes y endpoints en el controlador y manejo de sesión simulada | Realicé un análisis de código estático general sobre lo implementado por Codex | API funcional con la BD activa y datos semilla presentes en él. Sin mayor validación de cumplimiento |
| 3 | 08/11/2026 15:00 | Generar con Codex el plan de pruebas de Postman | Se generó documentación + JSON de prueba para Postman | Usando Postman Collections realicé la validación de cada prueba (API Request) generada por Codex para validar comportamientos de API REST | Todas las restricciones y flujos cumplían lo esperado, sin embargo se dejó evidencia de las ambigüedades en algunos casos |
| 4 | 08/11/2026 17:00 | Generar boilerplate en Angular 22 + Login simulado para pruebas dinámicas | Se generó el Frontend conectado a la API REST | Realicé pruebas manuales desde el sitio [Localhost](http://localhost:4200) hacia la API REST en [Localhost:8080](http://localhost:8080/api/*)  | Tanto pruebas en Postman Collection como en Sitio Web con Angular 22 fueron exitosas |
| 5 | 08/11/2026 18:00 | Documentación final | Se actualizaron documentos README.md y USO_IA.md | Realizado por mí | Ningún problema |