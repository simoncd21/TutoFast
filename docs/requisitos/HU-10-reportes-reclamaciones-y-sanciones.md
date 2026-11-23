### HU-10: Reportes, reclamaciones y sanciones

**Como** estudiante o tutor afectado por una irregularidad
**Quiero** interponer un reporte o reclamación tipificada por inasistencia, cobro indebido, fraude, acoso o credenciales falsas
**Para** activar los protocolos de investigación, congelamiento preventivo de fondos y aplicación del debido proceso con derecho a apelación

**Validación INVEST**
- I — Independent: ✅ Opera transversalmente sobre cualquier solicitud o servicio, constituyendo el mecanismo de salvaguarda y soporte de la plataforma.
- N — Negotiable: ✅ Tipos de sanciones temporales/definitivas y plazos procesales de respuesta son negociables con el área jurídica y de operaciones.
- V — Valuable: ✅ Protege la seguridad física y financiera de la comunidad universitaria, mitigando fraudes y conductas abusivas con garantías procesales.
- E — Estimable: ✅ Tipificación clara de incidentes (incumplimiento, abuso, desacuerdo académico) y flujos de revisión delimitados.
- S — Small: ✅ Acotado al registro del reporte, cambio de estado a "En revisión", suspensión cautelar y emisión de resolución con apelación.
- T — Testable: ✅ Comprobable mediante radicación de reporte por inasistencia, congelamiento de fondos, revisión de chat inmutable y emisión de sanción apelable.

**Criterios de aceptación (Gherkin)**
```gherkin
# language: es
Característica: Reportes, reclamaciones y sanciones

  Escenario: Radicación de reporte por inasistencia injustificada del tutor
    Dado que el tutor no se presentó a la sesión programada del "Servicio confirmado" tras transcurrir 20 minutos de tolerancia
    Cuando el estudiante reporta la incidencia seleccionando el motivo "Inasistencia del tutor" y adjunta captura de pantalla
    Entonces el sistema cambia el estado del servicio a "En revisión por un reporte o una disputa"
    Y congela de forma preventiva la liquidación de los fondos en custodia
    Y notifica al equipo de soporte y al tutor sobre la reclamación abierta

  Escenario: Investigación de disputa basada en el historial inmutable del chat y resolución
    Dado que un servicio se encuentra en estado "En revisión" debido a un desacuerdo sobre el cumplimiento del precio o temática
    Cuando el Administrador revisa el historial de la mensajería interna contextual y constata el incumplimiento del tutor
    Entonces el Administrador emite una resolución a favor del estudiante ordenando el reembolso total del valor retenido
    Y aplica una sanción temporal al perfil del tutor infractor

  Escenario: Presentación de recurso de apelación por parte del usuario sancionado
    Dado que un usuario recibió una notificación de sanción temporal por un reporte
    Cuando el usuario presenta un descargo y un recurso de apelación adjuntando nuevas evidencias dentro del plazo establecido
    Entonces el sistema reabre el caso bajo el estado "En apelación pendiente"
    Y asigna el expediente a un comité de revisión superior para ratificar o revocar la sanción
```

**Supuestos / preguntas abiertas**
- Los fondos objeto de disputa permanecen retenidos y congelados en la cuenta de custodia de la plataforma hasta que el comité de soporte emita un fallo definitivo.
- Las conductas reincidentes de fraude académico o acoso conllevan la inhabilitación definitiva (bloqueo permanente) de la cuenta del usuario infractor.
