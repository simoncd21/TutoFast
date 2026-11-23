### HU-01: Registro y perfiles de usuario

**Como** estudiante o tutor universitario
**Quiero** registrarme en la plataforma ingresando mis datos personales, aceptando la política de tratamiento de datos y aportando soportes en caso de requerir verificación
**Para** acceder a los servicios de tutoría con una identidad respaldada y protegida bajo el marco legal vigente

**Validación INVEST**
- I — Independent: ✅ Se puede implementar de manera autónoma como base del ecosistema de usuarios sin depender de la lógica de negociación o alquiler.
- N — Negotiable: ✅ Los campos específicos del formulario y los tipos de documentos admisibles para la verificación pueden ser ajustados por el equipo.
- V — Valuable: ✅ Otorga certeza legal (Ley Estatutaria 1581 de 2012 de Habeas Data) y genera confianza entre las partes mediante la distinción de perfiles verificados.
- E — Estimable: ✅ Flujos claros de alta, consentimiento expreso y validación de soportes para estimar el esfuerzo de desarrollo.
- S — Small: ✅ Alcance enfocado en el registro, diferenciación de roles (Estudiante, Tutor, Administrador) y auditoría documental básica.
- T — Testable: ✅ Verificable mediante escenarios de registro con/sin consentimiento, subida de certificados y cambio de estado a "Tutor Verificado".

**Criterios de aceptación (Gherkin)**
```gherkin
# language: es
Característica: Registro y perfiles de usuario

  Escenario: Registro exitoso de estudiante con aceptación de política de datos
    Dado que no existe una cuenta asociada al correo institucional "estudiante@universidad.edu.co"
    Cuando el usuario completa el registro con nombre "Carlos Gómez", documento "1098765432", rol "Estudiante solicitante" y marca la casilla de aceptación de la política de tratamiento de datos Ley 1581 de 2012
    Entonces el sistema crea la cuenta de usuario para "estudiante@universidad.edu.co"
    Y registra la fecha y hora de la autorización expresa del tratamiento de datos personales
    Y muestra el mensaje "Registro exitoso en UniDrive"

  Escenario: Rechazo de registro por falta de consentimiento de Habeas Data
    Dado que un visitante intenta registrarse con documento "1098765432" y correo "estudiante@universidad.edu.co"
    Cuando envía el formulario sin marcar la aceptación de la política de protección de datos personales
    Entonces el sistema rechaza la creación de la cuenta
    Y muestra una alerta indicando "Debe aceptar la política de tratamiento de datos conforme a la Ley 1581 de 2012 para continuar"

  Escenario: Registro de tutor con información declarada y solicitud de verificación
    Dado que un docente o monitor se registra con rol "Tutor"
    Cuando completa su información declarada de asignaturas de dominio y adjunta su "Certificado de Monitoría Académica 2026.pdf"
    Entonces el sistema crea el perfil con la etiqueta "Tutor Registrado (No Verificado)"
    Y genera un registro pendiente de auditoría documental para el Administrador

  Escenario: Administrador aprueba soportes y otorga distintivo verificado
    Dado que el tutor "tutor@universidad.edu.co" tiene soportes institucionales en revisión
    Cuando el Administrador valida la autenticidad del certificado de monitoría y confirma la aprobación
    Entonces el perfil del tutor se actualiza con la distinción pública "Tutor Verificado"
    Y se habilita el sello de garantía académica en su perfil público
```

**Supuestos / preguntas abiertas**
- Se asume que el correo institucional con dominio universitario (`.edu.co`) otorga prioridad en el proceso de revisión, pero no bloquea el uso de correos personales válidos.
- La auditoría documental es realizada por un Administrador del sistema a través de un panel de verificación interna.
