### HU-11: Organización de la solución en tres capas

**Como** arquitecto de software y director técnico
**Quiero** que la solución esté organizada estrictamente en tres capas desacopladas: interfaz de usuario (móvil y web), lógica de aplicación y persistencia respaldada por PostgreSQL
**Para** garantizar la mantenibilidad, escalabilidad, seguridad de los datos e integridad transaccional ACID en todas las operaciones de UniDrive

**Validación INVEST**
- I — Independent: ✅ Representa la decisión directriz de arquitectura que guía el diseño modular del sistema de forma independiente a las pantallas de usuario.
- N — Negotiable: ✅ Patrones de diseño internos por capa (ej. repositorios, servicios de dominio, DTOs) son negociables con el equipo de desarrollo.
- V — Valuable: ✅ Asegura la calidad técnica a largo plazo, facilita pruebas unitarias y de integración, y previene acoplamientos rígidos entre componentes.
- E — Estimable: ✅ Requerimientos estructurales y contratos de interfaz medibles por módulo y capa.
- S — Small: ⚠️ Requiere subdivisión en tareas específicas por cada módulo de negocio (catálogos, solicitudes, negociaciones, pagos).
- T — Testable: ✅ Comprobable mediante pruebas de integración, verificación de contratos de API y validación de transaccionalidad relacional.

**Criterios de aceptación (Gherkin)**
```gherkin
# language: es
Característica: Organización de la solución en tres capas

  Escenario: Desacoplamiento estricto entre presentación, lógica y persistencia
    Dado que el equipo de desarrollo implementa el módulo de publicación de solicitudes y negociación inDrive
    Cuando se audita la arquitectura del código fuente del sistema
    Entonces la capa de interfaz de usuario (móvil y web) se comunica exclusivamente con la capa de lógica de aplicación mediante contratos de API definidos
    Y la capa de lógica de aplicación ejecuta las reglas de negocio de forma aislada y accede a la persistencia únicamente a través de repositorios e interfaces abstractas

  Escenario: Integridad transaccional ACID mediante PostgreSQL en operaciones críticas
    Dado que se ejecuta una confirmación bilateral de servicio con reserva de fondos y actualización de estados
    Cuando se procesa la transacción en la base de datos relacional PostgreSQL
    Entonces el motor garantiza la atomicidad, consistencia, aislamiento y durabilidad (ACID)
    Y asegura que si falla la reserva de pago, se revierte automáticamente el cambio de estado de la solicitud para evitar estados inconsistentes

  Escenario: Ejecución exitosa de pruebas de integración entre lógica y persistencia
    Dado que existen contratos y esquemas relacionales definidos para usuarios, solicitudes y transacciones
    Cuando se ejecutan las pruebas de integración automatizadas
    Entonces las pruebas verifican la correcta persistencia y recuperación de entidades complejas sin acoplar la lógica de negocio al motor específico de base de datos
```

**Supuestos / preguntas abiertas**
- La adopción de PostgreSQL como motor relacional se mantiene como propuesta técnica respaldada por los requerimientos no funcionales de integridad transaccional, sujeta a ratificación formal del comité técnico.
- Las aplicaciones cliente (móvil y web) consumen servicios web basados en REST/GraphQL expuestos por la capa de lógica de aplicación.
