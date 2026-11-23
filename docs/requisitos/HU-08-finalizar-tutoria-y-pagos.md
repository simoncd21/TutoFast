### HU-08: Finalizar tutoría y liquidación de pagos

**Como** estudiante solicitante
**Quiero** confirmar la finalización de la sesión de tutoría para que el sistema calcule de forma transparente la comisión de la plataforma y el pago neto al tutor
**Para** asegurar una liquidación justa y la liberación de honorarios una vez recibido satisfactoriamente el servicio

**Validación INVEST**
- I — Independent: ✅ Se ejecuta al término de la sesión (tras HU-06 y HU-07), encapsulando la lógica de cierre transaccional y pasarela de pagos.
- N — Negotiable: ✅ Tiempos de gracia para cierre automático y pasarelas de pago externas son negociables.
- V — Valuable: ✅ Cierra el ciclo comercial del servicio garantizando confianza mediante la retención justa y liquidación transparente para ambas partes.
- E — Estimable: ✅ Reglas aritméticas claras (ej. 10% sobre $40.000 COP) y transiciones de estado delimitadas.
- S — Small: ✅ Acotado al evento de confirmación de fin por parte del estudiante, cálculo de retención y liberación de fondos.
- T — Testable: ✅ Comprobable mediante escenarios de finalización exitosa, cálculo de comisión e intento de doble finalización.

**Criterios de aceptación (Gherkin)**
```gherkin
# language: es
Característica: Finalizar tutoría y liquidación de pagos

  Escenario: Finalización exitosa confirmada por el estudiante con cálculo transparente de comisión
    Dado que el servicio de tutoría se encuentra en estado "Tutoría en desarrollo" con un precio acordado de "$40.000 COP" y una comisión de plataforma configurada al "10%"
    Cuando el estudiante confirma la finalización exitosa de la sesión en la plataforma
    Entonces el sistema actualiza el estado de la solicitud a "Servicio finalizado"
    Y calcula la retención de la comisión de la plataforma por valor de "$4.000 COP" (10% de $40.000)
    Y calcula el pago neto a favor del tutor por valor de "$36.000 COP"
    Y genera la orden de transferencia de fondos hacia la cuenta bancaria registrada del tutor

  Escenario: Cierre automático por inactividad tras transcurrir el plazo límite sin objeciones
    Dado que una tutoría se encuentra en estado "Tutoría en desarrollo" y han transcurrido más de 24 horas desde la hora programada de finalización
    Y el estudiante no ha confirmado la finalización ni ha reportado ninguna incidencia o disputa
    Cuando se ejecuta el proceso automático de cierre temporal del sistema
    Entonces el sistema cambia el estado del servicio a "Servicio finalizado" de forma automática
    Y procede a liquidar el pago neto al tutor aplicando la comisión correspondiente

  Escenario: Intento de finalización de un servicio que ya fue finalizado o cancelado
    Dado que el servicio ya se encuentra en estado "Servicio finalizado"
    Cuando el estudiante o el tutor intentan ejecutar nuevamente la acción de finalizar
    Entonces el sistema rechaza la solicitud
    Y muestra una advertencia indicando "El servicio ya se encuentra finalizado y no requiere nuevas acciones"
```

**Supuestos / preguntas abiertas**
- Se asume que el desembolso al tutor se ejecuta de forma automatizada a través de la pasarela de pagos integrada una vez finalizado y liquidado el saldo neto.
- Si surge una reclamación dentro de las primeras 24 horas, el sistema frena la liquidación automática trasladando la solicitud a estado de revisión.
