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
- [ ] `integracion/puertos/`: IPasarelaPago, INotificador, IAlmacenamiento (`guardar`, `recuperar`, `eliminar`), ResultadoTransaccion, ArchivoRecuperado.
- [ ] `integracion/adaptadores/`: AdaptadorPasarela (simulador `SIM-uuid`, `tok_rechazar`), AdaptadorNotificaciones (+ DocumentoNotificacion), AdaptadorAlmacenamiento (GridFsTemplate).
- [ ] `servicio/ServicioArchivos` (`validarFormato`, `validarTamano`, `guardar`, `recuperar`, `eliminar`, control de acceso por contexto, limpieza diaria de huérfanos).
- [ ] `servicio/ServicioNotificaciones` (`notificar`, `consultar`, `marcarLeida`).
- [ ] Controladores: ArchivoControlador, NotificacionControlador; soportes en UsuarioControlador.
- [ ] Endpoints: `GET /api/archivos/{id}`, `GET /api/notificaciones`, `GET /api/notificaciones/conteo-no-leidas`, `PATCH /api/notificaciones/{id}/leida`, `PATCH /api/notificaciones/leer-todas`, `POST|GET /api/usuarios/yo/soportes`.
- [ ] Frontend: campana con polling cada 30 s en el encabezado; lista de notificaciones y subida de soportes en `perfil.html`; descarga autenticada en `utilidades.js`.

**Pruebas**
- [ ] `ServicioArchivosTest`: `.exe` → 415; 11 MB → 413; PDF, PNG, JPG y DOCX aceptados.
- [ ] `AdaptadorPasarelaTest`: aprobado y rechazado.
- [ ] Manual: subir un soporte → aparece en `archivos.files`; nivel → EN_REVISION.

## Módulo 05 · `feature/05-catalogos-comision-admin`
**Depende de:** 03, 04
- [ ] `servicio/ServicioCatalogo` (`gestionarCatalogo`, `consultarCatalogo`, `configurarComision`, `consultarComision`).
- [ ] `ServicioIdentidad.verificarTutor` + cola con **prioridad `.edu.co`**.
- [ ] Controladores: CatalogoControlador, ComisionControlador, AdministracionControlador (soportes, usuarios, auditoría).
- [ ] Endpoints: `/api/catalogos/**`, `/api/comision/**`, `GET /api/admin/soportes`, `POST /api/admin/usuarios/{u}/soportes/{s}/aprobar|rechazar`, `GET /api/admin/usuarios`, `PATCH /api/admin/usuarios/{id}/estado`, `GET /api/admin/auditoria`.
- [ ] Seed: universidad, facultad, programa y asignaturas de demo + comisión del 10 %.
- [ ] Frontend: `admin.html` (pestañas Catálogos, Comisión, Verificación de tutores con insignia "Correo institucional · Prioridad alta", Usuarios, Auditoría); selects encadenados de registro en `acceso.html`.

**Pruebas**
- [ ] `ServicioCatalogoTest`: IS-204 duplicado → 409; cambio de comisión cierra la vigente.
- [ ] `ServicioIdentidadTest`: `.edu.co` primero en la cola; aprobar → VERIFICADO.
- [ ] Manual: el administrador crea una asignatura y la ve en el registro.

## Módulo 06 · `feature/06-solicitudes-busqueda`
**Depende de:** 04, 05
- [ ] `servicio/ServicioSolicitudes` (`publicarSolicitud`, `editarSolicitud`, `cancelarSolicitud`, `buscarSolicitudes`, `validarIntegridad` con listas configurables de frases genéricas y palabras de fraude).
- [ ] Orden en la publicación: validar → subir a GridFS → persistir (compensar si falla).
- [ ] `ServicioIdentidad.buscarTutores`.
- [ ] Controladores: SolicitudControlador, TutorControlador.
- [ ] Endpoints: `POST/GET /api/solicitudes`, `GET /api/solicitudes/mias`, `GET/PUT /api/solicitudes/{id}`, `POST /api/solicitudes/{id}/cancelar`, `POST /api/solicitudes/{id}/archivos`, `GET /api/tutores`, `GET /api/tutores/{id}`, `GET /api/usuarios/{id}/resenas`.
- [ ] Frontend: `solicitudes.html` (publicar, mis solicitudes, explorar con filtros, buscar tutores); perfil público en `perfil.html?id=`.

**Pruebas**
- [ ] `ServicioSolicitudesTest`: "necesito ayuda con matemáticas" → 422; "resolver examen parcial en vivo" → 422 `FRAUDE_ACADEMICO` + auditoría; duración 90 → 400; cancelar después de FORMALIZADA → 409.
- [ ] Búsqueda sin resultados → mensaje vacío en la interfaz.

## Módulo 07 · `feature/07-negociacion-confirmacion`
**Depende de:** 06
- [ ] `servicio/ServicioNegociacion` (`enviarPropuesta`, `modificar`, `retirar`, `contraofertar`, `seleccionarPropuesta`, `confirmarServicio` @Transactional, `iniciarSesion`).
- [ ] `servicio/ServicioPagos` (`retenerFondos`, `calcularLiquidacion`).
- [ ] Controlador: NegociacionControlador.
- [ ] Endpoints: `POST/GET /api/solicitudes/{id}/propuestas`, `PUT /api/propuestas/{id}`, `POST /api/propuestas/{id}/retirar|contraofertar|seleccionar`, `POST /api/solicitudes/{id}/confirmar`, `GET /api/servicios/mios`, `GET /api/servicios/{id}`, `PATCH /api/servicios/{id}/enlace`, `POST /api/servicios/{id}/iniciar`.
- [ ] `ProgramadorTareasConfig`: inicio automático CONFIRMADO → EN_DESARROLLO.
- [ ] Frontend: `negociacion.html` (comparativa de propuestas, formulario del tutor con los 3 tipos, contraoferta, modal "Autorizar pago simulado y confirmar", ficha del servicio).

**Pruebas**
- [ ] `ServicioNegociacionTest`: segunda propuesta activa del mismo tutor → 409; propuesta sobre solicitud FORMALIZADA → 409; confirmar descarta las demás y congela `comisionPct`; `tok_rechazar` → 422 sin cambios de estado; tutor dueño de la solicitud → 403.

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
