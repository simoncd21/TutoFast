### HU-06: Seleccionar tutor y confirmar servicio

**Como** estudiante solicitante
**Quiero** evaluar las propuestas recibidas, seleccionar al tutor de mi preferencia y formalizar la confirmación bilateral del servicio
**Para** asegurar la reserva de la tutoría congelando inmutablemente el precio y las condiciones acordadas sin alteraciones unilaterales

**Validación INVEST**
- I — Independent: ✅ Depende de la existencia de ofertas (HU-05), pero su lógica de bloqueo, autorización de pago y congelamiento de condiciones es una unidad funcional autónoma.
- N — Negotiable: ✅ Mecanismos de aviso a las partes y políticas de cancelación antes del inicio del servicio son negociables.
- V — Valuable: ✅ Elimina la incertidumbre en las contrataciones de servicios académicos, protegiendo tanto al estudiante frente a sobrecostos imprevistos como al tutor frente a cancelaciones arbitrarias.
- E — Estimable: ✅ Transiciones de estado finitas y reglas de negocio estrictas sobre inmutabilidad de precios.
- S — Small: ✅ Delimitado al acto de selección, cambio de estado a "Servicio confirmado", descarte de ofertas competidoras y bloqueo de edición.
- T — Testable: ✅ Comprobable verificando el congelamiento del precio, la transición del estado a "Servicio confirmado" y el rechazo a cualquier intento de edición unilateral posterior.

**Criterios de aceptación (Gherkin)**
```gherkin
# language: es
Característica: Seleccionar tutor y confirmar servicio

  Escenario: Confirmación bilateral exitosa y congelamiento de condiciones
    Dado que el estudiante tiene una solicitud con una propuesta del tutor "Tutor Alfa" por "$30.000 COP" por 90 minutos y otra de "Tutor Beta" por "$25.000 COP" por 1 hora
    Cuando el estudiante selecciona la propuesta del "Tutor Alfa" y autoriza el método de pago
    Entonces el sistema actualiza el estado de la solicitud a "Servicio confirmado"
    Y congela inmutablemente el precio final en "$30.000 COP", la duración en 90 minutos y la modalidad acordada
    Y descarta automáticamente la propuesta de "Tutor Beta"
    Y habilita el canal de mensajería contextual y la programación de la sesión

  Escenario: Edición o cancelación permitida antes de existir confirmación bilateral
    Dado que la solicitud se encuentra en estado "Solicitud publicada" o "Propuestas recibidas"
    Cuando el estudiante decide modificar la fecha o cancelar la solicitud
    Entonces el sistema permite la edición o cancelación sin penalidades
    Y actualiza el estado a "Cancelado" en caso de cancelación voluntaria

  Escenario: Bloqueo de cancelación o modificación unilateral tras la confirmación
    Dado que la solicitud ya se encuentra en estado "Servicio confirmado"
    Cuando el estudiante o el tutor intentan modificar el precio pactado o cancelar unilateralmente sin causa justificada
    Entonces el sistema bloquea la modificación del precio
    Y notifica que cualquier cancelación posterior a la confirmación debe canalizarse mediante el módulo de reportes y soporte
```

**Supuestos / preguntas abiertas**
- Al momento de la confirmación bilateral, el sistema valida la autorización/bloqueo de fondos del método de pago del estudiante (tarjeta/débito digital) en custodia (escrow) preventiva.
- Una vez confirmado el servicio, los datos esenciales (precio, hora, lugar o enlace virtual) quedan blindados frente a modificaciones directas en la base de datos.
