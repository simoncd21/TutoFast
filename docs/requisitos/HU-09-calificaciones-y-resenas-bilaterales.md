### HU-09: Calificaciones y reseñas bilaterales

**Como** estudiante o tutor participante en una sesión finalizada
**Quiero** calificar y emitir una reseña sobre la otra parte evaluando múltiples factores pedagógicos y de comportamiento
**Para** construir una reputación confiable en la comunidad y retroalimentar la calidad de los servicios prestados

**Validación INVEST**
- I — Independent: ✅ Requiere que el servicio esté en estado "Servicio finalizado", funcionando como un módulo posterior de reputación.
- N — Negotiable: ✅ Factores específicos de evaluación (ej. claridad, puntualidad, trato) y escalas numéricas de calificación son negociables.
- V — Valuable: ✅ Incentiva la excelencia académica y el cumplimiento riguroso, guiando a futuros usuarios en la elección de tutores o estudiantes idóneos.
- E — Estimable: ✅ Criterios multifactoriales claros y restricciones de evaluación única por servicio.
- S — Small: ✅ Acotado al formulario de evaluación bilateral y cálculo del promedio ponderado en el perfil.
- T — Testable: ✅ Comprobable mediante envío de calificaciones válidas, cálculo de promedios y bloqueo de reseñas en servicios no finalizados.

**Criterios de aceptación (Gherkin)**
```gherkin
# language: es
Característica: Calificaciones y reseñas bilaterales

  Escenario: Estudiante califica exitosamente al tutor tras finalizar el servicio
    Dado que el estudiante ha completado una tutoría con el "Tutor Alfa" y el servicio está en estado "Servicio finalizado"
    Cuando el estudiante emite una calificación de "5" estrellas evaluando claridad pedagógica, dominio temático, puntualidad, cumplimiento, trato y relación calidad/precio, y añade la reseña "Excelente explicación, muy paciente y claro con las ecuaciones"
    Entonces el sistema almacena la calificación y la reseña vinculada al servicio y al perfil del tutor
    Y recalcula el promedio general de calificaciones del tutor en la plataforma

  Escenario: Tutor califica al estudiante tras finalizar el servicio
    Dado que el tutor ha impartido la sesión y el servicio se encuentra en estado "Servicio finalizado"
    When el tutor califica al estudiante evaluando puntualidad, comunicación y cumplimiento con "4.8" estrellas y reseña "Estudiante puntual y preparado con los ejercicios"
    Entonces el sistema registra la evaluación en el historial del estudiante
    Y actualiza el indicador de confiabilidad del estudiante para futuras contrataciones

  Escenario: Restricción de calificación en servicios no finalizados o cancelados
    Dado que una solicitud se encuentra en estado "Cancelado" o "Negociación en curso"
    Cuando un usuario intenta emitir una calificación o reseña
    Entonces el sistema bloquea la acción
    Y muestra el mensaje "Las calificaciones y reseñas están restringidas exclusivamente a servicios registrados y finalizados en la plataforma (R13)"
```

**Supuestos / preguntas abiertas**
- Cada participante dispone de un plazo máximo de 14 días calendario tras la finalización para emitir su reseña. Transcurrido este tiempo, el espacio de calificación se cierra.
- Las reseñas son públicas en los perfiles de los usuarios una vez ambas partes han calificado o cuando expira el plazo de los 14 días.
