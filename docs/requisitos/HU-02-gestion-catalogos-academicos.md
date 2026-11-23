### HU-02: Gestión de catálogos académicos y comisión de la plataforma

**Como** administrador del sistema
**Quiero** administrar los catálogos de universidades, facultades, programas académicos y asignaturas, y configurar el porcentaje de comisión de la plataforma
**Para** garantizar una oferta académica estandarizada y asegurar la sostenibilidad financiera de las operaciones

**Validación INVEST**
- I — Independent: ✅ Puede desarrollarse de manera independiente a las transacciones de tutoría, estableciendo la estructura sobre la cual se publican solicitudes.
- N — Negotiable: ✅ La estructura de jerarquías académicas y los límites porcentuales de la comisión son parametrizables y acordados con el negocio.
- V — Valuable: ✅ Evita duplicidades y desorden temático en las solicitudes, y define la regla económica base para el cálculo de ingresos de UniDrive.
- E — Estimable: ✅ Requerimientos CRUD concretos y reglas claras sobre porcentajes numéricos facilitan la estimación.
- S — Small: ✅ Alcance delimitado a la administración y consulta de catálogos y parámetros globales de monetización.
- T — Testable: ✅ Comprobable mediante creación de asignaturas, actualización de comisión y bloqueo de registros duplicados.

**Criterios de aceptación (Gherkin)**
```gherkin
# language: es
Característica: Gestión de catálogos académicos y comisión de la plataforma

  Escenario: Registro exitoso de una nueva asignatura en el catálogo
    Dado que el usuario autenticado tiene el rol "Administrador"
    Y existe la universidad "Universidad Nacional" con la facultad "Ingeniería" y programa "Ingeniería de Sistemas"
    Cuando el Administrador crea la asignatura con código "IS-204" y nombre "Ecuaciones Diferenciales"
    Entonces el sistema registra la asignatura vinculada a "Ingeniería de Sistemas"
    Y la asignatura queda habilitada para que los estudiantes publiquen solicitudes de tutoría

  Escenario: Prevención de asignaturas duplicadas dentro del mismo programa
    Dado que en el programa "Ingeniería de Sistemas" ya existe la asignatura con código "IS-204"
    Cuando el Administrador intenta registrar otra asignatura con el mismo código "IS-204" para ese programa
    Entonces el sistema rechaza la operación
    Y muestra el mensaje de error "La asignatura con este código ya se encuentra registrada en el programa"

  Escenario: Actualización de la tasa de comisión global de la plataforma
    Dado que la tasa de comisión actual de la plataforma está configurada en 10%
    Cuando el Administrador modifica el parámetro de comisión al 12%
    Entonces el sistema almacena el nuevo porcentaje con fecha y usuario de auditoría
    Y las nuevas confirmaciones de servicio aplicarán la tasa del 12% sin alterar servicios previamente confirmados
```

**Supuestos / preguntas abiertas**
- Se asume que el porcentaje de comisión por defecto es del 10% y que cualquier cambio aplica únicamente a servicios confirmados con posterioridad a la modificación.
- La eliminación lógica o desactivación de asignaturas no afectará las solicitudes históricas o activas que ya la referencien.
