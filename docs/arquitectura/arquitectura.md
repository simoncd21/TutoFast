# Arquitectura de TutoFast

> Documento fuente de verdad. Jerarquía: `CLAUDE.md` > diagramas de `docs/diagramas/` > este documento > requisitos de `docs/requisitos/`.
> Los diagramas conservan el nombre histórico "UniDrive" en sus títulos y nombres de archivo; en todo el código y la interfaz el producto se llama **TutoFast**.

## 1. Visión general

TutoFast es una plataforma web universitaria de tutorías con **negociación dinámica de precios** inspirada en inDrive: el estudiante publica una necesidad con un precio inicial en COP, los tutores aceptan, contraproponen o sugieren otras condiciones, y el estudiante elige una propuesta que congela el acuerdo. La plataforma retiene el pago, cobra una comisión configurable y libera el neto al tutor cuando el estudiante confirma que terminó la sesión.

| Aspecto | Decisión |
|---|---|
| Estilo | Tres capas desacopladas (R15): Presentación · Lógica de aplicación y dominio · Persistencia, más una capa de integración de **puertos y adaptadores** |
| Backend | Java 21, Spring Boot 3.x, Spring Web, Spring Data MongoDB, Spring Validation, `spring-security-crypto` (BCrypt), `jjwt`. Artefacto `tutofast-backend.jar` |
| Persistencia | MongoDB Atlas (base de datos `tutofast`) + GridFS (bucket `archivos`) |
| Frontend | HTML5 + CSS3 (variables nativas) + JavaScript ES6 modular (`type="module"`, `fetch`). Sin frameworks ni build |
| Integraciones | Simulador interno de pasarela, notificaciones guardadas en MongoDB, almacenamiento en GridFS |
| Despliegue | GitHub → Render Web Service (Docker, `backend/`) + Render Static Site (`frontend/`) |

## 2. Las tres capas

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│ CAPA 1 · PRESENTACIÓN (Render Static Site)                                   │
│ index · acceso · solicitudes · negociacion · chat · calificaciones ·         │
│ reportes · admin · perfil   (HTML5 + CSS3 + JS ES6, tema claro/oscuro)       │
│ js/api.js ── fetch + Authorization: Bearer <JWT> ── JSON / multipart         │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │ HTTPS:443 · API REST (JSON)
┌───────────────────────────────▼──────────────────────────────────────────────┐
│ CAPA 2 · LÓGICA DE APLICACIÓN Y DOMINIO (Render Web Service · Docker)        │
│                                                                              │
│  FiltroAutenticacionJwt ─► InterceptorRoles (@RolesPermitidos)               │
│            │                                                                 │
│  controlador/  (≈ «ApiRest»: autenticarYAutorizar · validarEntrada · enrutar)│
│            │  DTO ◄─► mapper ◄─► modelo                                      │
│  servicio/   11 servicios de negocio (reglas, estados, transacciones)        │
│            │                                   │                             │
│  repositorio/ (MongoRepository)        integracion/puertos (interfaces)      │
│            │                                   │                             │
│            │                          integracion/adaptadores                │
└────────────┼───────────────────────────────────┼─────────────────────────────┘
             │ mongodb+srv · TLS (27017)          │
┌────────────▼───────────────────────────────────▼─────────────────────────────┐
│ CAPA 3 · PERSISTENCIA (MongoDB Atlas · clúster)                              │
│  BD `tutofast`: 16 colecciones de dominio + `notificaciones`                 │
│  GridFS bucket `archivos` (archivos.files / archivos.chunks)                 │
└──────────────────────────────────────────────────────────────────────────────┘
```

Reglas de dependencia:
1. La presentación **solo** habla con la API REST; nunca accede a la base de datos.
2. Los controladores **no** contienen lógica de negocio: autentican, validan la forma (`@Valid`), llaman a un servicio y convierten a DTO.
3. Los servicios solo acceden a la persistencia mediante repositorios y a los sistemas externos mediante puertos (`IPasarelaPago`, `INotificador`, `IAlmacenamiento`).
4. Los adaptadores son los únicos que conocen GridFS, el simulador de pagos y la colección `notificaciones`.

## 3. Flujo de una petición

```text
Navegador (frontend/js/paginas/negociacion.js)
   │  POST /api/solicitudes/{id}/confirmar   Authorization: Bearer eyJ...
   ▼
FiltroAutenticacionJwt ── valida firma/expiración (JWT_SECRET) y estado ACTIVA
   │                      → request.setAttribute(UsuarioAutenticado)
   ▼
InterceptorRoles ── @RolesPermitidos(ESTUDIANTE) ? sigue : 403
   ▼
NegociacionControlador.confirmar(@Valid ConfirmacionDTO, UsuarioAutenticado)
   ▼
ServicioNegociacion.confirmarServicio()  ── @Transactional (MongoTransactionManager en Atlas)
   ├─► RepositorioSolicitudTutoria / RepositorioPropuesta   (validar dueño y estado TUTOR_SELECCIONADO)
   ├─► ServicioCatalogo.consultarComision()                  (porcentaje vigente → congelado)
   ├─► ServicioPagos.retenerFondos() ─► IPasarelaPago ─► AdaptadorPasarela (simulador)
   ├─► RepositorioServicioTutoria.save()  ·  RepositorioPago.save()
   ├─► RepositorioPropuesta (otras → DESCARTADA)
   ├─► ServicioNotificaciones.notificar() ─► INotificador ─► AdaptadorNotificaciones ─► colección notificaciones
   └─► ServicioAuditoria.registrarAccion() ─► registros_auditoria
   ▼
ServicioDTO (JSON 200) ─► ManejadorGlobalExcepciones si algo falla (4xx/5xx con {codigo, mensaje})
```

Flujo con archivos (GridFS **fuera** de la transacción):

```text
POST /api/solicitudes (multipart)
  1. ServicioSolicitudes.validarIntegridad(datos)        ← sin E/S; rechaza antes de subir
  2. ServicioArchivos.validarFormato/validarTamano       ← PDF, PNG, JPG, DOCX ≤ 10 MB
  3. ServicioArchivos.guardar() ─► IAlmacenamiento ─► GridFS   (sin @Transactional)
  4. ServicioSolicitudes.persistir(solicitud con descriptores Archivo)  (@Transactional si aplica)
  5. Si el paso 4 falla → compensación: ServicioArchivos.eliminar(ids subidos)
```

## 4. Correspondencia con los 5 diagramas

### 4.1 Diagrama de clases (`DiagramaClases_UniDrive_v2.png`)
Las **22 clases** se implementan tal cual en `com.tutofast.modelo`: Usuario, PerfilEstudiante, PerfilTutor, SoporteVerificacion, Universidad, Facultad, ProgramaAcademico, Asignatura, ParametroComision, SolicitudTutoria, Propuesta, ServicioTutoria, Mensaje, Archivo, Pago, Calificacion (abstracta), CalificacionAlTutor, CalificacionAlEstudiante, Reporte, Sancion, Apelacion, RegistroAuditoria.
- **Composición** (♦) → documento embebido cuando la parte no vive sola y su tamaño está acotado (PerfilEstudiante, PerfilTutor, SoporteVerificacion, Apelacion). Las composiciones con crecimiento ilimitado o con escritura concurrente (Mensaje, Propuesta, Calificacion) van en colección propia con referencia al todo.
- **Agregación** (◇) → colección propia + referencia por id (catálogo académico; Archivo como descriptor embebido con el binario en GridFS).
- **Generalización** → herencia en una sola colección (`calificaciones`) con discriminador `tipo`.
- **Dependencia «use»** ServicioTutoria → ParametroComision → el porcentaje vigente se copia en `servicios_tutoria.comisionPct` al confirmar.
- Detalle completo en [`diseno-base-datos.md`](diseno-base-datos.md).

### 4.2 Diagrama de componentes (`DiagramaComponentes_UniDrive_v2.png`)

| Componente | Interfaz provista | Implementación TutoFast |
|---|---|---|
| Aplicación Web y Móvil | — | `frontend/` (web responsive; sin app nativa) |
| Panel de Administración | — | `frontend/admin.html` |
| Gestión de Usuarios y Verificación | IPerfiles | `ServicioIdentidad` |
| Gestión de Catálogos y Comisión | ICatalogo, IComision | `ServicioCatalogo` |
| Gestión de Solicitudes | ISolicitudes | `ServicioSolicitudes` |
| Negociación y Ciclo de Vida del Servicio | IServicioTutoria | `ServicioNegociacion` |
| Liquidación y Pagos | IPagos | `ServicioPagos` |
| Mensajería Contextual | IMensajeria | `ServicioMensajeria` |
| Gestión de Archivos | IArchivos | `ServicioArchivos` |
| Reputación y Calificaciones | — | `ServicioReputacion` |
| Reportes y Sanciones | — | `ServicioReportes` |
| Notificaciones | INotificaciones | `ServicioNotificaciones` |
| Auditoría | IAuditoria | `ServicioAuditoria` |
| «external» Pasarela de pagos | API pasarela | `IPasarelaPago` → `AdaptadorPasarela` (**simulador interno**) |
| «external» Notificaciones y correo | INotificador | `INotificador` → `AdaptadorNotificaciones` (**colección MongoDB**, sin correo) |
| «external» Almacenamiento de archivos | IAlmacenamiento | `IAlmacenamiento` → `AdaptadorAlmacenamiento` (**GridFS**) |

La nota del diagrama que menciona "PostgreSQL ACID (R16, pendiente de confirmar)" queda **reemplazada** por MongoDB Atlas, según la vista de despliegue y `CLAUDE.md` (ver ADR-002).

### 4.3 Vista de desarrollo (`VistaDesarrollo_UniDrive.png`)

| Capa del diagrama | Paquete / carpeta |
|---|---|
| Presentación: PaginaAcceso, PaginaSolicitudes, PaginaNegociacion, PaginaChat, PaginaCalificaciones, PaginaReportes, PanelAdministracion | `acceso.html`, `solicitudes.html`, `negociacion.html`, `chat.html`, `calificaciones.html`, `reportes.html`, `admin.html` (+ `index.html` y `perfil.html`) |
| «controlador» ApiRest (autenticarYAutorizar, validarEntrada, enrutar) | `seguridad/` (filtro + interceptor) + `controlador/` (15 controladores) + `ManejadorGlobalExcepciones` |
| Servicios (11) | `servicio/` |
| «repositorio» RepositorioDominio (guardar, buscarPorId, consultar, actualizar, eliminar) | `repositorio/` (una interfaz `MongoRepository` por colección) |
| ModeloDominio (22 clases) | `modelo/` |
| Integración: IPasarelaPago, INotificador, IAlmacenamiento + 3 adaptadores | `integracion/puertos/`, `integracion/adaptadores/` |

Dependencias entre servicios (flechas «use» del diagrama, respetadas en el código):

```text
ServicioReputacion ──► ServicioNegociacion ──► ServicioPagos ──► ServicioCatalogo
ServicioReportes   ──► ServicioNegociacion ──► ServicioSolicitudes ──► ServicioCatalogo
ServicioReportes   ──► ServicioMensajeria  ──► ServicioArchivos   ◄── ServicioSolicitudes
                       ServicioNegociacion ──► ServicioMensajeria
                       ServicioNegociacion ──► ServicioNotificaciones
(Todos) ──► ServicioAuditoria · ServicioNotificaciones          (nota al pie del diagrama)
ServicioIdentidad, ServicioReportes ──► ServicioArchivos       (nota del diagrama de componentes)
ServicioPagos ──► IPasarelaPago · ServicioArchivos ──► IAlmacenamiento · ServicioNotificaciones ──► INotificador
```

No se permiten ciclos: ServicioNegociacion nunca llama a ServicioReputacion ni a ServicioReportes. Esos servicios consultan a ServicioNegociacion.

### 4.4 Vista de despliegue (`VistaDespliegue_UniDrive.png`)

```text
 :Cliente / :Navegador ──HTTPS:443──► Render · Static Site  (frontend/)
          │
          └────HTTPS:443 · API REST (JSON)──► Render · Web Service (Docker)
                                              ├─ tutofast-backend.jar
                                              └─ Simulador de pasarela (módulo interno)
                                                        │ mongodb+srv · TLS 27017
                                                        ▼
                                              MongoDB Atlas · Clúster
                                              ├─ BD tutofast
                                              └─ GridFS (PDF·PNG·JPG·DOCX ≤ 10 MB)
 GitHub · Repositorio ──despliegue automático HTTPS:443──► ambos servicios de Render
```

### 4.5 DFD nivel 0 (`diagrama DFD nivel 0.png`)

| Entidad externa | Entradas al sistema | Salidas del sistema | Dónde se implementa |
|---|---|---|---|
| Estudiante | Registro y consentimiento de datos; solicitud y selección de propuesta; autorización de pago; confirmación de finalización; calificación, reportes y descargos | Propuestas y mensajes; estado de la solicitud; perfiles y reseñas de tutores; resultado de reportes | acceso, solicitudes, negociacion, chat, calificaciones, reportes |
| Tutor | Registro y soportes; ofertas, contrapropuestas y mensajes; cuenta bancaria de cobro; confirmación de condiciones y enlace de sesión; calificación y descargos | Solicitudes abiertas; servicio confirmado; liquidación (pago neto); resultado de verificación; reseñas y sanciones | perfil, solicitudes, negociacion, chat, calificaciones, reportes |
| Administrador | Catálogos y comisión; resultado de verificación; resolución de reportes y sanciones | Soportes por verificar; reportes y reclamaciones | admin |
| Pasarela de pagos | Estado de la transacción | Órdenes de retención, liberación y reembolso | `AdaptadorPasarela` (simulado) |
| Notificaciones y correo | Confirmación de entrega | Avisos y recordatorios | `AdaptadorNotificaciones` (colección MongoDB) |

Notas del DFD que se respetan: una misma cuenta puede activar los roles Estudiante y Tutor; los datos de tarjeta **no** se almacenan; la videollamada es externa y las partes solo comparten el enlace (`ServicioTutoria.lugarOEnlace`).

## 5. Ciclo de vida (R10)

```text
SolicitudTutoria:  PUBLICADA ─► PROPUESTAS_RECIBIDAS ─► NEGOCIACION_EN_CURSO ─► TUTOR_SELECCIONADO ─► FORMALIZADA
                       └──────────────┴───────────────────────┴──► CANCELADA  (solo antes de confirmar, R05)
ServicioTutoria:   CONFIRMADO ─► EN_DESARROLLO ─► FINALIZADO
                       │              │
                       └──────────────┴──► EN_REVISION (reporte) ─► FINALIZADO | CANCELADO (resolución admin)
Pago:              RETENIDO ─► LIBERADO | CONGELADO ─► LIBERADO | REEMBOLSADO
```

Correspondencia con los 9 estados de R10 que se muestran en la interfaz: Solicitud publicada = PUBLICADA · Propuestas recibidas = PROPUESTAS_RECIBIDAS · Negociación en curso = NEGOCIACION_EN_CURSO · Tutor seleccionado = TUTOR_SELECCIONADO · Servicio confirmado = servicio CONFIRMADO · Tutoría en desarrollo = EN_DESARROLLO · Servicio finalizado = FINALIZADO · Cancelado = CANCELADA/CANCELADO · En revisión por un reporte o una disputa = EN_REVISION.

## 6. Aspectos transversales

### 6.1 Seguridad
- Contraseñas con `BCryptPasswordEncoder` (factor 10). JWT HS256 firmado con `JWT_SECRET` (≥ 32 bytes), con *claims* `sub` (id), `correo`, `roles`, `exp` (por defecto 8 h).
- `FiltroAutenticacionJwt` deja pasar sin token: `GET /api/salud`, `POST /api/auth/registro`, `POST /api/auth/iniciar-sesion`, `GET /api/catalogos/**` y `OPTIONS`.
- La autorización por rol se declara con `@RolesPermitidos` y la verifica `InterceptorRoles`. La autorización por propiedad (dueño o participante) se verifica en los servicios y lanza `ExcepcionAutorizacion` (403).
- CORS: solo `FRONTEND_URL` y los orígenes locales (`http://localhost:5500`, `http://127.0.0.1:5500`).
- XSS: el frontend pinta los datos de usuario con `textContent` o `escaparHTML`.

### 6.2 Transacciones (ajuste aprobado)
- `GridFsConfig` registra `MongoTransactionManager` **solo** si `tutofast.mongo.transacciones=true` (variable `MONGO_TRANSACCIONES`, `true` en Render/Atlas). Con `false`, registra un gestor no-op (`GestorTransaccionesNoOp`), de modo que `@Transactional` no falla en un Mongo local sin replica set.
- Las pruebas unitarias usan JUnit 5 + Mockito sin contexto de Spring, así que no dependen de la configuración de transacciones.
- Las subidas y descargas de GridFS **nunca** se hacen dentro de un método `@Transactional`: primero se suben los archivos y después se ejecuta la transacción. Si la transacción falla, una compensación elimina lo subido (`IAlmacenamiento.eliminar`, método técnico añadido al puerto).
- Las colecciones e índices se crean al arrancar (`DatosInicialesSeed`), porque crear colecciones dentro de una transacción tiene restricciones.

### 6.3 Tareas programadas (`ProgramadorTareasConfig`)

| Frecuencia | Tarea |
|---|---|
| cada 5 min | CONFIRMADO → EN_DESARROLLO cuando llega `fechaHora` |
| cada 15 min | `ServicioNegociacion.cerrarAutomaticamente()`: EN_DESARROLLO con fin programado + 24 h vencido, sin reporte abierto → FINALIZADO + liquidación (HU-08) |
| cada hora | `ServicioNotificaciones.recordar()` y `ServicioReputacion.cerrarResenas()` (14 días) |

Para la demo, `POST /api/pagos/simular-cierre-automatico` (solo ADMINISTRADOR) ejecuta el cierre automático al momento.

### 6.4 Errores
Formato único: `{"codigo":"ESTADO_INVALIDO","mensaje":"...","detalles":[],"fecha":"..."}`. Códigos HTTP: 400 validación, 401 sin sesión, 403 rol o propiedad, 404 no encontrado, 409 conflicto de estado o duplicado, 413 archivo > 10 MB, 415 tipo no permitido, 422 regla de negocio (antifraude), 500 inesperado.

### 6.5 Configuración (variables de entorno)

| Variable | Uso | Ejemplo |
|---|---|---|
| `MONGODB_URI` | Conexión a Atlas (BD `tutofast`) | `mongodb+srv://usuario:clave@cluster0.xxxxx.mongodb.net/tutofast?retryWrites=true&w=majority` |
| `JWT_SECRET` | Firma de tokens | cadena aleatoria de 64 caracteres |
| `JWT_EXPIRACION_HORAS` | Vigencia del token | `8` |
| `FRONTEND_URL` | Origen permitido por CORS | `https://tutofast.onrender.com` |
| `PORT` | Puerto HTTP (Render lo inyecta) | `8080` |
| `MONGO_TRANSACCIONES` | Activa `MongoTransactionManager` | `true` en Atlas |
| `ADMIN_CORREO` / `ADMIN_CLAVE` | Administrador inicial (seed) | `admin@tutofast.co` |
