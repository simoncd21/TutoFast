### HU-03: Publicar solicitud de tutoría académica

**Como** estudiante solicitante
**Quiero** publicar una necesidad de tutoría indicando campos institucionales, tema específico, descripción pedagógica detallada, archivos adjuntos, modalidad, horario, duración y precio inicial propuesto en COP
**Para** recibir propuestas personalizadas de tutores calificados bajo reglas claras de integridad académica

**Validación INVEST**
- I — Independent: ✅ Puede implementarse asumiendo la disponibilidad del catálogo académico y de usuarios registrados.
- N — Negotiable: ✅ La lista de palabras clave para el filtro preventivo de fraude y los formatos de archivos permitidos son negociables.
- V — Valuable: ✅ Constituye el punto de partida del mercado dinámico de UniDrive, garantizando información precisa para que los tutores formulen ofertas realistas.
- E — Estimable: ✅ Formulario estructurado con validaciones de longitud, obligatoriedad y reglas anti-fraude claramente definidas.
- S — Small: ✅ Acotado al ciclo de creación, validación sintáctica/semántica y guardado en estado "Solicitud publicada".
- T — Testable: ✅ Se puede verificar con casos válidos (ej. Ecuaciones Diferenciales por $25.000 COP), rechazo por texto vago y detección preventiva de fraude o suplantación.

**Criterios de aceptación (Gherkin)**
```gherkin
# language: es
Característica: Publicar solicitud de tutoría académica

  Escenario: Publicación exitosa de solicitud académica detallada
    Dado que el estudiante "carlos@universidad.edu.co" está autenticado en la plataforma
    Y selecciona universidad "Universidad Central", facultad "Ingeniería", programa "Ingeniería de Software" y asignatura "Ecuaciones Diferenciales"
    Cuando publica una solicitud con tema "Ecuaciones diferenciales de primer orden y variables separables", descripción "Requiero explicación detallada del método de separación de variables y resolución guiada de 3 ejercicios preparatorios para el taller", modalidad "Presencial en zona segura de biblioteca", duración "1 hora", fecha "2026-11-05", hora "14:00", adjunta el archivo "taller_preparatorio.pdf" y ofrece un precio inicial de "$25.000 COP"
    Entonces el sistema registra la solicitud con un identificador único
    Y el estado del servicio queda establecido en "Solicitud publicada"
    Y la solicitud queda visible en el panel general para los tutores calificados

  Escenario: Rechazo de solicitud por descripción vaga o insuficiente
    Dado que el estudiante se encuentra en el formulario de publicación de solicitud
    Cuando ingresa en el campo de descripción el texto "necesito ayuda con matemáticas"
    Entonces el sistema bloquea el envío del formulario
    Y muestra una advertencia indicando "La descripción debe tener al menos 20 caracteres y especificar el objetivo pedagógico del tema; no se admiten textos genéricos"

  Escenario: Detección preventiva y bloqueo por sospecha de fraude o suplantación
    Dado que el estudiante redacta una solicitud académica
    Cuando incluye en la descripción o título expresiones como "resolver examen parcial en vivo", "hacer evaluación virtual por mí" o "suplantación de quiz"
    Entonces el sistema bloquea inmediatamente la publicación
    Y notifica al estudiante "Solicitud rechazada: UniDrive prohíbe terminantemente la suplantación y la realización de evaluaciones calificables individuales ajenas (R06)"
    Y genera una alerta de auditoría preventiva en el módulo de seguridad
```

**Supuestos / preguntas abiertas**
- La modalidad presencial solo admite ubicaciones categorizadas como "zonas seguras" dentro o en el entorno directo del campus universitario (ej. bibliotecas, salas de estudio, cafeterías centrales).
- Los archivos adjuntos están limitados a formatos PDF e imágenes (PNG, JPG) con un peso máximo de 10 MB por archivo.
