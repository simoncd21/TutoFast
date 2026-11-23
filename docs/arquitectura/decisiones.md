# Registro de decisiones de arquitectura (ADR) — TutoFast

Formato: Contexto · Decisión · Consecuencias. Estado de todas: **Aceptada** (2026-11-23). Prevalece `CLAUDE.md`, luego los diagramas de `docs/diagramas/` y por último los requisitos de `docs/requisitos/`.

---

## ADR-001 · Nombre del producto: TutoFast
**Contexto.** Los requisitos, las HU y los 5 diagramas usan el nombre anterior "UniDrive Tutorías" (`unidrive-backend.jar`, base de datos `unidrive`).
**Decisión.** El producto se llama **TutoFast** en todo el código y la interfaz: paquete `com.tutofast`, artefacto `tutofast-backend.jar`, base de datos `tutofast`, servicio de salud `tutofast-backend` y mensaje "Registro exitoso en TutoFast". Los PNG originales de los diagramas y las HU en `docs/requisitos/` se conservan como fuente histórica sin modificar; la versión limpia está en `docs/requisitos/requisitos-consolidados.md`.
**Consecuencias.** Antes de cada merge a `main` se verifica que `grep -ri unidrive backend frontend` no devuelva resultados.

## ADR-002 · Persistencia en MongoDB Atlas + GridFS (en lugar de PostgreSQL)
**Contexto.** R16 y HU-11 proponían PostgreSQL "pendiente de confirmación". La vista de despliegue, que prevalece, define un clúster de MongoDB Atlas con GridFS. HU-11 exige integridad transaccional en operaciones críticas.
**Decisión.** Se usa MongoDB Atlas (base de datos `tutofast`) con Spring Data MongoDB. Las composiciones pequeñas se embeben y las entidades de alto volumen o con escrituras concurrentes se referencian (ver `diseno-base-datos.md`). Los archivos (PDF, PNG, JPG, DOCX ≤ 10 MB) se guardan en GridFS, bucket `archivos`, dentro del mismo clúster; no se usan Cloudinary ni S3.
- Las operaciones críticas (confirmar, finalizar o liquidar, radicar y resolver reportes) usan `@Transactional` con `MongoTransactionManager`. Atlas, incluido M0, es replica set y admite transacciones multi-documento.
- **Ajuste aprobado:** el `MongoTransactionManager` se registra solo cuando `tutofast.mongo.transacciones=true` (variable `MONGO_TRANSACCIONES`, activa en Render/Atlas). En otro caso se registra un gestor no-op, para que `@Transactional` no falle con un Mongo local standalone. Las pruebas unitarias (JUnit + Mockito) no levantan el contexto de Spring.
- **Ajuste aprobado:** las subidas a GridFS nunca ocurren dentro de un método `@Transactional`. Primero se sube el archivo y luego se ejecuta la transacción; si esta falla, se compensa eliminando lo subido. Los archivos huérfanos (`referenciaId` nulo durante más de 24 h) se limpian con una tarea diaria.
- La integridad referencial (por ejemplo, que la asignatura exista y esté activa) se valida en los servicios.

**Consecuencias.** No hay *joins* ni claves foráneas: los servicios validan las referencias y algunos campos se desnormalizan para las búsquedas de R07. El nivel gratuito M0 (512 MB) basta para la demo académica.

## ADR-003 · Frontend en HTML5 + CSS3 + JavaScript ES6 puro
**Contexto.** Los requisitos mencionan "aplicación móvil y web". `CLAUDE.md` y la vista de despliegue (artefacto "HTML5 · CSS · JavaScript" en Render Static Site) definen un frontend sin frameworks.
**Decisión.** Se construyen 9 páginas HTML estáticas (`index`, `acceso`, `solicitudes`, `negociacion`, `chat`, `calificaciones`, `reportes`, `admin`, `perfil`) con módulos ES6 (`type="module"`), `fetch` y CSS con variables nativas. No se usan React, TypeScript, Tailwind ni ningún *bundler*. El diseño es responsive (*mobile-first*), lo que cubre el uso desde el móvil sin una app nativa. Hay tema claro/oscuro con paleta de morado pastel y azul suave, persistido en `localStorage`.
**Consecuencias.** Se despliega en Render Static Site sin comando de build. La URL del backend se configura en `frontend/js/config.js`. Hay que evitar XSS manualmente (`textContent` / `escaparHTML`).

## ADR-004 · Autenticación JWT liviana
**Contexto.** Se necesitan tres roles (ESTUDIANTE, TUTOR, ADMINISTRADOR), una misma cuenta con varios roles (nota del DFD) y una solución simple, sin OAuth.
**Decisión.** Las contraseñas se guardan con `BCryptPasswordEncoder` (`spring-security-crypto`, sin el *starter* completo de Spring Security). Se emiten tokens JWT HS256 con `jjwt`, firmados con `JWT_SECRET` y válidos 8 h, que se envían en `Authorization: Bearer`. `FiltroAutenticacionJwt` valida el token y el estado de la cuenta e inyecta `UsuarioAutenticado`. `@RolesPermitidos` + `InterceptorRoles` validan los roles, y los servicios validan la propiedad de cada recurso.
**Consecuencias.** No hay sesión en el servidor (escala sin estado en Render). La revocación inmediata se resuelve consultando `estadoCuenta` en el filtro. El token vive en `localStorage`, lo que exige cuidar el XSS. El administrador inicial se crea por seed desde variables de entorno.

## ADR-005 · Simulador de pasarela de pagos
**Contexto.** El DFD y el diagrama de componentes muestran una pasarela externa (retención, liberación, reembolso). Es un proyecto académico, sin un comercio real, y no se deben almacenar datos de tarjeta.
**Decisión.** El puerto `IPasarelaPago` (`retener`, `liberar`, `reembolsar`) lo implementa `AdaptadorPasarela`, un **simulador interno** del backend (artefacto "Simulador de pasarela" de la vista de despliegue). Genera referencias `SIM-<uuid>`, aprueba por defecto y rechaza con el token de prueba `tok_rechazar`. El frontend solo envía un token simulado; nunca un número de tarjeta.
**Consecuencias.** La liquidación de R12 (comisión y neto) se calcula en `ServicioPagos`, sin depender de la pasarela. Cambiar a una pasarela real solo requiere un nuevo adaptador.

## ADR-006 · Notificaciones guardadas en MongoDB
**Contexto.** El DFD muestra "Notificaciones y correo" como sistema externo. `CLAUDE.md` prohíbe enviar correos reales (SMTP, Resend).
**Decisión.** `INotificador.enviar()` lo implementa `AdaptadorNotificaciones`, que inserta documentos en la colección `notificaciones`. La web los consulta: la campana del encabezado pide el conteo cada 30 s y la lista completa está en el perfil. Los recordatorios los genera la tarea programada `ServicioNotificaciones.recordar()`.
**Consecuencias.** No hace falta configurar ni pagar un proveedor de correo. Las notificaciones se borran solas a los 180 días (índice TTL).

## ADR-007 · Chat con API REST y polling
**Contexto.** R11 y HU-07 piden mensajería contextual inmutable con adjuntos. `CLAUDE.md` excluye WebSockets.
**Decisión.** Se usan `GET /api/solicitudes/{id}/mensajes?tutorId=&desde=` cada 4 s (pausado cuando la pestaña está oculta, con *backoff* a 15 s ante errores) y `POST` para enviar. Los hilos se identifican por `(solicitudId, tutorId)` para aislar a los tutores que compiten por la misma solicitud. No existen endpoints para editar o borrar mensajes.
**Consecuencias.** La latencia es de hasta unos 4 s, aceptable para la coordinación de tutorías. El índice `(solicitudId, tutorId, marcaTemporal)` mantiene barata cada consulta.

## ADR-008 · Contenedor Docker Java 21 para Render
**Contexto.** Render Web Service no ofrece un *runtime* nativo de Java; sí admite Docker. El servicio debe escuchar en el `PORT` que Render inyecta.
**Decisión.** `backend/Dockerfile` *multi-stage*: etapa de build `maven:3.9-eclipse-temurin-21` (`./mvnw -B -DskipTests package`) y etapa de ejecución `eclipse-temurin:21-jre-alpine` con usuario sin privilegios, que copia `tutofast-backend.jar` y ejecuta `java -XX:MaxRAMPercentage=75 -jar app.jar`. `application.properties` usa `server.port=${PORT:8080}`. El *health check* de Render apunta a `/api/salud`.
**Consecuencias.** Las compilaciones son reproducibles. En el plan gratuito el servicio se duerme tras unos 15 min de inactividad y la primera petición tarda unos 30–60 s (aceptable para la demo; se documenta en la guía de infraestructura).

## ADR-009 · Arquitectura en capas con puertos y adaptadores
**Contexto.** R15/HU-11 exigen tres capas desacopladas. La vista de desarrollo define 11 servicios, 3 puertos y 3 adaptadores.
**Decisión.** Paquetes `controlador → servicio → repositorio/modelo`, con `integracion/puertos` y `integracion/adaptadores` para los sistemas externos. Los controladores no tienen lógica de negocio. Las dependencias entre servicios siguen las flechas «use» de la vista de desarrollo, sin ciclos.
**Consecuencias.** Se puede probar cada servicio con Mockito simulando los repositorios y los puertos.

## ADR-010 · Documentación de la API solo en Markdown
**Decisión.** `docs/arquitectura/diseno-api.md` es la única referencia de la API; no se agregan Swagger, springdoc ni OpenAPI.
**Consecuencias.** Cada módulo actualiza ese documento en el mismo PR que sus endpoints.

## ADR-011 · Modelado de estados de R10
**Contexto.** R10 enumera 9 estados en una sola lista; el diagrama de clases separa `SolicitudTutoria.estado` y `ServicioTutoria.estado`.
**Decisión.** Se usan dos enums, `EstadoSolicitud` (PUBLICADA, PROPUESTAS_RECIBIDAS, NEGOCIACION_EN_CURSO, TUTOR_SELECCIONADO, FORMALIZADA, CANCELADA) y `EstadoServicio` (CONFIRMADO, EN_DESARROLLO, FINALIZADO, CANCELADO, EN_REVISION). La interfaz los combina para mostrar los 9 nombres de R10. "En apelación pendiente" (HU-10) se modela en `EstadoSancion.EN_APELACION`, y los descargos son `Apelacion.descargo`.

## ADR-012 · Prioridad de verificación para correos `.edu.co`
**Contexto.** El supuesto de HU-01 dice que un correo institucional `.edu.co` da prioridad en la revisión sin bloquear los correos personales.
**Decisión.** La prioridad se calcula al consultar la cola (`GET /api/admin/soportes`): `ALTA` si el correo termina en `.edu.co` y `NORMAL` en otro caso. La cola se ordena por prioridad y luego por fecha de envío, y `admin.html` muestra la insignia "Correo institucional · Prioridad alta".
**Consecuencias.** No requiere migraciones; si cambia la regla, solo se modifica `ServicioIdentidad`.
