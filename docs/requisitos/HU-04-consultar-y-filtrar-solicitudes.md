### HU-04: Consultar y filtrar solicitudes de tutoría

**Como** tutor académico o estudiante
**Quiero** consultar y filtrar solicitudes de tutoría y perfiles de tutores aplicando criterios de universidad, facultad, programa, asignatura, tema, modalidad, horario, precio y calificación
**Para** encontrar con rapidez oportunidades pertinentes de enseñanza o profesionales con credenciales académicas verificadas

**Validación INVEST**
- I — Independent: ✅ Opera como mecanismo de consulta y lectura sobre las entidades existentes (solicitudes y perfiles).
- N — Negotiable: ✅ Criterios de ordenamiento (precio ascendente/descendente, mayor calificación, más recientes) negociables con producto.
- V — Valuable: ✅ Reduce los tiempos de búsqueda y facilita el emparejamiento eficiente entre la necesidad académica del estudiante y la experiencia del tutor.
- E — Estimable: ✅ Parámetros de filtrado estándar y paginación técnica bien documentada.
- S — Small: ✅ Delimitado a la búsqueda, aplicación de filtros combinados y presentación de resultados en tarjetas informativas.
- T — Testable: ✅ Verificable con filtros por asignatura, modalidad, rango de precio, distintivo de tutor verificado y resultados vacíos.

**Criterios de aceptación (Gherkin)**
```gherkin
# language: es
Característica: Consultar y filtrar solicitudes de tutoría

  Escenario: Filtrado de solicitudes por asignatura y modalidad virtual
    Dado que existen solicitudes activas para "Ecuaciones Diferenciales" en modalidades virtual y presencial
    Cuando un tutor filtra por asignatura "Ecuaciones Diferenciales" y selecciona la modalidad "Virtual"
    Entonces el sistema muestra únicamente las solicitudes publicadas que coinciden con dicha asignatura y modalidad
    Y muestra en cada resultado el tema específico, la duración y el precio propuesto por el estudiante

  Escenario: Búsqueda de tutores con calificación superior y verificación académica
    Dado que un estudiante desea contratar un tutor especializado
    Cuando aplica los filtros de programa "Ingeniería de Software", calificación mínima de "4.5" estrellas y activa la opción "Solo Tutores Verificados"
    Entonces el sistema lista únicamente aquellos tutores con promedio igual o superior a 4.5 y que cuentan con distintivo verificado por el Administrador
    Y excluye aquellos tutores no verificados o con calificaciones menores

  Escenario: Consulta sin resultados coincidentes con los criterios ingresados
    Dado que no existen solicitudes activas para la asignatura "Física Cuántica Avanzada" con precio menor a "$15.000 COP"
    Cuando un usuario realiza dicha búsqueda combinada
    Entonces el sistema muestra un mensaje indicando "No se encontraron solicitudes que coincidan con los filtros seleccionados"
    Y sugiere ajustar los rangos de precio o ampliar la búsqueda a otras asignaturas afines
```

**Supuestos / preguntas abiertas**
- La consulta de solicitudes abiertas excluye de forma automática aquellas que ya se encuentren en estado "Servicio confirmado", "Tutoría en desarrollo", "Servicio finalizado" o "Cancelado".
- Los tutores suspendidos preventivamente por reportes pendientes quedan ocultos de los resultados de búsqueda.
