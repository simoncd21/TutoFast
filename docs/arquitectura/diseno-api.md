# Diseño de la API REST de TutoFast

> Única documentación oficial de la API (sin Swagger/OpenAPI). Base: `https://<backend>.onrender.com` en producción y `http://localhost:8080` en local. Todas las rutas empiezan por `/api`.

## 1. Convenciones

| Tema | Regla |
|---|---|
| Formato | JSON UTF-8. Subida de archivos con `multipart/form-data`: parte `datos` (JSON, `application/json`) + parte(s) de archivo |
| Autenticación | `Authorization: Bearer <JWT>` (salvo rutas públicas) |
| Roles | **P** público · **A** cualquier usuario autenticado · **E** ESTUDIANTE · **T** TUTOR · **AD** ADMINISTRADOR · **Part.** participante del recurso (lo valida el servicio) |
| Fechas | ISO-8601 UTC (`2026-12-05T19:00:00Z`) |
| Dinero | número entero en COP (`25000`) |
| Paginación | `?pagina=0&tamano=20` → `{"contenido":[...],"pagina":0,"tamano":20,"totalElementos":57,"totalPaginas":3}` |
| Identificadores | cadenas hexadecimales de 24 caracteres (ObjectId) |

### 1.1 Formato de error
```json
{
  "codigo": "ESTADO_INVALIDO",
  "mensaje": "La solicitud ya ha sido acordada con otro tutor y no admite nuevas propuestas",
  "detalles": [],
  "fecha": "2026-11-23T16:12:00Z"
}
```

| HTTP | `codigo` | Cuándo |
|---|---|---|
| 400 | `VALIDACION` | Fallo de `@Valid`; `detalles` = `[{"campo":"descripcion","mensaje":"..."}]` |
| 401 | `NO_AUTENTICADO` | Token ausente, inválido o expirado |
| 403 | `NO_AUTORIZADO` | Rol insuficiente, no es dueño/participante, o cuenta suspendida |
| 404 | `NO_ENCONTRADO` | El recurso no existe |
| 409 | `ESTADO_INVALIDO` / `DUPLICADO` | Transición de estado no permitida, o índice único violado |
| 413 | `ARCHIVO_DEMASIADO_GRANDE` | Archivo > 10 MB |
| 415 | `TIPO_ARCHIVO_NO_PERMITIDO` | Archivo que no es PDF, PNG, JPG ni DOCX |
| 422 | `REGLA_NEGOCIO` / `FRAUDE_ACADEMICO` / `PAGO_RECHAZADO` | Regla de dominio (descripción vaga, antifraude, plazo vencido, pago rechazado por el simulador) |
| 500 | `ERROR_INTERNO` | Error inesperado (no se expone la traza) |

### 1.2 Controladores
| Controlador | Prefijo | Servicio principal |
|---|---|---|
| SaludControlador | `/api/salud` | — |
| AutenticacionControlador | `/api/auth` | ServicioIdentidad |
| UsuarioControlador | `/api/usuarios/yo` | ServicioIdentidad |
| TutorControlador | `/api/tutores`, `/api/usuarios/{id}/resenas` | ServicioIdentidad |
| CatalogoControlador | `/api/catalogos` | ServicioCatalogo |
| ComisionControlador | `/api/comision` | ServicioCatalogo |
| SolicitudControlador | `/api/solicitudes` | ServicioSolicitudes |
| PropuestaControlador | `/api/solicitudes/{id}/propuestas`, `/api/propuestas` | ServicioPropuestas |
| NegociacionControlador | `/api/solicitudes/{id}/confirmar`, `/api/servicios` | ServicioNegociacion |
| PagoControlador | `/api/pagos` | ServicioPagos *(el cierre automático, con ServicioNegociacion, llega en el módulo 09)* |
| MensajeControlador | `/api/solicitudes/{id}/mensajes`, `/api/mensajes` | ServicioMensajeria |
| ArchivoControlador | `/api/archivos` | ServicioArchivos |
| CalificacionControlador | `/api/servicios/{id}/calificaciones` | ServicioReputacion |
| ReporteControlador | `/api/reportes`, `/api/sanciones` | ServicioReportes |
| NotificacionControlador | `/api/notificaciones` | ServicioNotificaciones |
| AdministracionControlador | `/api/admin` | ServicioIdentidad, ServicioReportes, ServicioMensajeria, ServicioAuditoria |

---

## 2. Salud

### `GET /api/salud` · P
200:
```json
{ "estado": "UP", "servicio": "tutofast-backend", "version": "1.0.0", "baseDatos": "UP", "fecha": "2026-11-23T15:00:00Z" }
```
Si MongoDB no responde: 503 con `"estado":"DEGRADADO","baseDatos":"DOWN"`. Render usa esta ruta como *health check*.

---

## 3. Autenticación — `AutenticacionControlador`

### `POST /api/auth/registro` · P
```json
{
  "nombre": "Carlos Gómez",
  "correo": "estudiante@universidad.edu.co",
  "documento": "1098765432",
  "clave": "Segura#2026",
  "rol": "ESTUDIANTE",
  "aceptaTratamientoDatos": true,
  "programaId": "6741a0c1e4b0a1b2c3d4e103",
  "semestre": 4
}
```
- `rol`: `ESTUDIANTE` o `TUTOR` (ADMINISTRADOR solo se crea por seed). Clave de 8 caracteres o más, con letra y número.
- 201: `{"mensaje":"Registro exitoso en TutoFast","usuarioId":"..."}`
- 422 si `aceptaTratamientoDatos` es `false`: `"Debe aceptar la política de tratamiento de datos conforme a la Ley 1581 de 2012 para continuar"`.
- 409 `DUPLICADO` si el correo o el documento ya existen.
- Si el rol es TUTOR, el perfil se crea con `nivelVerificacion=NO_VERIFICADO` (etiqueta "Tutor Registrado (No Verificado)").

### `POST /api/auth/iniciar-sesion` · P
```json
{ "correo": "estudiante@universidad.edu.co", "clave": "Segura#2026" }
```
200:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiraEn": "2026-11-23T23:00:00Z",
  "usuario": { "id": "6741...502", "nombre": "Carlos Gómez", "correo": "estudiante@universidad.edu.co", "roles": ["ESTUDIANTE"], "estadoCuenta": "ACTIVA" }
}
```
401 con el mensaje genérico "Correo o contraseña incorrectos". 403 si la cuenta está SUSPENDIDA o BLOQUEADA.

### `GET /api/auth/yo` · A
200: `UsuarioDTO` (mismo formato que `usuario` en el inicio de sesión).

### 3.1 Notas de implementación (módulo de autenticación)
- El correo se normaliza a minúsculas y se recorta antes de guardarlo y de buscarlo.
- Validación del registro (400 `VALIDACION`): `documento` de 5 a 15 dígitos; `clave` de 8 a 64 caracteres con letra y número; `semestre` de 1 a 14; `programaId` (opcional) de 24 caracteres hexadecimales.
- El token JWT (HS256, firmado con `JWT_SECRET`, vigencia `JWT_EXPIRACION_HORAS`, 8 h por defecto) solo lleva el id del usuario. En cada petición protegida `FiltroAutenticacionJwt` recarga al usuario: los roles y el estado de cuenta siempre son los vigentes, y una cuenta SUSPENDIDA o BLOQUEADA recibe 403 aunque su token no haya expirado.
- Rutas públicas: `/api/salud`, `/api/auth/registro` y `/api/auth/iniciar-sesion`. Cualquier otra ruta `/api/**` exige `Authorization: Bearer <token>` (401 `NO_AUTENTICADO` si falta o es inválido). Los roles se exigen con `@RolesPermitidos` (403 `NO_AUTORIZADO`).
- Administrador inicial: si se definen `ADMIN_CORREO` y `ADMIN_CLAVE` y no existe ningún ADMINISTRADOR, `DatosInicialesSeed` lo crea al arrancar.
- Prueba manual: `docs/pruebas/modulo-02-autenticacion.ps1`.

### 3.2 Auditoría de identidad y cliente web (módulo 03)
- `ServicioAuditoria.registrarAccion(usuarioId, accion, entidad, detalle)` guarda en `registros_auditoria` (solo inserción; `usuarioId` nulo = acción del sistema) y `consultar(usuarioId, entidad, desde, hasta, paginacion)` devuelve los registros más recientes primero con filtros opcionales combinables. Nunca se guardan claves, tokens ni datos de tarjeta.
- Acciones registradas por `ServicioIdentidad` (entidad `Usuario`): `REGISTRO_USUARIO`, `CONSENTIMIENTO_DATOS` (detalle `norma: "Ley 1581 de 2012"` y fecha), `INICIO_SESION`, `INICIO_SESION_FALLIDO` (solo si el correo existe), `ACTUALIZAR_PERFIL_ESTUDIANTE`, `ACTUALIZAR_PERFIL_TUTOR`, `ACTIVAR_ROL` y `SEED_ADMINISTRADOR`. Los intentos rechazados por validación o consentimiento no generan registro.
- El endpoint de lectura (`GET /api/admin/auditoria`) se expone en el módulo de administración (sección 16).
- Cliente web: `frontend/js/sesion.js` guarda el token y el usuario en `localStorage` (`tutofast_token`, `tutofast_usuario`, `tutofast_rol_activo`) y recarga el usuario con `GET /api/auth/yo` al abrir páginas privadas; ante 401/403 limpia la sesión y redirige a `acceso.html?siguiente=...`. El rol activo es solo de interfaz: el servidor valida siempre los roles reales del token.

---

## 4. Usuarios y perfiles — `UsuarioControlador`

| Método y ruta | Rol | Cuerpo | Respuesta |
|---|---|---|---|
| `GET /api/usuarios/yo` | A | — | 200 `PerfilCompletoDTO` |
| `PUT /api/usuarios/yo/perfil-estudiante` | E | `{"programaId","semestre"}` | 200 `PerfilCompletoDTO` |
| `PUT /api/usuarios/yo/perfil-tutor` | T | `PerfilTutorDTO` | 200 `PerfilCompletoDTO` |
| `POST /api/usuarios/yo/roles` | A | `{"rol":"TUTOR"}` | 200 `UsuarioDTO` (409 si ya lo tiene) |
| `POST /api/usuarios/yo/soportes` | T | multipart: `datos={"tipo":"CERTIFICADO_MONITORIA"}`, `archivo` | 201 `SoporteDTO`; nivel → EN_REVISION |
| `GET /api/usuarios/yo/soportes` | T | — | 200 `SoporteDTO[]` |
| `GET /api/usuarios/{id}/resenas` | A | — | 200 `ResenaDTO[]` (solo `visible=true`) |

`PerfilTutorDTO`:
```json
{
  "presentacion": "Monitora de cálculo...",
  "areasDeInteres": ["Ecuaciones diferenciales"],
  "formacion": "Ingeniería de Sistemas",
  "tipoTutor": "MONITOR_CERTIFICADO",
  "experiencia": "4 semestres",
  "modalidades": ["VIRTUAL", "PRESENCIAL"],
  "disponibilidad": "L-J 14:00-18:00",
  "asignaturasDominadasIds": ["6741...104"],
  "cuentaBancariaCobro": "Bancolombia ahorros ****4821"
}
```

`SoporteDTO`: `{"id","tipo","archivoId","nombreArchivo","estado","fechaEnvio","fechaRevision","observacion"}`.

`POST /api/usuarios/yo/soportes`: la parte `datos` debe enviarse con `Content-Type: application/json`. Errores: 415 (formato o firma no permitidos), 413 (> 10 MB), 400 (archivo vacío o `tipo` inválido), 409 (ya hay 5 soportes en revisión). Si el nivel era `NO_VERIFICADO` pasa a `EN_REVISION`; un tutor `VERIFICADO` conserva su nivel.

`ResenaDTO`: `{"tipo":"AL_TUTOR","autorNombre","comentario","promedio":4.83,"criterios":{...},"fechaEmision"}`. `GET /api/usuarios/{id}/resenas` devuelve 404 si el usuario no existe y `[]` si todavía no tiene reseñas visibles (las reseñas llegan con el módulo de reputación).

---

## 5. Tutores — `TutorControlador`

### `GET /api/tutores` · A
Filtros: `programaId`, `asignaturaId`, `modalidad`, `disponibilidad` (texto), `calificacionMin` (0–5), `soloVerificados` (bool), `orden` (`calificacion` | `servicios`), paginación (`tamano` por defecto 12, máximo 50). `asignaturaId` prevalece sobre `programaId` (que se expande a las asignaturas del programa). Solo devuelve tutores con la cuenta ACTIVA (excluye SUSPENDIDA y BLOQUEADA). Orden por defecto: mejor calificados (los que no tienen calificación quedan al final). `orden` inválido, `modalidad` desconocida o `calificacionMin` fuera de 0–5 → 400.

200 `Pagina<TutorResumenDTO>`:
```json
{ "contenido": [{ "id": "6741...501", "nombre": "Laura Martínez", "tipoTutor": "MONITOR_CERTIFICADO",
  "verificado": true, "promedioCalificacion": 4.75, "serviciosCompletados": 12,
  "modalidades": ["VIRTUAL","PRESENCIAL"], "areasDeInteres": ["Ecuaciones diferenciales"] }],
  "pagina": 0, "tamano": 20, "totalElementos": 1, "totalPaginas": 1 }
```

### `GET /api/tutores/{id}` · A
200 `PerfilPublicoTutorDTO`: `{"id","nombre","declarado":{presentacion, areasDeInteres, formacion, experiencia, modalidades, disponibilidad, asignaturas:[nombre]},"verificado":{nivelVerificacion, etiqueta, soportesAprobados:[{tipo, fechaRevision}]},"promedioCalificacion","serviciosCompletados","resenas":[ResenaDTO]}`. Nunca expone la cuenta bancaria, el documento ni el correo. 404 si el tutor no existe, no tiene perfil de tutor o su cuenta no está activa.

---

## 6. Catálogos — `CatalogoControlador`

| Método y ruta | Rol | Cuerpo | Respuesta |
|---|---|---|---|
| `GET /api/catalogos/universidades?soloActivas=true` | P | — | 200 `[{"id","nombre","activa"}]` |
| `GET /api/catalogos/universidades/{id}/facultades` | P | — | 200 `[FacultadDTO]` |
| `GET /api/catalogos/facultades/{id}/programas` | P | — | 200 `[ProgramaDTO]` |
| `GET /api/catalogos/programas/{id}/asignaturas` | P | — | 200 `[AsignaturaDTO]` |
| `GET /api/catalogos/asignaturas/{id}` | P | — | 200 `AsignaturaDetalleDTO` (incluye la jerarquía completa) |
| `POST /api/catalogos/universidades` | AD | `{"nombre"}` | 201 |
| `POST /api/catalogos/facultades` | AD | `{"universidadId","nombre"}` | 201 |
| `POST /api/catalogos/programas` | AD | `{"facultadId","nombre"}` | 201 |
| `POST /api/catalogos/asignaturas` | AD | `{"programaId","codigo":"IS-204","nombre":"Ecuaciones Diferenciales"}` | 201; 409 "La asignatura con este código ya se encuentra registrada en el programa" |
| `PUT /api/catalogos/{tipo}/{id}` | AD | mismo cuerpo que el alta | 200 |
| `PATCH /api/catalogos/{tipo}/{id}/activa` | AD | `{"activa":false}` | 200 (borrado lógico) |

`{tipo}` ∈ `universidades | facultades | programas | asignaturas`.

**Notas de implementación (módulo 05)**
- Las consultas `GET /api/catalogos/**` son públicas (sin token); el alta, la edición y el borrado lógico exigen AD. Las listas aceptan `?soloActivas=true` (por defecto) o `false` (panel de administración).
- Se añade `GET /api/catalogos/programas/{id}` → `{"programa","facultad","universidad"}` para precargar los selects encadenados.
- `POST /api/catalogos/{tipo}` usa el mismo cuerpo para los cuatro tipos: `{"universidadId"|"facultadId"|"programaId", "codigo"?, "nombre"}` según el nivel. `PUT` solo cambia `nombre` (y `codigo` en asignaturas); no se mueve un elemento de padre. Los elementos desactivados conservan a sus hijos.
- El código de asignatura se normaliza (mayúsculas, sin espacios) y es único por programa; nombres de universidad, facultad y programa son únicos dentro de su padre → 409 `DUPLICADO`. Padre inexistente → 404; falta el padre o el código → 400.
- Los DTO del catálogo incluyen `activa`.
- Acciones de auditoría: `CREAR_CATALOGO`, `EDITAR_CATALOGO`, `ACTIVAR_CATALOGO`, `DESACTIVAR_CATALOGO` (entidad `Universidad`, `Facultad`, `ProgramaAcademico` o `Asignatura`).
- Seed idempotente al arrancar: «Universidad Demo TutoFast» → «Facultad de Ingeniería» → «Ingeniería de Sistemas» con `IS-101`, `IS-204`, `IS-210` e `IS-305`, y la comisión inicial del 10 %.

## 7. Comisión — `ComisionControlador`

| Método y ruta | Rol | Cuerpo | Respuesta |
|---|---|---|---|
| `GET /api/comision/vigente` | A | — | 200 `{"porcentaje":10.0,"vigenteDesde":"..."}` |
| `PUT /api/comision` | AD | `{"porcentaje":12.0}` (0–50) | 200 nuevo `ComisionDTO`; solo se aplica a confirmaciones futuras |
| `GET /api/comision/historial` | AD | — | 200 `[{"porcentaje","vigenteDesde","vigenteHasta","modificadoPor"}]` |

Notas (módulo 05): `GET /vigente` devuelve 404 mientras no exista ninguna comisión (el seed crea la del 10 %). `PUT` valida 0–50 (400), cierra la vigente y activa la nueva (respeta el índice parcial único `uk_comision_vigente`; si la activación falla reabre la anterior) y responde 409 si el porcentaje es igual al vigente o si otro administrador la cambió al mismo tiempo. Auditoría: `CONFIGURAR_COMISION` (entidad `ParametroComision`, detalle con porcentaje anterior y nuevo) y `SEED_COMISION`.

---

## 8. Solicitudes — `SolicitudControlador`

### `POST /api/solicitudes` · E (multipart)
- Parte `datos`:
```json
{
  "asignaturaId": "6741...104",
  "tema": "Ecuaciones diferenciales de primer orden y variables separables",
  "descripcion": "Requiero explicación detallada del método de separación de variables y resolución guiada de 3 ejercicios preparatorios para el taller",
  "tipoServicio": "RESOLUCION_GUIADA_EJERCICIOS",
  "fechaHora": "2026-12-05T19:00:00Z",
  "modalidad": "PRESENCIAL",
  "zonaEncuentro": "Biblioteca central, sala 3",
  "duracion": "MIN_60",
  "precioInicialCOP": 25000
}
```
- Parte(s) `archivos` (opcionales): hasta 5 archivos (PDF, PNG, JPG o DOCX de hasta 10 MB). Orden interno: validar → subir a GridFS → persistir; si persistir falla se borran los archivos subidos.
- Reglas de validación: cuenta ACTIVA con rol ESTUDIANTE (403 si no); asignatura, programa, facultad y universidad vigentes; `fechaHora` futura; precio > 0 (se guarda como `Decimal128`, 2 decimales). Las listas de frases genéricas y de fraude son configurables con `tutofast.solicitudes.frases-genericas` y `tutofast.solicitudes.palabras-fraude` (separadas por coma; vacías = valores por defecto del dominio).
- 201 `SolicitudDetalleDTO` con estado `PUBLICADA`.
- 422 `DESCRIPCION_INSUFICIENTE`: "La descripción debe tener al menos 20 caracteres y especificar el objetivo pedagógico del tema; no se admiten textos genéricos".
- 422 `FRAUDE_ACADEMICO`: "Solicitud rechazada: TutoFast prohíbe terminantemente la suplantación y la realización de evaluaciones calificables individuales ajenas (R06)". Además se registra en la auditoría (`FRAUDE_ACADEMICO_DETECTADO`).
- 400 si falta un campo, si `fechaHora` está en el pasado, si la duración no es 30/60/120, si la asignatura está inactiva o si la modalidad es presencial sin `zonaEncuentro`.

### `GET /api/solicitudes` · A
Filtros (R07): `universidadId`, `facultadId`, `programaId`, `asignaturaId`, `tema` (texto), `modalidad`, `fechaDesde`, `fechaHasta`, `precioMin`, `precioMax`, `orden` (`recientes` | `precioAsc` | `precioDesc` | `fecha`), paginación. Solo devuelve solicitudes PUBLICADA, PROPUESTAS_RECIBIDAS o NEGOCIACION_EN_CURSO. `orden` inválido → 400; `tamano` máximo 50 (por defecto 12). Si no hay resultados: 200 con `contenido: []`, y la interfaz muestra "No se encontraron solicitudes que coincidan con los filtros seleccionados".

`SolicitudResumenDTO`: `{"id","tema","asignaturaNombre","programaNombre","modalidad","duracion","precioInicialCOP","fechaHora","estado","totalPropuestas","estudianteNombre","estudianteConfiabilidad"}`.

| Método y ruta | Rol | Notas |
|---|---|---|
| `GET /api/solicitudes/mias` | E | todas las del estudiante, en cualquier estado |
| `GET /api/solicitudes/{id}` | A | `SolicitudDetalleDTO` (incluye `archivos[]` con `archivoId`, y `servicioId` si existe). Una solicitud fuera de los estados explorables solo la ve su dueño o un administrador (403) |
| `PUT /api/solicitudes/{id}` | E (dueño) | JSON `SolicitudEntradaDTO` (mismos campos que la parte `datos` al crear; se revalida la integridad); 409 "La solicitud no puede modificarse tras la confirmación bilateral" (R05) |
| `POST /api/solicitudes/{id}/cancelar` | E (dueño) | 200 → CANCELADA; propuestas activas → DESCARTADA (y se notifica a los tutores); 409 si ya está FORMALIZADA ("cualquier cancelación posterior a la confirmación debe canalizarse mediante el módulo de reportes y soporte") |
| `POST /api/solicitudes/{id}/archivos` | E (dueño) | multipart `archivos`; 201 `ArchivoDTO[]`; solo antes de confirmar; máximo 5 por solicitud (400) |

---

## 9. Propuestas, negociación y ciclo del servicio — `PropuestaControlador` y `NegociacionControlador`

### `POST /api/solicitudes/{id}/propuestas` · T
```json
{ "tipo": "CONTRAPROPUESTA_PRECIO", "precioCOP": 30000, "duracion": "MIN_60", "modalidad": "PRESENCIAL", "nota": "Incluye material de apoyo" }
```
- Con `ACEPTA_PRECIO`, el precio, la duración y la modalidad se copian de la solicitud (los que se envíen se ignoran). Con `CONTRAPROPUESTA_PRECIO` el precio es obligatorio y debe ser distinto del de la solicitud; la duración y la modalidad se mantienen. Con `ALTERNATIVA_CONDICIONES` puede cambiar la duración (30/45/60/90/120) o la modalidad (y, opcionalmente, el precio); debe cambiar al menos la duración o la modalidad.
- Monto: mayor que cero y como máximo 5.000.000 COP; si no, 400 `VALIDACION`. `nota`: hasta 500 caracteres.
- Efecto en la solicitud: la primera propuesta pasa la solicitud a PROPUESTAS_RECIBIDAS; una contrapropuesta o alternativa la pasa a NEGOCIACION_EN_CURSO. Se notifica al estudiante (`NUEVA_PROPUESTA`) y se audita (`PROPUESTA_ENVIADA`).
- 201 `PropuestaVistaDTO`. 409 `SOLICITUD_NO_ADMITE_PROPUESTAS` ("La solicitud ya ha sido acordada con otro tutor y no admite nuevas propuestas") si la solicitud está en TUTOR_SELECCIONADO, FORMALIZADA o CANCELADA. 409 `DUPLICADO` si el tutor ya tiene una propuesta activa (lo garantiza el índice parcial único `uk_propuesta_activa`; tras retirar o ser descartada puede volver a proponer). 403 si el tutor es el dueño de la solicitud, no tiene el rol TUTOR o su cuenta no está activa. 404 si la solicitud no existe.

`PropuestaVistaDTO`:
```json
{ "id": "6741...301", "solicitudId": "6741...201", "solicitudTema": "Derivadas de funciones compuestas", "solicitudEstado": "NEGOCIACION_EN_CURSO",
  "tutorId": "6741...501", "tutorNombre": "Laura Martínez", "tutorVerificado": true, "tutorPromedioCalificacion": 4.75, "tutorServiciosCompletados": 12,
  "tipo": "ALTERNATIVA_CONDICIONES", "precioCOP": 30000, "duracion": "MIN_90", "modalidad": "PRESENCIAL", "nota": "...",
  "contraofertaCOP": null, "estado": "ENVIADA", "fechaCreacion": "...", "fechaActualizacion": "..." }
```
`solicitudTema` y `solicitudEstado` se rellenan en `GET /api/solicitudes/{id}/propuestas` y en `GET /api/propuestas/mias`; `contraofertaCOP` solo tiene valor en CONTRAOFERTA_ESTUDIANTE. Estados: ENVIADA, CONTRAOFERTA_ESTUDIANTE, ACEPTADA (provisional), DESCARTADA, RETIRADA.

### `GET /api/solicitudes/{id}/propuestas` · E (dueño ve todas) · T (solo la suya) · AD (todas)
200 `PropuestaVistaDTO[]` (más recientes primero; la interfaz las ordena por precio). 403 para cualquier otro usuario; 404 si la solicitud no existe.

### `GET /api/propuestas/mias` · T
200 `PropuestaVistaDTO[]` con todas las propuestas del tutor (activas e históricas), con `solicitudTema` y `solicitudEstado`.

| Método y ruta | Rol | Cuerpo | Efecto |
|---|---|---|---|
| `PUT /api/propuestas/{id}` | T (autor) | igual que al crear | modifica la propuesta (también es la respuesta a una contraoferta); estado → ENVIADA y se borra la contraoferta; solo si la propuesta es ENVIADA o CONTRAOFERTA_ESTUDIANTE y la solicitud sigue abierta (409 si no). Se notifica al estudiante |
| `POST /api/propuestas/{id}/retirar` | T (autor) | — | RETIRADA, `activa=false`; 409 si ya estaba ACEPTADA, DESCARTADA o RETIRADA |
| `POST /api/propuestas/{id}/contraofertar` | E (dueño) | `{"precioCOP":27000,"nota":"¿Podría ser 27.000?"}` | estado CONTRAOFERTA_ESTUDIANTE y `contraofertaCOP`; el precio anterior queda en la auditoría (`PROPUESTA_CONTRAOFERTADA`); se notifica al tutor (`CONTRAOFERTA`, con la nota), que responde con `PUT`. 400 si el precio es ≤ 0 o > 5.000.000; 422 si es igual al precio propuesto; 409 si la propuesta no está ENVIADA o la solicitud ya no está abierta |
| `POST /api/propuestas/{id}/seleccionar` | E (dueño) | — | propuesta ACEPTADA (provisional); solicitud TUTOR_SELECCIONADO; se notifica al tutor (`PROPUESTA_ACEPTADA`). 409 `CONTRAOFERTA_PENDIENTE` si la propuesta tiene una contraoferta sin responder; 409 si la solicitud ya tiene tutor seleccionado o no está abierta; 409 `TUTOR_NO_DISPONIBLE` si la cuenta del tutor dejó de estar activa |
| `POST /api/propuestas/{id}/rechazar` | E (dueño) | opcional `{"motivo":"..."}` (hasta 300 caracteres) | DESCARTADA (`activa=false`); se notifica al tutor (`PROPUESTA_DESCARTADA`, con el motivo). 409 si ya estaba ACEPTADA, DESCARTADA o RETIRADA |
| `POST /api/solicitudes/{id}/confirmar` | E (dueño) | `{"tokenPagoSimulado":"tok_aprobar"}` | ver abajo |

`ServicioPropuestas` también ofrece `descartarPropuesta` y `descartarPropuestasDeSolicitud` (uso interno): la confirmación del servicio las usa para descartar las demás propuestas; la cancelación de la solicitud ya descarta las activas.

### `POST /api/solicitudes/{id}/confirmar` · E (dueño)
Cuerpo: `{"tokenPagoSimulado":"tok_aprobar"}` — token **opaco** del simulador; obligatorio (400 `VALIDACION` si falta o está en blanco; máximo 64 caracteres). La plataforma nunca recibe, registra ni guarda datos de tarjeta: ni el token ni la respuesta ni la auditoría lo conservan. Con `tok_rechazar` (o cualquier token vacío) el simulador rechaza el pago.

Una sola operación (`@Transactional` con Atlas; con el gestor no-op compensa a mano): verifica dueño y estado (TUTOR_SELECCIONADO con una propuesta ACEPTADA y el tutor activo) → lee la comisión vigente (`ServicioCatalogo.consultarComision`) → **reserva** el `ServicioTutoria` (CONFIRMADO, con precio, duración, modalidad y `comisionPct` congelados; el índice único `uk_servicio_solicitud` hace de cerrojo) → `IPasarelaPago.procesarPago` → crea el `Pago` (RETENIDO, con comisión y neto calculados con `BigDecimal`, HALF_UP a pesos enteros, guardados como `Decimal128`) → solicitud FORMALIZADA → descarta las demás propuestas (`PROPUESTA_DESCARTADA`) → auditoría y notificaciones. Si el pago se rechaza o algo falla, se elimina la reserva y la solicitud queda en TUTOR_SELECCIONADO, sin pago ni servicio. *(El cierre de los hilos de chat de los tutores no elegidos llega con el módulo 08.)*

200 `ServicioDTO`:
```json
{ "id": "6741...401", "solicitudId": "6741...201", "propuestaId": "6741...301", "tema": "Ecuaciones diferenciales",
  "estudiante": {"id","nombre"}, "tutor": {"id","nombre"},
  "precioFinalCOP": 30000, "duracion": "MIN_90", "modalidad": "PRESENCIAL", "lugarOEnlace": "Biblioteca central, sala 3",
  "fechaHora": "2026-12-05T19:00:00Z", "comisionPct": 10.0, "fechaConfirmacion": "...", "fechaInicio": null, "fechaFinalizacion": null,
  "estado": "CONFIRMADO",
  "pago": { "estado": "RETENIDO", "montoRetenidoCOP": 30000, "comisionCOP": 3000, "montoNetoCOP": 27000, "referencia": "SIM-..." } }
```
`pago` es `null` si el servicio no tiene pago registrado. `lugarOEnlace` parte de la zona de encuentro de la solicitud si es presencial.

Errores:
| Estado | Código | Cuándo |
|---|---|---|
| 401 / 403 | — | sin sesión; rol distinto de ESTUDIANTE; no es el dueño de la solicitud; cuenta suspendida |
| 404 | — | la solicitud no existe |
| 409 | `SERVICIO_YA_CONFIRMADO` | la solicitud ya estaba FORMALIZADA o dos confirmaciones concurrentes chocaron con el índice único: **no se cobra de nuevo** (idempotencia) |
| 409 | `SOLICITUD_NO_CONFIRMABLE` | la solicitud no está en TUTOR_SELECCIONADO (publicada, en negociación o cancelada) |
| 409 | `PROPUESTA_NO_SELECCIONADA` | no hay una propuesta ACEPTADA |
| 409 | `TUTOR_NO_DISPONIBLE` | la cuenta del tutor dejó de estar activa |
| 422 | `PAGO_RECHAZADO` | el simulador rechazó el pago; no se guarda nada y se puede reintentar con otro token |

Auditoría (sin el token): `SERVICIO_CONFIRMADO` (entidad `servicio:{id}`; solicitud, tutor, precio, comisión y propuestas descartadas) y `PAGO_RETENIDO` (entidad `pago:{id}`; monto, comisión, neto y referencia). Notificaciones `SERVICIO_CONFIRMADO` al estudiante y al tutor, con enlace `negociacion.html?id={servicioId}`. Un fallo al notificar no revierte la confirmación.

### Servicios
| Método y ruta | Rol | Cuerpo | Efecto / respuesta |
|---|---|---|---|
| `GET /api/servicios/mios?estado=` | E, T | — | 200 `ServicioDTO[]` (como estudiante o como tutor, recientes primero); `estado` opcional (CONFIRMADO, EN_DESARROLLO, FINALIZADO, EN_REVISION, CANCELADO; otro valor → 400) |
| `GET /api/servicios/{id}` | Part., AD | — | 200 `ServicioDTO`; 403 para cualquier otro usuario; 404 si no existe |
| `PATCH /api/servicios/{id}/enlace` | T (del servicio) | `{"lugarOEnlace":"https://meet.example/abc"}` (obligatorio, hasta 300 caracteres) | solo en CONFIRMADO (409 `SERVICIO_NO_EDITABLE` si no); si el cuerpo trae `precioFinalCOP`, `duracion` o `modalidad` → 409 `SERVICIO_INMUTABLE` (inmutables tras la confirmación); 403 si no es el tutor del servicio. Notifica al estudiante y audita `SERVICIO_ENLACE_ACTUALIZADO` |
| `POST /api/servicios/{id}/iniciar` | Part. | — | CONFIRMADO → EN_DESARROLLO; 409 `TRANSICION_INVALIDA` si no estaba CONFIRMADO; 403 a quien no participa; audita `SERVICIO_INICIADO`. *(El inicio automático por hora llega con la tarea programada del módulo 09.)* |
| `POST /api/servicios/{id}/finalizar` *(módulo 09)* | E (del servicio) | — | EN_DESARROLLO → FINALIZADO; liquidación y liberación; 200 `LiquidacionDTO`; 409 "El servicio ya se encuentra finalizado y no requiere nuevas acciones"; 409 si está EN_REVISION |

---

## 10. Pagos — `PagoControlador`

`LiquidacionDTO`:
```json
{ "servicioId": "6741...401", "precioFinalCOP": 40000, "comisionPct": 10.0, "comisionCOP": 4000,
  "montoNetoCOP": 36000, "estado": "LIBERADO", "referenciaPasarela": "SIM-...",
  "fechaRetencion": "...", "fechaLiquidacion": "..." }
```

| Método y ruta | Rol | Respuesta |
|---|---|---|
| `GET /api/pagos/servicio/{servicioId}` | Part., AD | 200 `LiquidacionDTO` (con el servicio RETENIDO muestra la comisión y el neto que se liquidarán); 403 a otros usuarios; 404 si el servicio o el pago no existen |
| `GET /api/pagos/mios` | E, T | 200 `LiquidacionDTO[]` (egresos del estudiante o ingresos netos del tutor; recientes primero) |
| `POST /api/pagos/simular-cierre-automatico?ignorarPlazo=false` *(módulo 09)* | **AD únicamente** | 200 `CierreAutomaticoDTO` |

### `POST /api/pagos/simular-cierre-automatico` · AD (demo)
Ejecuta al momento la misma lógica que la tarea programada (`ServicioNegociacion.cerrarAutomaticamente`): busca servicios EN_DESARROLLO cuyo fin programado (`fechaHora + duracion`) más 24 h ya pasó y que no tienen reporte abierto; los finaliza y libera el pago. Con `ignorarPlazo=true` (solo para la demo) omite la espera de 24 h. Queda registrado en la auditoría como `CIERRE_AUTOMATICO_MANUAL`.
```json
{ "revisados": 5, "cerrados": 3, "omitidosPorReporte": 1, "omitidosPorPlazo": 1, "serviciosCerrados": ["6741...401", "..."] }
```
Responde 403 a cualquier rol distinto de ADMINISTRADOR.

---

## 11. Mensajería — `MensajeControlador`

Hilo = `(solicitudId, tutorId)`. Participantes: el estudiante dueño y ese tutor (que debe tener o haber tenido una propuesta en la solicitud). El administrador solo puede leer. Tras la confirmación, los hilos de los tutores no elegidos quedan en solo lectura.

| Método y ruta | Rol | Parámetros / cuerpo | Respuesta |
|---|---|---|---|
| `GET /api/solicitudes/{id}/mensajes` | Part., AD | `tutorId` (obligatorio para el estudiante y el administrador), `desde` (ISO, exclusivo, opcional) | 200 `{"soloLectura":false,"mensajes":[MensajeDTO]}` ordenados ascendente |
| `GET /api/solicitudes/{id}/hilos` | E (dueño), AD | — | 200 `[{"tutorId","tutorNombre","ultimoMensaje","noLeidos"}]` |
| `POST /api/solicitudes/{id}/mensajes` | Part. | multipart `datos={"tutorId","contenido"}`, `archivo?` | 201 `MensajeDTO`; 403 si el hilo es de solo lectura; 415 si el tipo de archivo no está permitido ("Tipo de archivo no permitido. Solo se admiten formatos seguros (PDF, PNG, JPG, DOCX)") |
| `POST /api/mensajes/{id}/reportar` | Part. (receptor) | `{"motivo":"CONDUCTA_ABUSIVA","detalle":"..."}` | 200; `reportado=true`; se notifica al administrador; el mensaje sigue siendo inmutable |

`MensajeDTO`: `{"id","emisorId","emisorNombre","contenido","marcaTemporal","reportado","archivo":{"archivoId","nombre","tipo","tamanoMB"}|null}`.

**Contrato de polling:** el cliente llama cada 4 s con `desde=<marcaTemporal del último mensaje recibido>`. El servidor devuelve solo los mensajes más nuevos (como máximo 100). Para el contador de no leídos no hay endpoint aparte: se usa `/hilos`.

---

## 12. Archivos — `ArchivoControlador`

### `GET /api/archivos/{id}` · A (con control de acceso por contexto)
200 binario con `Content-Type` original y `Content-Disposition: attachment; filename="..."`. 403 si el usuario no tiene acceso al contexto; 404 si no existe. El frontend lo descarga con `fetch` (cabecera Bearer) y luego `URL.createObjectURL(blob)` (`descargarArchivo` en `js/servicios/api.js`). Responde además `X-Content-Type-Options: nosniff` y el tipo MIME se fija según el formato validado. Acceso: un archivo solo se entrega si algún contexto lo referencia (hoy: soportes de verificación → su dueño o un ADMINISTRADOR); un archivo sin referencia da 404.

---

## 13. Reputación — `CalificacionControlador`

### `POST /api/servicios/{id}/calificaciones` · E o T (participante)
Estudiante → CalificacionAlTutor:
```json
{ "claridadPedagogica": 5, "dominioTematico": 5, "puntualidad": 5, "cumplimiento": 5, "trato": 5, "relacionCalidadPrecio": 4,
  "comentario": "Excelente explicación, muy paciente y claro con las ecuaciones" }
```
Tutor → CalificacionAlEstudiante:
```json
{ "puntualidad": 5, "comunicacion": 5, "cumplimiento": 4, "comentario": "Estudiante puntual y preparado con los ejercicios" }
```
- El tipo de calificación lo decide el servidor según quién es el autor en el servicio.
- 201 `CalificacionDTO`; se recalcula `promedioCalificacion` del tutor o `indicadorConfiabilidad` del estudiante.
- 422 "Las calificaciones y reseñas están restringidas exclusivamente a servicios registrados y finalizados en la plataforma (R13)" si el servicio no está FINALIZADO.
- 422 si se venció el plazo de 14 días. 409 `DUPLICADO` si esa parte ya calificó.

### `GET /api/servicios/{id}/calificaciones` · Part.
200 `{"mia":CalificacionDTO|null,"contraparte":CalificacionDTO|null,"fechaLimite":"...","puedeCalificar":true}`. La calificación de la contraparte solo se muestra cuando ya es visible.

Las reseñas públicas se consultan en `GET /api/usuarios/{id}/resenas` (sección 4).

---

## 14. Reportes, sanciones y apelaciones — `ReporteControlador`

### `POST /api/reportes` · E, T (multipart)
- `datos`:
```json
{ "tipo": "INASISTENCIA", "motivo": "El tutor no se presentó tras 20 minutos de tolerancia", "denunciadoId": "6741...501", "servicioId": "6741...401" }
```
- `evidencias`: 0 a 5 archivos.
- Si viene `servicioId` y el servicio está CONFIRMADO o EN_DESARROLLO (o FINALIZADO hace menos de 24 h con el pago aún sin liberar): servicio → EN_REVISION y pago → CONGELADO. Se notifica al denunciado y a los administradores.
- 201 `ReporteDTO`. 403 si el usuario se reporta a sí mismo o no participa en el servicio.

| Método y ruta | Rol | Respuesta |
|---|---|---|
| `GET /api/reportes/mios` | E, T | 200 `{"radicados":[ReporteDTO],"recibidos":[ReporteDTO]}` |
| `GET /api/sanciones/mias` | A | 200 `SancionDTO[]` |
| `POST /api/sanciones/{id}/apelaciones` | A (sancionado) | multipart `datos={"descargo":"..."}`, `evidencias[]` → 201 `ApelacionDTO`; sanción → EN_APELACION; 409 si ya hay una apelación pendiente o la sanción no está VIGENTE |

`ReporteDTO`: `{"id","tipo","categoria":"INCUMPLIMIENTO_SERVICIO","motivo","estado","reportante":{id,nombre},"denunciado":{id,nombre},"servicioId","evidencias":[archivoId],"fechaRadicacion","resolucion"}`.

`SancionDTO`: `{"id","reporteId","tipo","vigencia":{"inicio","fin"},"estado","apelaciones":[{"id","descargo","evidencias","resultado","fechaPresentacion","fechaResolucion"}]}`.

---

## 15. Notificaciones — `NotificacionControlador`

| Método y ruta | Rol | Respuesta |
|---|---|---|
| `GET /api/notificaciones?soloNoLeidas=false` + paginación | A | 200 `Pagina<NotificacionDTO>` |
| `GET /api/notificaciones/conteo-no-leidas` | A | 200 `{"noLeidas":3}` (la campana lo consulta cada 30 s) |
| `PATCH /api/notificaciones/{id}/leida` | A (destinatario) | 204; 404 si no existe o es de otro usuario |
| `PATCH /api/notificaciones/leer-todas` | A | 204 |

Parámetros de `GET /api/notificaciones`: `soloNoLeidas` (false), `pagina` (0), `tamano` (20, máximo 100).

`NotificacionDTO`: `{"id","tipo","titulo","mensaje","enlace","leida","fecha"}`. Tipos: `NUEVA_PROPUESTA`, `CONTRAOFERTA`, `PROPUESTA_ACEPTADA`, `PROPUESTA_DESCARTADA`, `SERVICIO_CONFIRMADO`, `NUEVO_MENSAJE`, `RECORDATORIO_SESION`, `SERVICIO_FINALIZADO`, `PAGO_LIBERADO`, `CALIFICACION_PENDIENTE`, `REPORTE_RECIBIDO`, `REPORTE_RESUELTO`, `SANCION_APLICADA`, `APELACION_RESUELTA`, `SOPORTE_APROBADO`, `SOPORTE_RECHAZADO`.

---

## 16. Administración y auditoría — `AdministracionControlador` (todo AD)

### `GET /api/admin/soportes?estado=EN_REVISION`
Cola de verificación ordenada por `prioridad` (ALTA primero) y luego por `fechaEnvio` ascendente (ajuste aprobado de HU-01):
```json
[{ "usuarioId": "6741...501", "nombre": "Laura Martínez", "correo": "tutor@universidad.edu.co",
   "prioridad": "ALTA", "motivoPrioridad": "Correo institucional .edu.co",
   "soporte": { "id": "c0f1...", "tipo": "CERTIFICADO_MONITORIA", "archivoId": "6741...901", "fechaEnvio": "..." } }]
```
`prioridad` es `"ALTA"` si el correo termina en `.edu.co` y `"NORMAL"` en otro caso. El panel la muestra como insignia.

| Método y ruta | Cuerpo | Efecto |
|---|---|---|
| `POST /api/admin/usuarios/{usuarioId}/soportes/{soporteId}/aprobar` | `{"observacion":"Certificado válido"}` | soporte APROBADO; `nivelVerificacion=VERIFICADO` ("Tutor Verificado"); notificación |
| `POST /api/admin/usuarios/{usuarioId}/soportes/{soporteId}/rechazar` | `{"observacion":"Documento ilegible"}` (obligatoria) | soporte RECHAZADO; nivel NO_VERIFICADO si no quedan soportes aprobados ni en revisión |
| `GET /api/admin/usuarios?rol=&estado=&q=` | — | 200 `Pagina<UsuarioAdminDTO>` |
| `PATCH /api/admin/usuarios/{id}/estado` | `{"estadoCuenta":"SUSPENDIDA","motivo":"..."}` | 200 |
| `GET /api/admin/reportes?estado=` | — | 200 `Pagina<ReporteDTO>` |
| `GET /api/admin/reportes/{id}` | — | 200 `{"reporte":ReporteDTO,"servicio":ServicioDTO,"pago":LiquidacionDTO,"historialChat":[MensajeDTO]}` |
| `POST /api/admin/reportes/{id}/resolver` | `ResolucionDTO` | ver abajo |
| `POST /api/admin/sanciones/{id}/apelaciones/{apelacionId}/resolver` | `{"resultado":"REVOCADA","observacion":"..."}` | sanción → RATIFICADA o REVOCADA; si se revoca, se restablece `estadoCuenta` |
| `GET /api/admin/mensajes-reportados` | — | 200 `[{"mensaje":MensajeDTO,"solicitudId","tutorId","motivoReporte"}]` |
| `GET /api/admin/auditoria?usuarioId=&entidad=&desde=&hasta=` | — | 200 `Pagina<RegistroAuditoriaDTO>` |

`ResolucionDTO`:
```json
{ "decision": "A_FAVOR_REPORTANTE", "accionPago": "REEMBOLSAR", "observacion": "Inasistencia comprobada en el chat",
  "sancion": { "tipo": "SUSPENSION_TEMPORAL", "inicio": "2026-12-07T00:00:00Z", "fin": "2026-12-21T00:00:00Z" } }
```
- `decision`: A_FAVOR_REPORTANTE · A_FAVOR_DENUNCIADO · DESESTIMADO. `accionPago`: REEMBOLSAR (servicio CANCELADO) · LIBERAR (servicio FINALIZADO con liquidación) · NINGUNA. `sancion` es opcional.
- Una SUSPENSION_TEMPORAL pone la cuenta en SUSPENDIDA durante la vigencia; un BLOQUEO_PERMANENTE la pone en BLOQUEADA.
- 200 `ReporteDTO` con `resolucion`.

`RegistroAuditoriaDTO`: `{"id","usuarioId","usuarioNombre","accion","entidadAfectada","fecha","detalle"}`.

**Notas de implementación (módulo 05, endpoints ya disponibles)**
- `GET /api/admin/soportes?estado=` (por defecto `EN_REVISION`): un elemento por soporte. `motivoPrioridad` es `null` cuando la prioridad es `NORMAL`; el orden es prioridad ALTA primero y luego `fechaEnvio` ascendente.
- `.../aprobar`: cuerpo opcional `{"observacion"}` (≤ 500). `.../rechazar`: `{"observacion"}` obligatoria (400 si falta). Ambos responden 200 con el `SoporteDTO` revisado; un soporte ya revisado → 409 `SOPORTE_YA_REVISADO`; usuario sin rol TUTOR o soporte inexistente → 404.
- Nivel resultante: `VERIFICADO` si el tutor tiene algún soporte aprobado; si no, `EN_REVISION` mientras le quede alguno pendiente y `NO_VERIFICADO` en otro caso. Notificaciones `SOPORTE_APROBADO` / `SOPORTE_RECHAZADO` (con el motivo) y auditoría `APROBAR_SOPORTE` / `RECHAZAR_SOPORTE`.
- `GET /api/admin/usuarios?rol=&estado=&q=&pagina=&tamano=`: `Pagina<UsuarioAdminDTO>` con `{"id","nombre","correo","roles","estadoCuenta","fechaRegistro","nivelVerificacion"}` (`q` busca en nombre y correo; más recientes primero; tamaño máximo 100).
- `PATCH /api/admin/usuarios/{id}/estado`: `motivo` obligatorio para `SUSPENDIDA` y `BLOQUEADA`; no se puede actuar sobre la propia cuenta ni sobre otro administrador (422). Auditoría `CAMBIAR_ESTADO_CUENTA`. Una cuenta no activa deja de poder usar el token en la siguiente petición (403).
- `GET /api/admin/auditoria`: `desde` y `hasta` son instantes ISO-8601 (`2026-12-01T00:00:00Z`); `usuarioNombre` es «Sistema» cuando `usuarioId` es nulo. Los endpoints de reportes, sanciones y mensajes reportados de esta tabla llegan en módulos posteriores.

---

## 17. Matriz resumen de endpoints

| # | Método | Ruta | Rol |
|---|---|---|---|
| 1 | GET | /api/salud | P |
| 2 | POST | /api/auth/registro | P |
| 3 | POST | /api/auth/iniciar-sesion | P |
| 4 | GET | /api/auth/yo | A |
| 5 | GET | /api/usuarios/yo | A |
| 6 | PUT | /api/usuarios/yo/perfil-estudiante | E |
| 7 | PUT | /api/usuarios/yo/perfil-tutor | T |
| 8 | POST | /api/usuarios/yo/roles | A |
| 9 | POST | /api/usuarios/yo/soportes | T |
| 10 | GET | /api/usuarios/yo/soportes | T |
| 11 | GET | /api/usuarios/{id}/resenas | A |
| 12 | GET | /api/tutores | A |
| 13 | GET | /api/tutores/{id} | A |
| 14–18 | GET | /api/catalogos/... | P |
| 19–22 | POST | /api/catalogos/{universidades,facultades,programas,asignaturas} | AD |
| 23 | PUT | /api/catalogos/{tipo}/{id} | AD |
| 24 | PATCH | /api/catalogos/{tipo}/{id}/activa | AD |
| 25 | GET | /api/comision/vigente | A |
| 26 | PUT | /api/comision | AD |
| 27 | GET | /api/comision/historial | AD |
| 28 | POST | /api/solicitudes | E |
| 29 | GET | /api/solicitudes | A |
| 30 | GET | /api/solicitudes/mias | E |
| 31 | GET | /api/solicitudes/{id} | A |
| 32 | PUT | /api/solicitudes/{id} | E |
| 33 | POST | /api/solicitudes/{id}/cancelar | E |
| 34 | POST | /api/solicitudes/{id}/archivos | E |
| 35 | POST | /api/solicitudes/{id}/propuestas | T |
| 36 | GET | /api/solicitudes/{id}/propuestas | E, T, AD |
| 37 | PUT | /api/propuestas/{id} | T |
| 38 | POST | /api/propuestas/{id}/retirar | T |
| 39 | POST | /api/propuestas/{id}/contraofertar | E |
| 40 | POST | /api/propuestas/{id}/seleccionar | E |
| 41 | POST | /api/propuestas/{id}/rechazar | E |
| 42 | GET | /api/propuestas/mias | T |
| 43 | POST | /api/solicitudes/{id}/confirmar | E |
| 44 | GET | /api/servicios/mios | E, T |
| 45 | GET | /api/servicios/{id} | Part., AD |
| 46 | PATCH | /api/servicios/{id}/enlace | T |
| 47 | POST | /api/servicios/{id}/iniciar | Part. |
| 48 | POST | /api/servicios/{id}/finalizar | E |
| 49 | GET | /api/pagos/servicio/{servicioId} | Part., AD |
| 50 | GET | /api/pagos/mios | E, T |
| 51 | POST | /api/pagos/simular-cierre-automatico | AD |
| 52 | GET | /api/solicitudes/{id}/mensajes | Part., AD |
| 53 | GET | /api/solicitudes/{id}/hilos | E, AD |
| 54 | POST | /api/solicitudes/{id}/mensajes | Part. |
| 55 | POST | /api/mensajes/{id}/reportar | Part. |
| 56 | GET | /api/archivos/{id} | A (acceso por contexto) |
| 57 | POST | /api/servicios/{id}/calificaciones | E, T |
| 58 | GET | /api/servicios/{id}/calificaciones | Part. |
| 59 | POST | /api/reportes | E, T |
| 60 | GET | /api/reportes/mios | E, T |
| 61 | GET | /api/sanciones/mias | A |
| 62 | POST | /api/sanciones/{id}/apelaciones | A |
| 63 | GET | /api/notificaciones | A |
| 64 | GET | /api/notificaciones/conteo-no-leidas | A |
| 65 | PATCH | /api/notificaciones/{id}/leida | A |
| 66 | PATCH | /api/notificaciones/leer-todas | A |
| 67 | GET | /api/admin/soportes | AD |
| 68 | POST | /api/admin/usuarios/{u}/soportes/{s}/aprobar | AD |
| 69 | POST | /api/admin/usuarios/{u}/soportes/{s}/rechazar | AD |
| 70 | GET | /api/admin/usuarios | AD |
| 71 | PATCH | /api/admin/usuarios/{id}/estado | AD |
| 72 | GET | /api/admin/reportes | AD |
| 73 | GET | /api/admin/reportes/{id} | AD |
| 74 | POST | /api/admin/reportes/{id}/resolver | AD |
| 75 | POST | /api/admin/sanciones/{id}/apelaciones/{a}/resolver | AD |
| 76 | GET | /api/admin/mensajes-reportados | AD |
| 77 | GET | /api/admin/auditoria | AD |
