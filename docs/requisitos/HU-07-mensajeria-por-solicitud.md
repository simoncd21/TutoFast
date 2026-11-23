### HU-07: Mensajería contextual por solicitud

**Como** estudiante o tutor con una solicitud en negociación o confirmada
**Quiero** comunicarme a través de un chat interno contextual, intercambiar archivos permitidos y reportar mensajes indebidos
**Para** coordinar detalles pedagógicos y logísticos manteniendo un registro seguro y auditable ante cualquier controversia

**Validación INVEST**
- I — Independent: ✅ Puede desarrollarse como un componente de comunicación vinculado al identificador de la solicitud académica.
- N — Negotiable: ✅ Tipos de formatos permitidos, tamaño máximo de adjuntos y filtros automáticos de palabras obscenas o números telefónicos son negociables.
- V — Valuable: ✅ Previene el acoso, evita transacciones fuera de la plataforma (fugas desleales) y suministra evidencia inmutable para la resolución de disputas.
- E — Estimable: ✅ Especificación clara de envío, almacenamiento, validación de adjuntos y reporte de mensajes.
- S — Small: ✅ Alcance centrado en el intercambio de texto, adjuntos permitidos y flag de denuncia con registro de auditoría.
- T — Testable: ✅ Comprobable mediante envío exitoso de mensajes/PDFs, rechazo de ejecutables y conservación de trazas para el equipo de soporte.

**Criterios de aceptación (Gherkin)**
```gherkin
# language: es
Característica: Mensajería contextual por solicitud

  Escenario: Envío exitoso de mensaje y documento de estudio permitido
    Dado que el estudiante y el tutor tienen un servicio en estado "Servicio confirmado"
    Cuando el tutor envía el mensaje "Te comparto la guía de ejercicios resueltos" y adjunta el archivo "guia_ejercicios.pdf"
    Entonces el sistema entrega el mensaje y el archivo en el chat del estudiante
    Y almacena el registro del mensaje con marca temporal inmutable en la base de datos

  Escenario: Bloqueo de archivos con extensiones peligrosas o no autorizadas
    Dado que un usuario intenta compartir un archivo a través de la mensajería interna
    Cuando adjunta un archivo con extensión ejecutable como "script_calculo.exe" o "macro.bat"
    Entonces el sistema rechaza la carga del archivo
    Y muestra una advertencia indicando "Tipo de archivo no permitido. Solo se admiten formatos seguros (PDF, PNG, JPG, DOCX)"

  Escenario: Reporte de mensaje inapropiado o conducta abusiva
    Dado que un participante recibe un mensaje que contiene lenguaje ofensivo o solicitudes contrarias a las políticas
    Cuando el receptor pulsa el botón "Reportar mensaje" y selecciona el motivo "Conducta abusiva"
    Entonces el sistema marca el mensaje como reportado para auditoría inmediata del Administrador
    Y preserva la conversación íntegra impidiendo que el emisor pueda borrar o editar el mensaje denunciado
```

**Supuestos / preguntas abiertas**
- Los mensajes en el chat de una solicitud no pueden ser borrados ni modificados por los usuarios una vez enviados para garantizar la validez probatoria ante reclamos.
- El chat se habilita al momento de iniciar la negociación y permanece activo para consulta histórica una vez finalizado el servicio.
