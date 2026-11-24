# Diseño de base de datos — MongoDB Atlas (`tutofast`)

> Fuente: diagrama de clases (`docs/diagramas/DiagramaClases_UniDrive_v2.png`). Las 22 clases se conservan con sus atributos. Los campos marcados **(+)** son técnicos: se derivan de asociaciones del diagrama o de las historias de usuario.

## 1. Convenciones
- **Identificadores:** el diagrama usa `Integer`; en MongoDB son `ObjectId` y en Java `String id`. Las referencias se llaman `<entidad>Id`.
- **Dinero (COP):** `BigDecimal` en Java, `Decimal128` en MongoDB (`NumberDecimal`) mediante los conversores de `config/MongoConfig`. Los montos se redondean a pesos enteros con `HALF_UP`.
- **Fechas:** `Instant` / `LocalDate` en Java, `ISODate` (UTC) en MongoDB. La interfaz las muestra en `America/Bogota`.
- **Enums:** se guardan como texto (`"PUBLICADA"`).
- **Índices:** se declaran con `@Indexed` / `@CompoundIndex` y `spring.data.mongodb.auto-index-creation=true`. Los índices parciales se crean en `DatosInicialesSeed` con `MongoTemplate.indexOps()`.
- **Estrategia:** se embebe cuando la parte es una composición pequeña y acotada; se referencia cuando hay crecimiento ilimitado, escrituras concurrentes o la entidad tiene vida propia.

## 2. Resumen de colecciones

| Colección | Clases del diagrama | Tipo |
|---|---|---|
| `usuarios` | Usuario, PerfilEstudiante*, PerfilTutor*, SoporteVerificacion* | raíz + embebidos |
| `universidades` | Universidad | catálogo |
| `facultades` | Facultad | catálogo |
| `programas_academicos` | ProgramaAcademico | catálogo |
| `asignaturas` | Asignatura | catálogo |
| `parametros_comision` | ParametroComision | histórico |
| `solicitudes_tutoria` | SolicitudTutoria, Archivo* | raíz + embebidos |
| `propuestas` | Propuesta | referenciada |
| `servicios_tutoria` | ServicioTutoria | referenciada |
| `pagos` | Pago | referenciada |
| `mensajes` | Mensaje, Archivo* | referenciada + embebido |
| `calificaciones` | Calificacion, CalificacionAlTutor, CalificacionAlEstudiante | herencia (discriminador) |
| `reportes` | Reporte | referenciada |
| `sanciones` | Sancion, Apelacion* | raíz + embebidos |
| `registros_auditoria` | RegistroAuditoria | solo inserción |
| `notificaciones` | — (infraestructura de `INotificador`) | solo adaptador |
| `archivos.files` / `archivos.chunks` | binarios de Archivo, SoporteVerificacion, Reporte y Apelacion | GridFS |

\* = embebido.

Total: 22 clases → 15 colecciones de dominio + `notificaciones` + GridFS.

---

## 3. Colecciones de identidad

### 3.1 `usuarios` — Usuario (+ PerfilEstudiante, PerfilTutor, SoporteVerificacion)

| Campo | Tipo | Origen |
|---|---|---|
| `_id` | ObjectId | Usuario.id |
| `correo` | string (minúsculas) | Usuario.correo |
| `nombre` | string | Usuario.nombre |
| `roles` | array<Rol> (1..*) | Usuario.roles |
| `estadoCuenta` | ACTIVA · SUSPENDIDA · BLOQUEADA | Usuario.estadoCuenta |
| `fechaAutorizacionDatos` | date | Usuario.fechaAutorizacionDatos (Ley 1581) |
| `documento` | string | (+) HU-01 |
| `claveHash` | string (BCrypt) | (+) autenticación |
| `fechaRegistro` | date | (+) |
| `perfilEstudiante` | objeto \| null | composición 0..1 |
| `perfilTutor` | objeto \| null | composición 0..1 |

`perfilEstudiante`: `semestre` (int), `indicadorConfiabilidad` (decimal 0–5), (+)`programaId` («estudia en»), (+)`calificacionesRecibidas` (int).

`perfilTutor`: `presentacion`, `areasDeInteres[]`, `formacion`, `tipoTutor` (PAR · MONITOR_CERTIFICADO), `experiencia`, `modalidades[]` (VIRTUAL · PRESENCIAL), `disponibilidad`, `nivelVerificacion` (NO_VERIFICADO · EN_REVISION · VERIFICADO), `promedioCalificacion`, `serviciosCompletados`, `cuentaBancariaCobro`, (+)`asignaturasDominadasIds[]` («domina»), `soportes[]`.

`soportes[]` (SoporteVerificacion): (+)`id`, `tipo` (CERTIFICADO_MONITORIA · HISTORIAL_CALIFICACIONES · CARNE_ESTUDIANTIL · OTRO), `rutaArchivo` (id de GridFS), `estado` (EN_REVISION · APROBADO · RECHAZADO), `fechaRevision`, (+)`fechaEnvio`, (+)`observacion`, (+)`revisadoPor`.

Lo **declarado** (presentación, áreas, formación…) y lo **verificado** (`nivelVerificacion`, soportes aprobados) se separan en la API pública (R02).

**Prioridad de verificación (HU-01, ajuste aprobado):** no se guarda. Al listar la cola `GET /api/admin/soportes`, `ServicioIdentidad` calcula `prioridad = "ALTA"` si `correo` termina en `.edu.co` (y `"NORMAL"` en otro caso), ordena por `prioridad` y luego por `fechaEnvio` ascendente, y el panel muestra la insignia **"Correo institucional · Prioridad alta"**. Un correo personal no se bloquea.

```json
{
  "_id": { "$oid": "6741a0c1e4b0a1b2c3d4e501" },
  "correo": "tutor@universidad.edu.co",
  "nombre": "Laura Martínez",
  "documento": "1012345678",
  "claveHash": "$2a$10$Qw8...",
  "roles": ["TUTOR", "ESTUDIANTE"],
  "estadoCuenta": "ACTIVA",
  "fechaAutorizacionDatos": { "$date": "2026-11-01T14:02:11Z" },
  "fechaRegistro": { "$date": "2026-11-01T14:02:11Z" },
  "perfilEstudiante": {
    "semestre": 7,
    "indicadorConfiabilidad": { "$numberDecimal": "4.80" },
    "programaId": { "$oid": "6741a0c1e4b0a1b2c3d4e103" },
    "calificacionesRecibidas": 3
  },
  "perfilTutor": {
    "presentacion": "Monitora de cálculo y ecuaciones diferenciales desde 2024.",
    "areasDeInteres": ["Ecuaciones diferenciales", "Cálculo integral"],
    "formacion": "Ingeniería de Sistemas (8.º semestre)",
    "tipoTutor": "MONITOR_CERTIFICADO",
    "experiencia": "4 semestres como monitora académica",
    "modalidades": ["VIRTUAL", "PRESENCIAL"],
    "disponibilidad": "Lunes a jueves 14:00-18:00",
    "nivelVerificacion": "VERIFICADO",
    "promedioCalificacion": { "$numberDecimal": "4.75" },
    "serviciosCompletados": 12,
    "cuentaBancariaCobro": "Bancolombia ahorros ****4821",
    "asignaturasDominadasIds": [{ "$oid": "6741a0c1e4b0a1b2c3d4e104" }],
    "soportes": [{
      "id": "c0f1b6e2-3a7d-4c39-9d0e-1f2a3b4c5d6e",
      "tipo": "CERTIFICADO_MONITORIA",
      "rutaArchivo": { "$oid": "6741a0c1e4b0a1b2c3d4e901" },
      "estado": "APROBADO",
      "fechaEnvio": { "$date": "2026-11-01T14:05:00Z" },
      "fechaRevision": { "$date": "2026-11-02T09:10:00Z" },
      "observacion": "Certificado válido",
      "revisadoPor": { "$oid": "6741a0c1e4b0a1b2c3d4e500" }
    }]
  }
}
```

| Índice | Definición | Propósito |
|---|---|---|
| `uk_correo` | `{correo: 1}` único | inicio de sesión, no duplicar cuentas |
| `uk_documento` | `{documento: 1}` único | HU-01 |
| `ix_roles` | `{roles: 1}` | listados de administración |
| `ix_tutor_busqueda` | `{"perfilTutor.nivelVerificacion": 1, "perfilTutor.promedioCalificacion": -1}` | R07 / HU-04 |
| `ix_tutor_asignaturas` | `{"perfilTutor.asignaturasDominadasIds": 1}` | búsqueda de tutores por asignatura |
| `ix_soportes_estado` | `{"perfilTutor.soportes.estado": 1}` | cola de verificación |
| `ix_est_programa` | `{"perfilEstudiante.programaId": 1}` | — |

---

## 4. Catálogo académico (agregaciones)

### 4.1 `universidades`
```json
{ "_id": { "$oid": "6741a0c1e4b0a1b2c3d4e101" }, "nombre": "Universidad Nacional", "activa": true }
```
Índice: `{nombre: 1}` único.

### 4.2 `facultades`
```json
{ "_id": { "$oid": "6741a0c1e4b0a1b2c3d4e102" }, "universidadId": { "$oid": "6741a0c1e4b0a1b2c3d4e101" }, "nombre": "Ingeniería", "activa": true }
```
Índice: `{universidadId: 1, nombre: 1}` único.

### 4.3 `programas_academicos`
```json
{ "_id": { "$oid": "6741a0c1e4b0a1b2c3d4e103" }, "facultadId": { "$oid": "6741a0c1e4b0a1b2c3d4e102" }, "nombre": "Ingeniería de Sistemas", "activo": true }
```
Índice: `{facultadId: 1, nombre: 1}` único.

### 4.4 `asignaturas`
```json
{ "_id": { "$oid": "6741a0c1e4b0a1b2c3d4e104" }, "programaId": { "$oid": "6741a0c1e4b0a1b2c3d4e103" }, "codigo": "IS-204", "nombre": "Ecuaciones Diferenciales", "activa": true }
```
Índices: `{programaId: 1, codigo: 1}` **único** (HU-02: "La asignatura con este código ya se encuentra registrada en el programa") y `{programaId: 1, activa: 1}`.

Desactivar un elemento del catálogo es un borrado **lógico** (`activa=false`): no afecta a las solicitudes ya creadas.

### 4.5 `parametros_comision`
```json
{ "_id": { "$oid": "6741a0c1e4b0a1b2c3d4e110" }, "porcentaje": { "$numberDecimal": "10.00" },
  "vigenteDesde": { "$date": "2026-11-01T00:00:00Z" }, "vigenteHasta": null,
  "modificadoPor": { "$oid": "6741a0c1e4b0a1b2c3d4e500" } }
```
- `configurar()`: cierra el registro vigente (`vigenteHasta = ahora`) e inserta uno nuevo. `consultarVigente()`: `{vigenteHasta: null}`.
- Índices: `{vigenteDesde: -1}`; parcial único `uk_comision_vigente` sobre `{vigente: 1}` con `partialFilterExpression: {vigente: true}`, para que solo exista un parámetro vigente. (+) `vigente` es un booleano derivado de `vigenteHasta == null`: Spring Data omite los campos nulos, por lo que un filtro parcial sobre `vigenteHasta: null` no los alcanzaría.
- Los servicios ya confirmados conservan su `comisionPct` (HU-02).

---

## 5. Ciclo de negociación

### 5.1 `solicitudes_tutoria` — SolicitudTutoria (+ Archivo embebido)

| Campo | Tipo | Notas |
|---|---|---|
| `tema`, `descripcion` | string | descripción ≥ 20 caracteres, sin frases genéricas ni palabras de fraude (R04, R06) |
| `tipoServicio` | string | EXPLICACION_TEMA · RESOLUCION_GUIADA_EJERCICIOS · PREPARACION_EXAMEN · REVISION_TRABAJO |
| `fechaHora` | date | sugerida |
| `modalidad` | VIRTUAL · PRESENCIAL | |
| `zonaEncuentro` | string \| null | obligatoria si es PRESENCIAL (zona segura del campus) |
| `duracion` | MIN_30 · MIN_60 · MIN_120 | R04 |
| `precioInicialCOP` | decimal | > 0 |
| `estado` | EstadoSolicitud | |
| (+)`estudianteId` | ObjectId | «publica» |
| (+)`asignaturaId`, `programaId`, `facultadId`, `universidadId` | ObjectId | universidad, facultad y programa se derivan de la asignatura y se guardan desnormalizados para los filtros de R07 |
| (+)`fechaCreacion`, `fechaActualizacion` | date | |
| (+)`totalPropuestas` | int | contador para la lista |
| `archivos[]` | Archivo (1..*) | «adjunta» |

```json
{
  "_id": { "$oid": "6741a0c1e4b0a1b2c3d4e201" },
  "estudianteId": { "$oid": "6741a0c1e4b0a1b2c3d4e502" },
  "universidadId": { "$oid": "6741a0c1e4b0a1b2c3d4e101" },
  "facultadId": { "$oid": "6741a0c1e4b0a1b2c3d4e102" },
  "programaId": { "$oid": "6741a0c1e4b0a1b2c3d4e103" },
  "asignaturaId": { "$oid": "6741a0c1e4b0a1b2c3d4e104" },
  "tema": "Ecuaciones diferenciales de primer orden y variables separables",
  "descripcion": "Requiero explicación detallada del método de separación de variables y resolución guiada de 3 ejercicios preparatorios para el taller",
  "tipoServicio": "RESOLUCION_GUIADA_EJERCICIOS",
  "fechaHora": { "$date": "2026-12-05T19:00:00Z" },
  "modalidad": "PRESENCIAL",
  "zonaEncuentro": "Biblioteca central, sala de estudio 3",
  "duracion": "MIN_60",
  "precioInicialCOP": { "$numberDecimal": "25000" },
  "estado": "NEGOCIACION_EN_CURSO",
  "totalPropuestas": 2,
  "fechaCreacion": { "$date": "2026-11-23T15:00:00Z" },
  "archivos": [{
    "nombre": "taller_preparatorio.pdf",
    "tipo": "PDF",
    "tamanoMB": { "$numberDecimal": "1.24" },
    "gridFsId": { "$oid": "6741a0c1e4b0a1b2c3d4e902" }
  }]
}
```

| Índice | Definición |
|---|---|
| `ix_busqueda` | `{estado: 1, asignaturaId: 1, modalidad: 1, fechaHora: 1}` |
| `ix_programa` | `{estado: 1, programaId: 1, precioInicialCOP: 1}` |
| `ix_mis_solicitudes` | `{estudianteId: 1, fechaCreacion: -1}` |
| `tx_tema` | índice de texto `{tema: "text", descripcion: "text"}` (idioma `spanish`) |

### 5.2 `propuestas` — Propuesta
Se guarda en una colección propia porque varios tutores escriben a la vez sobre la misma solicitud.

```json
{
  "_id": { "$oid": "6741a0c1e4b0a1b2c3d4e301" },
  "solicitudId": { "$oid": "6741a0c1e4b0a1b2c3d4e201" },
  "tutorId": { "$oid": "6741a0c1e4b0a1b2c3d4e501" },
  "tipo": "ALTERNATIVA_CONDICIONES",
  "precioCOP": { "$numberDecimal": "30000" },
  "duracion": "MIN_90",
  "modalidad": "PRESENCIAL",
  "nota": "Incluye explicación conceptual detallada y material de apoyo",
  "estado": "ENVIADA",
  "activa": true,
  "fechaCreacion": { "$date": "2026-11-23T16:10:00Z" },
  "fechaActualizacion": { "$date": "2026-11-23T16:10:00Z" }
}
```
- `tipo`: ACEPTA_PRECIO · CONTRAPROPUESTA_PRECIO · ALTERNATIVA_CONDICIONES. `duracion`: MIN_30, MIN_45, MIN_60, MIN_90, MIN_120.
- `estado`: ENVIADA · CONTRAOFERTA_ESTUDIANTE · ACEPTADA · DESCARTADA · RETIRADA. `activa = estado ∈ {ENVIADA, CONTRAOFERTA_ESTUDIANTE, ACEPTADA}`.
- Índices: `{solicitudId: 1, estado: 1}`; `{tutorId: 1, fechaCreacion: -1}`; **parcial único** `{solicitudId: 1, tutorId: 1}` con `partialFilterExpression: {activa: true}` (HU-05: una propuesta activa por tutor y solicitud).  (+) `contraofertaCOP`: precio propuesto por el estudiante mientras el estado es CONTRAOFERTA_ESTUDIANTE.

### 5.3 `servicios_tutoria` — ServicioTutoria
Guarda las condiciones congeladas al confirmar (R09). Ningún endpoint modifica `precioFinalCOP`, `duracion`, `modalidad` ni `comisionPct`.

```json
{
  "_id": { "$oid": "6741a0c1e4b0a1b2c3d4e401" },
  "solicitudId": { "$oid": "6741a0c1e4b0a1b2c3d4e201" },
  "propuestaId": { "$oid": "6741a0c1e4b0a1b2c3d4e301" },
  "estudianteId": { "$oid": "6741a0c1e4b0a1b2c3d4e502" },
  "tutorId": { "$oid": "6741a0c1e4b0a1b2c3d4e501" },
  "precioFinalCOP": { "$numberDecimal": "30000" },
  "duracion": "MIN_90",
  "modalidad": "PRESENCIAL",
  "lugarOEnlace": "Biblioteca central, sala de estudio 3",
  "fechaHora": { "$date": "2026-12-05T19:00:00Z" },
  "comisionPct": { "$numberDecimal": "10.00" },
  "fechaConfirmacion": { "$date": "2026-11-23T17:00:00Z" },
  "fechaInicio": null,
  "fechaFinalizacion": null,
  "estado": "CONFIRMADO"
}
```
Índices: `{solicitudId: 1}` único; `{propuestaId: 1}` único; `{estudianteId: 1, estado: 1}`; `{tutorId: 1, estado: 1}`; `{estado: 1, fechaHora: 1}` (programador de tareas).

### 5.4 `pagos` — Pago

```json
{
  "_id": { "$oid": "6741a0c1e4b0a1b2c3d4e451" },
  "servicioId": { "$oid": "6741a0c1e4b0a1b2c3d4e401" },
  "montoRetenidoCOP": { "$numberDecimal": "40000" },
  "comisionCOP": { "$numberDecimal": "4000" },
  "montoNetoCOP": { "$numberDecimal": "36000" },
  "estado": "LIBERADO",
  "referenciaPasarela": "SIM-8f14e45f-ceea-467e-a7c3-1e2b4b7f3a10",
  "fechaRetencion": { "$date": "2026-11-23T17:00:00Z" },
  "fechaLiquidacion": { "$date": "2026-12-05T21:00:00Z" },
  "historial": [
    { "estado": "RETENIDO", "fecha": { "$date": "2026-11-23T17:00:00Z" } },
    { "estado": "LIBERADO", "fecha": { "$date": "2026-12-05T21:00:00Z" } }
  ]
}
```
- Liquidación (R12): `comisionCOP = round(precio × pct / 100)`, `montoNetoCOP = precio − comisionCOP`. Ejemplo: $40.000 con 10 % → $4.000 / $36.000.
- Al confirmar (RETENIDO) ya se guardan `comisionCOP` y `montoNetoCOP` calculados con la comisión congelada del servicio (proyección de lo que se liquidará); `liberar` los recalcula con el mismo `comisionPct` y `reembolsar` los pone en 0.
- `estado`: RETENIDO · LIBERADO · REEMBOLSADO · CONGELADO · RECHAZADO. No se guarda ningún dato de tarjeta: solo la referencia del simulador.
- Índices: `{servicioId: 1}` único; `{referenciaPasarela: 1}` único *sparse*; `{estado: 1}`.

### 5.5 `mensajes` — Mensaje (+ Archivo embebido 0..1)
Cada hilo de chat se identifica por `(solicitudId, tutorId)` para que los tutores que negocian la misma solicitud no lean las conversaciones de los demás. Los mensajes son **inmutables**: ninguna operación los edita ni los borra; solo se cambian `reportado` y `motivoReporte`.

```json
{
  "_id": { "$oid": "6741a0c1e4b0a1b2c3d4e601" },
  "solicitudId": { "$oid": "6741a0c1e4b0a1b2c3d4e201" },
  "tutorId": { "$oid": "6741a0c1e4b0a1b2c3d4e501" },
  "emisorId": { "$oid": "6741a0c1e4b0a1b2c3d4e501" },
  "contenido": "Te comparto la guía de ejercicios resueltos",
  "marcaTemporal": { "$date": "2026-11-23T17:30:12.345Z" },
  "reportado": false,
  "motivoReporte": null,
  "archivo": {
    "nombre": "guia_ejercicios.pdf", "tipo": "PDF",
    "tamanoMB": { "$numberDecimal": "0.85" },
    "gridFsId": { "$oid": "6741a0c1e4b0a1b2c3d4e903" }
  }
}
```
Índices: `{solicitudId: 1, tutorId: 1, marcaTemporal: 1}` (polling `desde`); parcial `{reportado: 1, marcaTemporal: -1}` con `partialFilterExpression: {reportado: true}`.

---

## 6. Reputación

### 6.1 `calificaciones` — Calificacion (abstracta) / CalificacionAlTutor / CalificacionAlEstudiante
Herencia en una sola colección. Cada subclase lleva `@TypeAlias("AL_TUTOR")` / `@TypeAlias("AL_ESTUDIANTE")` (Spring lo guarda en `_class`) y además el campo explícito `tipo` (`AL_TUTOR` | `AL_ESTUDIANTE`) sobre el que se apoya el índice único `(servicioId, tipo)`.

```json
{
  "_id": { "$oid": "6741a0c1e4b0a1b2c3d4e701" },
  "tipo": "AL_TUTOR",
  "servicioId": { "$oid": "6741a0c1e4b0a1b2c3d4e401" },
  "autorId": { "$oid": "6741a0c1e4b0a1b2c3d4e502" },
  "destinatarioId": { "$oid": "6741a0c1e4b0a1b2c3d4e501" },
  "comentario": "Excelente explicación, muy paciente y claro con las ecuaciones",
  "fechaEmision": { "$date": "2026-12-06T10:00:00Z" },
  "fechaLimite": { "$date": "2026-12-19T21:00:00Z" },
  "claridadPedagogica": 5, "dominioTematico": 5, "puntualidad": 5,
  "cumplimiento": 5, "trato": 5, "relacionCalidadPrecio": 4,
  "visible": false
}
```
```json
{
  "_id": { "$oid": "6741a0c1e4b0a1b2c3d4e702" },
  "tipo": "AL_ESTUDIANTE",
  "servicioId": { "$oid": "6741a0c1e4b0a1b2c3d4e401" },
  "autorId": { "$oid": "6741a0c1e4b0a1b2c3d4e501" },
  "destinatarioId": { "$oid": "6741a0c1e4b0a1b2c3d4e502" },
  "comentario": "Estudiante puntual y preparado con los ejercicios",
  "fechaEmision": { "$date": "2026-12-06T12:00:00Z" },
  "fechaLimite": { "$date": "2026-12-19T21:00:00Z" },
  "puntualidad": 5, "comunicacion": 5, "cumplimiento": 4,
  "visible": true
}
```
- Criterios de 1 a 5. `calcularPromedio()` = media de los criterios. `fechaLimite` = `fechaFinalizacion` + 14 días.
- `visible` (+) pasa a `true` cuando ambas partes calificaron o cuando vence `fechaLimite` (HU-09).
- Índices: `{servicioId: 1, tipo: 1}` **único** (máximo 2 calificaciones por servicio, «0..2»); `{destinatarioId: 1, visible: 1, fechaEmision: -1}`.

---

## 7. Reportes y sanciones

### 7.1 `reportes` — Reporte

```json
{
  "_id": { "$oid": "6741a0c1e4b0a1b2c3d4e801" },
  "reportanteId": { "$oid": "6741a0c1e4b0a1b2c3d4e502" },
  "denunciadoId": { "$oid": "6741a0c1e4b0a1b2c3d4e501" },
  "servicioId": { "$oid": "6741a0c1e4b0a1b2c3d4e401" },
  "tipo": "INASISTENCIA",
  "motivo": "El tutor no se presentó tras 20 minutos de tolerancia",
  "evidencias": [{ "$oid": "6741a0c1e4b0a1b2c3d4e904" }],
  "estado": "EN_REVISION",
  "fechaRadicacion": { "$date": "2026-12-05T19:25:00Z" },
  "resolucion": null
}
```
- `tipo`: INASISTENCIA · COBRO_EXTERNO · FRAUDE_ACADEMICO · CONDUCTA_ABUSIVA · ACOSO · FALSEDAD_CREDENCIALES · DESACUERDO_ACADEMICO.
- Categoría R14 derivada del tipo: incumplimiento de servicio (INASISTENCIA, COBRO_EXTERNO), conducta abusiva (CONDUCTA_ABUSIVA, ACOSO, FRAUDE_ACADEMICO, FALSEDAD_CREDENCIALES) o desacuerdo académico (DESACUERDO_ACADEMICO).
- `estado`: RADICADO · EN_REVISION · RESUELTO · DESESTIMADO.
- `resolucion` (+): `{decision, accionPago, observacion, resueltoPor, fecha}`.
- Índices: `{estado: 1, fechaRadicacion: -1}`, `{reportanteId: 1}`, `{denunciadoId: 1}`, `{servicioId: 1}`.

### 7.2 `sanciones` — Sancion (+ Apelacion embebida)

```json
{
  "_id": { "$oid": "6741a0c1e4b0a1b2c3d4e851" },
  "reporteId": { "$oid": "6741a0c1e4b0a1b2c3d4e801" },
  "usuarioId": { "$oid": "6741a0c1e4b0a1b2c3d4e501" },
  "tipo": "SUSPENSION_TEMPORAL",
  "vigencia": { "inicio": { "$date": "2026-12-07T00:00:00Z" }, "fin": { "$date": "2026-12-21T00:00:00Z" } },
  "estado": "EN_APELACION",
  "apelaciones": [{
    "id": "a7b8c9d0-1111-4222-8333-944455566677",
    "descargo": "Tuve una emergencia médica; adjunto la incapacidad.",
    "evidencias": [{ "$oid": "6741a0c1e4b0a1b2c3d4e905" }],
    "resultado": "PENDIENTE",
    "fechaPresentacion": { "$date": "2026-12-08T10:00:00Z" },
    "fechaResolucion": null
  }]
}
```
- `tipo`: ADVERTENCIA · SUSPENSION_TEMPORAL · BLOQUEO_PERMANENTE. `estado`: VIGENTE · EN_APELACION · RATIFICADA · REVOCADA · CUMPLIDA. `Apelacion.resultado`: PENDIENTE · RATIFICADA · REVOCADA.
- Índices: `{usuarioId: 1, estado: 1}`, `{reporteId: 1}`, `{"apelaciones.resultado": 1}`.

---

## 8. Auditoría y notificaciones

### 8.1 `registros_auditoria` — RegistroAuditoria (solo inserción)
```json
{
  "_id": { "$oid": "6741a0c1e4b0a1b2c3d4ea01" },
  "usuarioId": { "$oid": "6741a0c1e4b0a1b2c3d4e502" },
  "accion": "SOLICITUD_RECHAZADA_ANTIFRAUDE",
  "entidadAfectada": "SolicitudTutoria",
  "fecha": { "$date": "2026-11-23T15:00:00Z" },
  "detalle": { "coincidencia": "examen parcial en vivo" }
}
```
Índices: `{fecha: -1}`, `{usuarioId: 1, fecha: -1}`, `{entidadAfectada: 1, fecha: -1}`. `usuarioId` es `null` cuando la acción la ejecuta el sistema (cierre automático).

### 8.2 `notificaciones` (infraestructura de `INotificador`, no es clase de dominio)
```json
{
  "_id": { "$oid": "6741a0c1e4b0a1b2c3d4eb01" },
  "usuarioId": { "$oid": "6741a0c1e4b0a1b2c3d4e502" },
  "tipo": "NUEVA_PROPUESTA",
  "titulo": "Nueva propuesta recibida",
  "mensaje": "Laura Martínez propuso $30.000 COP por 90 minutos.",
  "enlace": "negociacion.html?solicitud=6741a0c1e4b0a1b2c3d4e201",
  "leida": false,
  "fecha": { "$date": "2026-11-23T16:10:01Z" }
}
```
Índices: `{usuarioId: 1, leida: 1, fecha: -1}`; TTL `{fecha: 1}` con `expireAfterSeconds: 15552000` (180 días).

---

## 9. GridFS (bucket `archivos`)
- Colecciones: `archivos.files` (metadatos) y `archivos.chunks` (fragmentos de 255 KB).
- Tipos permitidos: `application/pdf` (.pdf), `image/png` (.png), `image/jpeg` (.jpg/.jpeg), `application/vnd.openxmlformats-officedocument.wordprocessingml.document` (.docx). Máximo **10 MB**. Se validan la extensión y el content-type; cualquier otro (p. ej. `.exe`, `.bat`) se rechaza con 415.
- Configuración: `spring.servlet.multipart.max-file-size=10MB`, `max-request-size=50MB`.

```json
{
  "_id": { "$oid": "6741a0c1e4b0a1b2c3d4e902" },
  "filename": "taller_preparatorio.pdf",
  "length": 1300234,
  "chunkSize": 261120,
  "uploadDate": { "$date": "2026-11-23T14:59:58Z" },
  "metadata": {
    "contentType": "application/pdf",
    "propietarioId": { "$oid": "6741a0c1e4b0a1b2c3d4e502" },
    "contexto": "SOLICITUD",
    "referenciaId": null
  }
}
```
- `contexto`: SOLICITUD · MENSAJE · SOPORTE · REPORTE · APELACION. `referenciaId` se completa después de la transacción. Si queda `null` más de 24 h, el archivo se considera huérfano y lo limpia una tarea diaria.
- Control de acceso en `GET /api/archivos/{id}`: SOLICITUD → cualquier usuario autenticado; MENSAJE → participantes del hilo y administrador; SOPORTE, REPORTE, APELACION → propietario y administrador.
- Índice: `{"metadata.contexto": 1, "metadata.referenciaId": 1}`.

## 10. Transacciones
| Operación | Documentos afectados | Transacción |
|---|---|---|
| `confirmarServicio` | solicitud, propuestas, servicio, pago | sí |
| `finalizarServicio` / `cerrarAutomaticamente` | servicio, pago, perfil del tutor | sí (una por servicio) |
| `radicarReporte` | reporte, servicio, pago | sí (las evidencias se suben antes, fuera de la transacción) |
| `resolverReporte` | reporte, pago, sanción, usuario | sí |
| Subidas y descargas GridFS | archivos.* | **nunca** dentro de `@Transactional` |

Con `MONGO_TRANSACCIONES=false` (Mongo local sin replica set) las operaciones se ejecutan sin atomicidad multi-documento; esto solo es aceptable en desarrollo.
