# Plan de implementación de TutoFast — 10 módulos

> Referencias: [arquitectura](arquitectura/arquitectura.md) · [base de datos](arquitectura/diseno-base-datos.md) · [API](arquitectura/diseno-api.md) · [ADR](arquitectura/decisiones.md) · [requisitos](requisitos/requisitos-consolidados.md).

## Flujo de trabajo por módulo
```bash
git checkout main && git pull
git checkout -b feature/NN-nombre
# implementar → ./mvnw test → prueba manual
git add . && git commit -m "feat(NN): ..."
git push -u origin feature/NN-nombre      # abrir PR hacia main, revisar, merge
```
Definición de terminado (todos los módulos):
- [ ] `./mvnw test` en verde.
- [ ] Endpoints nuevos documentados en `diseno-api.md`.
- [ ] `grep -ri unidrive backend frontend` vacío.
- [ ] Sin lógica de negocio en los controladores.
- [ ] Ninguna subida a GridFS dentro de un método `@Transactional`.
- [ ] Interfaz probada en tema claro y oscuro.

Grafo de dependencias:
```text
01 ─► 02 ─► 03 ─► 04 ─► 05 ─► 06 ─► 07 ─┬─► 08 ─┐
                   └────────►┘           └─► 09 ─┴─► 10
```

---

## Módulo 01 · `feature/01-base-proyecto`
**Depende de:** —
**Backend**
- [ ] `backend/pom.xml` (Spring Boot 3.x, Java 21, `finalName` tutofast-backend; dependencias: web, data-mongodb, validation, spring-security-crypto, jjwt-api/impl/jackson, starter-test).
- [ ] Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper`).
- [ ] `Dockerfile` *multi-stage* (`maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre-alpine`, `${PORT:8080}`) + `.dockerignore`.
- [ ] `application.properties` (MONGODB_URI, JWT_*, FRONTEND_URL, PORT, MONGO_TRANSACCIONES, multipart 10MB/50MB, `auto-index-creation=true`).
- [ ] `TutoFastApplication`; `config/CorsConfig`; `config/GridFsConfig` (GridFsTemplate del bucket `archivos` + `MongoTransactionManager` condicional + `GestorTransaccionesNoOp`).
- [ ] `excepcion/` (ExcepcionNegocio con `codigo`, ExcepcionNoEncontrado, ExcepcionAutorizacion, ManejadorGlobalExcepciones → `ErrorDTO`).
- [ ] `controlador/SaludControlador` → `GET /api/salud` (hace ping a Mongo).

**Frontend**
- [ ] `index.html` (portada), `css/variables.css` (tokens claro/oscuro), `css/base.css`, `css/componentes.css`.
- [ ] `js/config.js`, `js/api.js`, `js/tema.js`, `js/utilidades.js`.

**Raíz:** `.gitignore`, `.env.example`, `README.md`.

**Pruebas / criterios**
- [ ] `./mvnw package` genera `target/tutofast-backend.jar`.
- [ ] `docker build` local funciona.
- [ ] `curl /api/salud` → `{"estado":"UP","baseDatos":"UP"}` con Atlas.
- [ ] Con `MONGO_TRANSACCIONES=false` arranca sin replica set.
- [ ] La portada alterna claro/oscuro y recuerda la preferencia.

## Módulo 02 · `feature/02-modelo-persistencia`
**Depende de:** 01
- [x] `modelo/`: las 22 clases (`@Document` para las raíces; clases simples para los embebidos), `Calificacion` abstracta con `@TypeAlias` en las subclases, enums y `Periodo`.
- [x] Métodos de dominio sin E/S: `SolicitudTutoria.validarIntegridad()`, `Pago.calcularLiquidacion()`, `CalificacionAlTutor.calcularPromedio()`, transiciones de estado (`confirmar()`, `iniciarSesion()`, `finalizar()`…) que lanzan `ExcepcionNegocio` si no son válidas.
- [x] `repositorio/`: 16 interfaces + `RepositorioSolicitudTutoriaPersonalizado(Impl)` (Criteria dinámicos).
- [x] Índices `@Indexed` / `@CompoundIndex`; `config/DatosInicialesSeed` crea los índices parciales (propuesta activa, comisión vigente, mensajes reportados) y el TTL.
- [x] `mapper/` base.

**Pruebas**
- [x] `PagoTest`: 40.000 × 10 % → 4.000 / 36.000; redondeo HALF_UP con 12,5 %.
- [x] `ServicioTutoriaTest`: transiciones válidas e inválidas.
- [x] `CalificacionTest`: promedios.
- [ ] Al arrancar, Atlas muestra las colecciones y los índices.

## Módulo 03 · `feature/03-seguridad-identidad`
**Depende de:** 02
- [x] `seguridad/`: JwtUtil, FiltroAutenticacionJwt, UsuarioAutenticado, RolesPermitidos, InterceptorRoles, SeguridadConfig (BCrypt, registro del filtro, interceptor y *argument resolver*).
- [x] `servicio/ServicioAuditoria` (`registrarAccion`, `consultar`).
- [x] `servicio/ServicioIdentidad` (`registrarUsuario`, `autenticar`, `autorizarDatos`, `activarRol`, perfiles).
- [x] Controladores: AutenticacionControlador, UsuarioControlador (perfil, roles).
- [x] Endpoints: `POST /api/auth/registro`, `POST /api/auth/iniciar-sesion`, `GET /api/auth/yo`, `GET /api/usuarios/yo`, `PUT /api/usuarios/yo/perfil-estudiante`, `PUT /api/usuarios/yo/perfil-tutor`, `POST /api/usuarios/yo/roles`.
- [x] Seed del administrador (ADMIN_CORREO / ADMIN_CLAVE).
- [x] Frontend: `acceso.html` + `js/paginas/acceso.js` (pestañas, casilla Ley 1581), `js/sesion.js`, `js/componentes/encabezado.js` (navegación por rol, selector de rol activo, botón de tema), `perfil.html` base. Además: encabezado compartido en `index.html`, `acceso.html` y `perfil.html`; el registro no pide programa académico hasta que exista el catálogo (módulo 04).

**Pruebas**
- [x] `ServicioIdentidadTest`: registro sin consentimiento → 422; correo duplicado → 409; clave guardada como hash.
- [x] `JwtUtilTest`: firma, expiración, token manipulado.
- [x] `ServicioAuditoriaTest` y `ConsultaAuditoriaTest`: registro, detalle copiado, acción vacía → error, filtros combinables; `ServicioIdentidadTest` verifica la auditoría de registro, consentimiento Ley 1581, sesión, rol y seed (130 pruebas en verde).
- [ ] Manual: ruta protegida sin token → 401; rol incorrecto → 403; cuenta suspendida → 403.
- [ ] En la interfaz aparece "Registro exitoso en TutoFast".

## Módulo 04 · `feature/04-integracion-archivos-notificaciones`
**Depende de:** 03
- [x] `integracion/puertos/`: IPasarelaPago, INotificador, IAlmacenamiento (`guardar`, `recuperar`, `eliminar`), ResultadoTransaccion, ArchivoRecuperado.
- [x] `integracion/adaptadores/`: AdaptadorPasarela (simulador `SIM-uuid`, `tok_rechazar`), AdaptadorNotificaciones (+ DocumentoNotificacion), AdaptadorAlmacenamiento (GridFsTemplate).
- [x] `servicio/ServicioArchivos` (`validarFormato`, `validarTamano`, `guardar`, `recuperar`, `eliminar`, control de acceso por contexto, limpieza diaria de huérfanos).
- [x] `servicio/ServicioNotificaciones` (`notificar`, `consultar`, `marcarLeida`).
- [x] Controladores: ArchivoControlador, NotificacionControlador; soportes en UsuarioControlador.
- [x] Endpoints: `GET /api/archivos/{id}`, `GET /api/notificaciones`, `GET /api/notificaciones/conteo-no-leidas`, `PATCH /api/notificaciones/{id}/leida`, `PATCH /api/notificaciones/leer-todas`, `POST|GET /api/usuarios/yo/soportes`.
- [x] Frontend: campana con polling cada 30 s en el encabezado; lista de notificaciones y subida de soportes en `perfil.html`; descarga autenticada (`descargarArchivo`) en `js/servicios/api.js`; campana en `js/componentes/campana.js`, integrada desde `encabezado.js`.

**Pruebas**
- [x] `ServicioArchivosTest`: `.exe` → 415; 11 MB → 413; PDF, PNG, JPG y DOCX aceptados; firma del contenido; control de acceso; limpieza de huérfanos.
- [x] `AdaptadorPasarelaTest`: aprobado y rechazado.
- [ ] Manual: subir un soporte → aparece en `archivos.files`; nivel → EN_REVISION.
- [x] `ServicioNotificacionesTest`, soportes en `ServicioIdentidadTest` y `ArchivosYNotificacionesControladorTest` (171 pruebas en verde).
- [x] Interfaz verificada en navegador (Edge headless contra una API simulada) en tema claro, oscuro y móvil.
- [ ] Manual contra Atlas: notificación llega a la campana en ≤ 30 s.

**Notas de diseño**
- `ContextoArchivo` (servicio): cada módulo que adjunta archivos debe registrar un bean que diga si referencia un archivo y quién puede descargarlo. Lo usan el control de acceso y la limpieza de huérfanos (24 h de gracia; no borra nada si no hay contextos). Hoy: `ContextoSoportesVerificacion`.
- La subida de soportes no es transaccional: valida y guarda en GridFS, luego guarda el perfil; si eso falla, elimina el archivo. Máximo 5 soportes en revisión por tutor.
- Se valida extensión **y** firma del contenido (un `.exe` renombrado a `.pdf` da 415).

## Módulo 05 · `feature/05-catalogos-comision-admin`
**Depende de:** 03, 04
- [x] `servicio/ServicioCatalogo` (`gestionarCatalogo`, `consultarCatalogo`, `configurarComision`, `consultarComision`; además `cambiarEstadoCatalogo`, `consultarAsignatura`, `consultarPrograma`, `consultarHistorialComision` y `sembrarDatosIniciales`).
- [x] `ServicioIdentidad.verificarTutor` + `consultarColaVerificacion` con **prioridad `.edu.co`**; también `listarUsuarios` y `cambiarEstadoCuenta`; `ServicioAuditoria.consultarPagina` (con nombre del usuario).
- [x] Controladores: CatalogoControlador, ComisionControlador, AdministracionControlador (soportes, usuarios, auditoría).
- [x] Endpoints: `/api/catalogos/**`, `/api/comision/**`, `GET /api/admin/soportes`, `POST /api/admin/usuarios/{u}/soportes/{s}/aprobar|rechazar`, `GET /api/admin/usuarios`, `PATCH /api/admin/usuarios/{id}/estado`, `GET /api/admin/auditoria`.
- [x] Seed (idempotente): universidad, facultad, programa y 4 asignaturas de demo (incluye `IS-204`) + comisión del 10 %.
- [x] Frontend: `admin.html` + `js/paginas/admin.js` (pestañas Catálogos, Comisión, Verificación de tutores con insignia "Correo institucional · Prioridad alta", Usuarios, Auditoría; enlace «Administración» en el encabezado del rol ADMINISTRADOR); selects encadenados universidad → facultad → programa en `acceso.html` y `perfil.html`, y asignaturas dominadas del tutor en `perfil.html` (helper `js/servicios/catalogo.js`).

**Pruebas**
- [x] `ServicioCatalogoTest`: IS-204 duplicado → 409; cambio de comisión cierra la vigente (con simulación del índice parcial único); seed idempotente.
- [x] `ServicioIdentidadTest`: `.edu.co` primero en la cola; aprobar → VERIFICADO; rechazar exige observación; cuentas.
- [x] `AdministracionControladorTest`: catálogo público sin sesión, gestión solo ADMINISTRADOR, validaciones 400/404.
- [ ] Manual: el administrador crea una asignatura y la ve en el registro.

**Notas de diseño**
- El catálogo se consulta sin sesión (`GET /api/catalogos/**`, lo necesita el registro); el filtro JWT solo exime los GET. La gestión exige ADMINISTRADOR.
- Cambio de comisión: primero se cierra la vigente y luego se inserta la nueva (respeta `uk_comision_vigente`); si la inserción falla se reabre la anterior. Repetir el porcentaje vigente → 409.
- Un administrador no puede cambiar el estado de su propia cuenta ni el de otro administrador; suspender o bloquear exige motivo.

## Módulo 06 · `feature/06-solicitudes-busqueda`
**Depende de:** 04, 05
- [x] `servicio/ServicioSolicitudes` (`publicarSolicitud`, `editarSolicitud`, `cancelarSolicitud`, `buscarSolicitudes`, `validarIntegridad` con listas configurables de frases genéricas y palabras de fraude).
- [x] Orden en la publicación: validar → subir a GridFS → persistir (compensar si falla).
- [x] `ServicioIdentidad.buscarTutores` (+ `obtenerPerfilPublicoTutor` y `listarResenas`), con `RepositorioUsuarioPersonalizado.buscarTutores` y `FiltroTutores`. *(entregado junto con el Módulo 07)*
- [x] `SolicitudControlador`. [x] `TutorControlador`.
- [x] Endpoints de solicitudes: `POST/GET /api/solicitudes`, `GET /api/solicitudes/mias`, `GET/PUT /api/solicitudes/{id}`, `POST /api/solicitudes/{id}/cancelar`, `POST /api/solicitudes/{id}/archivos`.
- [x] `GET /api/tutores` (filtros programa, asignatura, modalidad, disponibilidad, calificación mínima, solo verificados, orden), `GET /api/tutores/{id}` (perfil público sin cuenta bancaria, documento ni correo) y `GET /api/usuarios/{id}/resenas` (solo reseñas visibles).
- [x] Frontend: `solicitudes.html` (explorar con filtros, publicar/editar, mis solicitudes con cancelación, detalle con adjuntos) y enlace en `encabezado.js`.
- [x] `tutores.html` + `js/paginas/tutores.js`: búsqueda con filtros y paginación, y perfil público con reseñas en un modal (`tutores.html?id=` lo abre directamente). Sustituye al `perfil.html?id=` previsto; enlace «Tutores» en el encabezado de ESTUDIANTE y ADMINISTRADOR.
- [x] `ContextoSolicitudes`: los adjuntos de una solicitud los ve su dueño, un administrador y, mientras está abierta, los tutores.

**Pruebas**
- [x] `ServicioSolicitudesTest` (31 casos) y `SolicitudControladorTest`: "necesito ayuda con matemáticas" → 422; "resolver examen parcial en vivo" → 422 `FRAUDE_ACADEMICO` + auditoría; duración 90 → 400; cancelar después de FORMALIZADA → 409.
- [x] Búsqueda sin resultados → mensaje vacío en la interfaz.
- [x] `ServicioIdentidadTutoresTest` (filtros y expansión del programa, calificación mínima inválida → 400, perfil público sin datos privados, cuentas suspendidas → 404, reseñas), `TutorControladorTest` (401 sin sesión, parámetros inválidos 400, perfil público sin cuenta bancaria/documento/correo) y `ConsultaTutoresTest` (Criteria sin MongoDB).

## Módulo 07 · `feature/07-propuestas-negociacion`
**Depende de:** 06

El módulo se entrega en dos partes: **(A) propuestas y negociación**, hecha en esta rama, y **(B) confirmación, pago simulado y ciclo del servicio**, pendiente (ver al final).

### Parte A · Propuestas y negociación
- [x] `servicio/ServicioPropuestas`: `enviarPropuesta`, `modificarPropuesta` (también responde a una contraoferta), `retirarPropuesta`, `contraofertar`, `aceptarPropuesta` (selección provisional), `rechazarPropuesta`, `descartarPropuesta` / `descartarPropuestasDeSolicitud` (uso interno, para la confirmación), `listarPropuestas` y `listarMisPropuestas`.
- [x] Controlador: `PropuestaControlador` (sin lógica de negocio).
- [x] Endpoints: `POST/GET /api/solicitudes/{id}/propuestas`, `GET /api/propuestas/mias`, `PUT /api/propuestas/{id}`, `POST /api/propuestas/{id}/retirar|contraofertar|seleccionar|rechazar`.
- [x] Reglas: una propuesta activa por tutor y solicitud (comprobación previa + traducción de `DuplicateKeyException` del índice `uk_propuesta_activa` → 409 `DUPLICADO`); el tutor no puede proponer a su propia solicitud (403); la solicitud debe estar abierta (409); monto > 0 y ≤ 5.000.000 COP; `ACEPTA_PRECIO` copia las condiciones de la solicitud; `CONTRAPROPUESTA_PRECIO` exige un precio distinto; `ALTERNATIVA_CONDICIONES` exige cambiar duración o modalidad; no se selecciona una propuesta con contraoferta pendiente.
- [x] Notificaciones: `NUEVA_PROPUESTA`, `CONTRAOFERTA`, `PROPUESTA_ACEPTADA` (tipo nuevo) y `PROPUESTA_DESCARTADA`; un fallo al notificar no revierte la operación. Auditoría: `PROPUESTA_ENVIADA|MODIFICADA|RETIRADA|CONTRAOFERTADA|ACEPTADA|RECHAZADA|DESCARTADA` (la contraoferta guarda el precio anterior).
- [x] El `ManejadorGlobalExcepciones` responde 400 (en lugar de 500) cuando un parámetro de ruta o de consulta no se puede convertir (por ejemplo, una modalidad inexistente).
- [x] Frontend: `js/componentes/propuestas.js` dentro del detalle de `solicitudes.html` (el estudiante dueño compara las propuestas por precio, contraoferta, selecciona o rechaza; el tutor propone con los 3 tipos, modifica/responde a la contraoferta y retira), pestaña «Mis propuestas» para el TUTOR y apertura directa con `solicitudes.html?id=` (enlace de las notificaciones). Estilos con variables de tema (claro y oscuro) en `css/estilos.css`.

**Pruebas (parte A)**
- [x] `ServicioPropuestasTest` (29 casos): segunda propuesta activa del mismo tutor → 409; propuesta sobre solicitud TUTOR_SELECCIONADO / FORMALIZADA / CANCELADA → 409; el dueño no puede proponer; montos inválidos; contraoferta con auditoría del precio anterior; respuesta del tutor con `PUT`; selección bloqueada con contraoferta pendiente; descarte masivo conservando la elegida; compensación si falla el guardado de la solicitud; listados por rol.
- [x] `PropuestaControladorTest`: flujo completo envío → contraoferta → respuesta → selección, 401/403 por rol y por recurso, 400 de validación, 404 y 409.
- [ ] Manual contra Atlas (con dos navegadores): el tutor propone, el estudiante contraoferta y la notificación llega a la campana en ≤ 30 s; revisar el aspecto en tema claro y oscuro.

### Parte B · Confirmación y ciclo del servicio *(pendiente)*
- [ ] `servicio/ServicioNegociacion` (`confirmarServicio` @Transactional, `iniciarSesion`; usará `ServicioPropuestas.descartarPropuestasDeSolicitud`).
- [ ] `servicio/ServicioPagos` (`retenerFondos`, `calcularLiquidacion`).
- [ ] Controlador: `NegociacionControlador`.
- [ ] Endpoints: `POST /api/solicitudes/{id}/confirmar`, `GET /api/servicios/mios`, `GET /api/servicios/{id}`, `PATCH /api/servicios/{id}/enlace`, `POST /api/servicios/{id}/iniciar`.
- [ ] `ProgramadorTareasConfig`: inicio automático CONFIRMADO → EN_DESARROLLO.
- [ ] Frontend: `negociacion.html` (modal "Autorizar pago simulado y confirmar", ficha del servicio).
- [ ] `ServicioNegociacionTest`: confirmar descarta las demás propuestas y congela `comisionPct`; `tok_rechazar` → 422 sin cambios de estado.

## Módulo 08 · `feature/08-mensajeria-chat`
**Depende de:** 07, 04
- [ ] `servicio/ServicioMensajeria` (`enviarMensaje`, `consultarHistorial(desde)`, `listarHilos`, `reportarMensaje`, `cerrarHilos`).
- [ ] Controlador: MensajeControlador; mensajes reportados en AdministracionControlador.
- [ ] Endpoints: `GET/POST /api/solicitudes/{id}/mensajes`, `GET /api/solicitudes/{id}/hilos`, `POST /api/mensajes/{id}/reportar`, `GET /api/admin/mensajes-reportados`.
- [ ] Frontend: `chat.html` (polling de 4 s con pausa por `visibilitychange` y *backoff*, adjuntos, reportar, solo lectura); pestaña "Mensajes reportados" en `admin.html`.

**Pruebas**
- [ ] `ServicioMensajeriaTest`: un tercero no puede leer ni escribir (403); hilo cerrado → 403; `.bat` → 415; `desde` devuelve solo los mensajes nuevos.
- [ ] Manual: dos navegadores ven los mensajes del otro en unos 4 s.

## Módulo 09 · `feature/09-finalizacion-pagos-reputacion`
**Depende de:** 07
- [ ] `ServicioNegociacion.finalizarServicio` y `cerrarAutomaticamente` (una transacción por servicio).
- [ ] `ServicioPagos` (`liberarPago`, `reembolsar`, `congelar`).
- [ ] `servicio/ServicioReputacion` (`calificar`, `cerrarResenas`, `recalcularPromedio`).
- [ ] `ServicioNotificaciones.recordar`.
- [ ] `ProgramadorTareasConfig`: cierre cada 15 min; recordatorios y reseñas cada hora.
- [ ] Controladores: PagoControlador, CalificacionControlador.
- [ ] Endpoints: `POST /api/servicios/{id}/finalizar`, `GET /api/pagos/servicio/{id}`, `GET /api/pagos/mios`, **`POST /api/pagos/simular-cierre-automatico` (solo ADMINISTRADOR)**, `POST/GET /api/servicios/{id}/calificaciones`.
- [ ] Frontend: desglose de liquidación en `negociacion.html`; `calificaciones.html`; reseñas en `perfil.html`; botón "Simular cierre automático" en `admin.html`.

**Pruebas**
- [ ] `ServicioPagosTest`: 40.000 / 10 % → 4.000 / 36.000, estado LIBERADO.
- [ ] Doble finalización → 409.
- [ ] `cerrarAutomaticamente` omite servicios con reporte abierto y los que aún no cumplen +24 h (salvo `ignorarPlazo`).
- [ ] `simular-cierre-automatico` con rol TUTOR → 403.
- [ ] `ServicioReputacionTest`: calificar un servicio no finalizado → 422; duplicado → 409; día 15 → 422; el promedio se recalcula.

## Módulo 10 · `feature/10-reportes-sanciones-despliegue`
**Depende de:** 08, 09
- [ ] `servicio/ServicioReportes` (`radicarReporte` → EN_REVISION + CONGELADO, `presentarDescargo` → Apelación, `resolverReporte` → reembolsar o liberar, `aplicarSancion`, `resolverApelacion`).
- [ ] Controladores: ReporteControlador; reportes y apelaciones en AdministracionControlador.
- [ ] Endpoints: `POST /api/reportes`, `GET /api/reportes/mios`, `GET /api/sanciones/mias`, `POST /api/sanciones/{id}/apelaciones`, `GET /api/admin/reportes`, `GET /api/admin/reportes/{id}`, `POST /api/admin/reportes/{id}/resolver`, `POST /api/admin/sanciones/{id}/apelaciones/{a}/resolver`.
- [ ] Frontend: `reportes.html` (radicar, mis reportes, mis sanciones, apelar); pestaña Reportes en `admin.html` (expediente con historial del chat, resolución, sanción).
- [ ] Cierre: `render.yaml`, README con despliegue, `diseno-api.md` final, revisión de "UniDrive".

**Pruebas**
- [ ] `ServicioReportesTest`: radicar congela el pago y bloquea la finalización; REEMBOLSAR → REEMBOLSADO + CANCELADO; sanción temporal suspende la cuenta; apelación revocada la reactiva; segunda apelación pendiente → 409.
- [ ] Recorrido E2E completo en Render (ver la guía de infraestructura).

---

## Recorrido E2E final (aceptación)
1. Registrar al estudiante Carlos y a la tutora Laura (`.edu.co`) → Laura sube su certificado.
2. El administrador ve a Laura con prioridad alta y la aprueba → "Tutor Verificado".
3. Carlos publica Ecuaciones Diferenciales por $25.000 con PDF; un intento de fraude se rechaza.
4. Laura contrapropone $30.000 / 90 min; un segundo tutor acepta $25.000; Carlos contraoferta.
5. Chat Carlos ↔ Laura con adjunto; reportar un mensaje.
6. Carlos selecciona a Laura y confirma (pago RETENIDO); la otra propuesta queda DESCARTADA.
7. Iniciar → finalizar → liquidación correcta → calificaciones bilaterales visibles.
8. En otro servicio: reporte de inasistencia → CONGELADO → resolución con reembolso y sanción → apelación → revocada.
9. El administrador ejecuta `simular-cierre-automatico?ignorarPlazo=true` sobre un servicio en desarrollo.
10. Revisar la auditoría y las notificaciones de cada actor.
