# Requisitos consolidados de TutoFast

> Versión limpia de `requerimientos.txt`, `preguntas.txt` y `HU-01` a `HU-11`, actualizada al nombre **TutoFast** y a las decisiones de arquitectura (MongoDB Atlas + GridFS, frontend HTML/CSS/JS puro, simulador de pasarela, notificaciones en MongoDB, chat por polling). Los archivos originales de esta carpeta se conservan como histórico. Si hay conflicto, prevalecen `CLAUDE.md` y los diagramas.

## 1. Descripción del caso
TutoFast es una plataforma web universitaria (responsive, usable desde el móvil) que conecta a estudiantes que necesitan apoyo académico con tutores pares y monitores certificados, mediante un modelo de **negociación dinámica de precios** inspirado en inDrive.

Actores: **Estudiante solicitante**, **Tutor** y **Administrador**. Una misma cuenta puede activar los roles Estudiante y Tutor.

## 2. Requisitos R01–R16

| ID | Requisito (versión TutoFast) | Cumplimiento |
|---|---|---|
| R01 | TutoFast registra usuarios con roles Estudiante, Tutor y Administrador, protegiendo los datos personales conforme a la Ley Estatutaria 1581 de 2012 (Habeas Data): consentimiento obligatorio con fecha y hora registradas. | HU-01 · ServicioIdentidad |
| R02 | El perfil del tutor separa la información **declarada** (áreas, presentación) de la **verificada** con soportes institucionales auditados por el Administrador (certificado de monitoría, historial de calificaciones, carné vigente). | HU-01 · SoporteVerificacion en GridFS |
| R03 | El Administrador gestiona el catálogo de universidades, facultades, programas y asignaturas, y configura el porcentaje de comisión (10 % por defecto). | HU-02 · ServicioCatalogo |
| R04 | Una solicitud exige: universidad, facultad, programa, asignatura, tema, descripción detallada (sin textos genéricos), archivos adjuntos (PDF, PNG, JPG, DOCX ≤ 10 MB), tipo de servicio, fecha y hora, modalidad (virtual o presencial en zona segura del campus), duración (30 min, 1 h o 2 h) y precio inicial en COP. | HU-03 · ServicioSolicitudes |
| R05 | Editar o cancelar una solicitud solo está permitido antes de la confirmación bilateral. | HU-06 |
| R06 | Se previenen solicitudes de fraude académico, suplantación en exámenes en tiempo real o realización de evaluaciones calificables ajenas (filtro de palabras clave + auditoría). | HU-03 |
| R07 | Se pueden consultar y filtrar solicitudes y perfiles de tutores por universidad, facultad, programa, asignatura, tema, modalidad, disponibilidad horaria, precio y calificación promedio. | HU-04 |
| R08 | Negociación estilo inDrive: el tutor puede (1) aceptar el precio, (2) contraproponer un precio o (3) proponer otra duración o modalidad con su valor. El estudiante puede contraofertar. | HU-05 · ServicioNegociacion |
| R09 | El estudiante selecciona una propuesta y la confirmación bilateral congela el precio y las condiciones; no se permiten cambios unilaterales. | HU-06 |
| R10 | Ciclo de vida: Solicitud publicada, Propuestas recibidas, Negociación en curso, Tutor seleccionado, Servicio confirmado, Tutoría en desarrollo, Servicio finalizado, Cancelado, En revisión por reporte o disputa. | EstadoSolicitud + EstadoServicio |
| R11 | Cada solicitud tiene mensajería interna contextual (API REST + polling) con archivos permitidos, reporte de mensajes indebidos e historial inmutable. | HU-07 · ServicioMensajeria |
| R12 | La finalización la confirma el estudiante; el sistema calcula la comisión y el pago neto al tutor (ej.: $40.000 con 10 % → la plataforma retiene $4.000 y libera $36.000). Pagos mediante el **simulador de pasarela** interno; no se almacenan datos de tarjeta. | HU-08 · ServicioPagos |
| R13 | Calificaciones bilaterales: el estudiante califica al tutor (claridad pedagógica, dominio temático, puntualidad, cumplimiento, trato, relación calidad/precio) y el tutor al estudiante (puntualidad, comunicación, cumplimiento). Solo sobre servicios finalizados. | HU-09 · ServicioReputacion |
| R14 | Reportes, reclamaciones y sanciones por inasistencia, cobro por fuera de la plataforma, fraude académico, conducta abusiva, acoso o credenciales falsas. Se distingue entre incumplimiento de servicio, conducta abusiva y desacuerdo académico, con derecho a descargos y apelación. | HU-10 · ServicioReportes |
| R15 | Arquitectura en tres capas desacopladas: presentación web (HTML5/CSS3/JS), lógica de aplicación (Spring Boot) y persistencia. | HU-11 |
| R16 | ~~PostgreSQL~~ → **MongoDB Atlas** (base de datos `tutofast`) con GridFS para archivos. La integridad de las operaciones críticas se garantiza con transacciones multi-documento de MongoDB. | HU-11 · ADR-002 |

Requisitos no funcionales derivados: despliegue GitHub → Render (Web Service con Docker y Static Site); autenticación JWT + BCrypt; notificaciones guardadas en MongoDB (sin correo); documentación de la API en Markdown.

## 3. Decisiones de negocio (respuestas de `preguntas.txt`)
1. **Registro:** los estudiantes se autorregistran aceptando la política de datos. Los tutores se registran con perfil declarativo y necesitan aprobación documental del Administrador para obtener el distintivo **"Tutor Verificado"**. Los correos `.edu.co` tienen prioridad en la cola de verificación, pero no se bloquean los correos personales.
2. **Catálogo y comisión:** solo los gestiona el Administrador. La comisión global es de 10 % por defecto y un cambio solo se aplica a las confirmaciones posteriores.
3. **Calidad y antifraude:** descripción de 20 caracteres o más, sin frases genéricas; rechazo automático ante palabras clave de fraude (suplantación, examen en tiempo real, parcial virtual…), registrado en la auditoría. La solicitud solo puede editarse o cancelarse antes de la confirmación.
4. **Negociación:** se admiten ofertas concurrentes de varios tutores (como máximo una activa por tutor). Al confirmar una, las demás se descartan automáticamente y el acuerdo queda congelado.
5. **Pagos:** al confirmar se retiene el valor acordado (simulador). Cuando el estudiante finaliza, se calcula la comisión y se libera el neto. Hay cierre automático 24 h después del fin programado si no hay objeciones (el Administrador puede ejecutarlo al momento para la demo).
6. **Restricciones:** solo se califican servicios finalizados; todo reporte pasa el servicio a "En revisión" y congela la liquidación; arquitectura en tres capas; MongoDB Atlas.

## 4. Historias de usuario consolidadas

### HU-01 · Registro y perfiles de usuario
**Como** estudiante o tutor **quiero** registrarme aceptando la política de datos y aportando soportes **para** acceder con una identidad respaldada.
- Registro exitoso con consentimiento → se crea la cuenta, se guarda `fechaAutorizacionDatos` y se muestra **"Registro exitoso en TutoFast"**.
- Sin consentimiento → rechazo: "Debe aceptar la política de tratamiento de datos conforme a la Ley 1581 de 2012 para continuar".
- Tutor con soporte adjunto (GridFS) → "Tutor Registrado (No Verificado)" y queda pendiente en la cola de auditoría del Administrador.
- Correo `.edu.co` → insignia "Prioridad alta" en la cola de verificación.
- El Administrador aprueba → **"Tutor Verificado"** y sello de garantía en el perfil público.

### HU-02 · Gestión de catálogos académicos y comisión
**Como** administrador **quiero** gestionar el catálogo y la comisión **para** estandarizar la oferta y asegurar la sostenibilidad.
- Crear la asignatura "IS-204 Ecuaciones Diferenciales" en un programa → queda habilitada.
- Código duplicado en el mismo programa → "La asignatura con este código ya se encuentra registrada en el programa".
- Cambiar la comisión de 10 % a 12 % → se guarda con fecha y usuario en la auditoría; los servicios ya confirmados conservan su porcentaje.
- Desactivar un elemento es lógico y no afecta a las solicitudes existentes.

### HU-03 · Publicar solicitud de tutoría
**Como** estudiante **quiero** publicar una necesidad detallada con precio inicial en COP **para** recibir propuestas.
- Solicitud válida (ej.: Ecuaciones Diferenciales, 1 h, presencial en la biblioteca, $25.000, `taller_preparatorio.pdf`) → estado "Solicitud publicada" y visible para los tutores.
- Descripción vaga ("necesito ayuda con matemáticas") → "La descripción debe tener al menos 20 caracteres y especificar el objetivo pedagógico del tema; no se admiten textos genéricos".
- Expresiones de fraude ("resolver examen parcial en vivo", "hacer evaluación virtual por mí", "suplantación de quiz") → "Solicitud rechazada: TutoFast prohíbe terminantemente la suplantación y la realización de evaluaciones calificables individuales ajenas (R06)" y se registra en la auditoría.
- Adjuntos: PDF, PNG, JPG o DOCX de 10 MB como máximo. Presencial solo en zonas seguras del campus.

### HU-04 · Consultar y filtrar solicitudes y tutores
**Como** tutor o estudiante **quiero** filtrar solicitudes y tutores **para** encontrar oportunidades o tutores adecuados.
- Filtro por asignatura y modalidad virtual → solo coincidencias, mostrando tema, duración y precio.
- Tutores con programa, calificación ≥ 4.5 y "Solo Tutores Verificados" → solo los que cumplen.
- Sin resultados → "No se encontraron solicitudes que coincidan con los filtros seleccionados" y sugerencia de ampliar los filtros.
- Se excluyen las solicitudes confirmadas, en desarrollo, finalizadas o canceladas, y los tutores suspendidos.

### HU-05 · Negociación y contrapropuestas
**Como** tutor **quiero** aceptar, contraproponer o sugerir otras condiciones **para** acordar una tarifa justa.
- Aceptar el precio → propuesta registrada, solicitud "Propuestas recibidas" y aviso al estudiante (notificación en MongoDB).
- Contrapropuesta de $30.000 → "Negociación en curso" y visible en la bandeja del estudiante.
- Alternativa (45 min virtual por $22.000 o 90 min presencial por $30.000) → comparativa visible para el estudiante.
- Solicitud ya confirmada → "La solicitud ya ha sido acordada con otro tutor y no admite nuevas propuestas".
- Una propuesta activa por tutor; el tutor puede modificarla o retirarla antes de que la acepten. El estudiante puede contraofertar.

### HU-06 · Seleccionar tutor y confirmar servicio
**Como** estudiante **quiero** elegir una propuesta y confirmar **para** congelar el precio y las condiciones.
- Seleccionar al "Tutor Alfa" ($30.000, 90 min) y autorizar el pago simulado → "Servicio confirmado", condiciones congeladas, la propuesta del "Tutor Beta" se descarta y se habilita la coordinación por chat.
- Antes de confirmar → se puede editar o cancelar sin penalidad (estado "Cancelado").
- Después de confirmar → precio bloqueado; cualquier cancelación se tramita por reportes.
- Si el simulador rechaza el pago, no se confirma nada.

### HU-07 · Mensajería contextual
**Como** participante **quiero** chatear, compartir archivos y reportar mensajes **para** coordinar con trazabilidad.
- Mensaje con `guia_ejercicios.pdf` → entregado (polling) y guardado con marca temporal inmutable.
- `.exe` o `.bat` → "Tipo de archivo no permitido. Solo se admiten formatos seguros (PDF, PNG, JPG, DOCX)".
- "Reportar mensaje" (Conducta abusiva) → queda marcado para el Administrador y la conversación se preserva sin poder editarse ni borrarse.
- El chat se habilita desde la negociación (un hilo por tutor) y se conserva para consulta histórica.

### HU-08 · Finalizar tutoría y liquidación de pagos
**Como** estudiante **quiero** confirmar la finalización **para** que se liquide con transparencia.
- Servicio "Tutoría en desarrollo" de $40.000 con 10 % → "Servicio finalizado", comisión $4.000, neto $36.000, liberación mediante el simulador hacia la cuenta de cobro del tutor.
- Sin confirmación ni reporte 24 h después del fin programado → cierre automático con liquidación. El Administrador puede forzarlo para la demo con `POST /api/pagos/simular-cierre-automatico`.
- Finalizar dos veces → "El servicio ya se encuentra finalizado y no requiere nuevas acciones".
- Un reclamo dentro de las 24 h frena la liquidación ("En revisión").

### HU-09 · Calificaciones y reseñas bilaterales
**Como** participante de un servicio finalizado **quiero** calificar a la otra parte **para** construir reputación.
- El estudiante califica al tutor en 6 criterios → se recalcula el promedio del tutor.
- El tutor califica al estudiante en 3 criterios → se actualiza el indicador de confiabilidad.
- Servicio no finalizado → "Las calificaciones y reseñas están restringidas exclusivamente a servicios registrados y finalizados en la plataforma (R13)".
- Plazo de 14 días. Las reseñas se publican cuando ambas partes calificaron o cuando vence el plazo.

### HU-10 · Reportes, reclamaciones y sanciones
**Como** usuario afectado **quiero** reportar irregularidades **para** activar el debido proceso.
- Inasistencia del tutor (20 min de tolerancia) con captura → servicio "En revisión", fondos congelados, aviso al denunciado y a soporte.
- El Administrador revisa el historial inmutable del chat → resolución con reembolso y sanción temporal.
- El sancionado presenta descargo y apelación con evidencias → sanción "En apelación pendiente" hasta que la revisión la ratifique o revoque.
- La reincidencia en fraude o acoso puede llevar al bloqueo permanente.

### HU-11 · Organización en tres capas
**Como** arquitecto **quiero** tres capas desacopladas **para** asegurar mantenibilidad e integridad.
- Presentación (HTML/CSS/JS) → solo usa la API REST. Lógica → reglas aisladas; persistencia solo mediante repositorios y puertos.
- Integridad transaccional: confirmar servicio y retener fondos se ejecutan en una transacción multi-documento de **MongoDB Atlas**; si la retención falla, no se cambia ningún estado.
- Pruebas unitarias de los servicios con repositorios y puertos simulados (Mockito), sin acoplarse al motor de base de datos.
