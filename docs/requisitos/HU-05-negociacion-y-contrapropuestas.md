### HU-05: Negociación y contrapropuestas de tutoría

**Como** tutor académico
**Quiero** enviar ofertas sobre solicitudes abiertas aceptando el precio propuesto, enviando una contrapropuesta económica o sugiriendo variaciones en duración o modalidad
**Para** acordar tarifas y condiciones pedagógicas proporcionales a la complejidad temática bajo el modelo dinámico inDrive

**Validación INVEST**
- I — Independent: ✅ Se formula a partir de solicitudes en estado "Solicitud publicada", permitiendo la interacción comercial sin depender aún de la confirmación bilateral.
- N — Negotiable: ✅ Cantidad de ofertas simultáneas y tiempo de expiración de las contrapropuestas negociables según la dinámica del mercado.
- V — Valuable: ✅ Constituye la ventaja competitiva central de UniDrive, adaptando la oferta y demanda académica en tiempo real con flexibilidad para ambas partes.
- E — Estimable: ✅ Tres flujos de respuesta bien tipificados (aceptación directa, contrapropuesta de valor, ajuste de tiempo/modalidad).
- S — Small: ✅ Enfocado en la generación de la propuesta por parte del tutor y la actualización del estado de la solicitud a "Propuestas recibidas" o "Negociación en curso".
- T — Testable: ✅ Comprobable verificando la creación de propuestas con precios ajustados, duraciones alternativas y rechazo de ofertas sobre solicitudes cerradas.

**Criterios de aceptación (Gherkin)**
```gherkin
# language: es
Característica: Negociación y contrapropuestas de tutoría

  Escenario: Tutor acepta directamente el precio ofrecido por el estudiante
    Dado que existe una solicitud publicada para "Ecuaciones Diferenciales" con duración "1 hora" y precio inicial de "$25.000 COP"
    Cuando el tutor "docente.mate@universidad.edu.co" postula su oferta aceptando el valor de "$25.000 COP" bajo las mismas condiciones
    Entonces el sistema registra la oferta en la solicitud
    Y actualiza el estado de la solicitud a "Propuestas recibidas"
    Y notifica al estudiante solicitante sobre la nueva propuesta económica

  Escenario: Tutor envía contrapropuesta económica incrementando el precio
    Dado que existe una solicitud publicada de 1 hora de ecuaciones diferenciales con oferta de "$25.000 COP"
    Cuando el tutor analiza el material adjunto y envía una contrapropuesta económica de "$30.000 COP" por la misma hora con nota pedagógica "Incluye material de apoyo"
    Entonces el sistema registra la contrapropuesta con el valor de "$30.000 COP"
    Y el estado del servicio pasa a "Negociación en curso"
    Y la propuesta queda visible en la bandeja de negociación del estudiante

  Escenario: Tutor propone duración y modalidad alternativa
    Dado que existe una solicitud publicada de 1 hora presencial por "$25.000 COP"
    Cuando el tutor propone una sesión virtual de 45 minutos por "$22.000 COP" o una sesión presencial extendida de 90 minutos con explicación conceptual profunda por "$30.000 COP"
    Entonces el sistema registra la propuesta con la nueva duración, la modalidad indicada y el precio correspondiente
    Y permite al estudiante visualizar de forma comparativa las condiciones ofertadas

  Escenario: Bloqueo de contrapropuestas sobre solicitudes confirmadas o canceladas
    Dado que una solicitud de tutoría cambió a estado "Servicio confirmado"
    Cuando otro tutor intenta enviar una contrapropuesta económica
    Entonces el sistema rechaza la postulación
    Y muestra una notificación indicando "La solicitud ya ha sido acordada con otro tutor y no admite nuevas propuestas"
```

**Supuestos / preguntas abiertas**
- Cada tutor puede mantener como máximo una propuesta activa simultánea por cada solicitud publicada. Si desea rectificar su oferta antes de ser aceptada, puede actualizar su valor o retirarla.
- Las contrapropuestas no obligan al estudiante, quien conserva la potestad de rechazarla o elegir otra de las recibidas.
