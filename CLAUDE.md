# TutoFast — Manual Técnico y Reglas Permanentes del Proyecto

## 1. Identidad, alcance y jerarquía de documentos
Estamos construyendo **TutoFast**, una plataforma web universitaria de tutorías con negociación dinámica de precios inspirada en inDrive.

### Reglas de fuente de verdad
1. **Rebranding absoluto a TutoFast:** En los diagramas y documentos dentro de `docs/` figura el nombre anterior "UniDrive" / "UniDrive Tutorías" / `unidrive-backend.jar` / base de datos `unidrive`. Debes reemplazar el 100 % de esas referencias por **TutoFast** (`tutofast`, paquete `com.tutofast`, `tutofast-backend.jar`, base de datos `tutofast`, mensajes "Registro exitoso en TutoFast"). No debe quedar la palabra "UniDrive" en ningún archivo de código ni en la interfaz.
2. **Los 5 diagramas tienen mayor peso que los requisitos previos:** Si existe discrepancia entre `requerimientos.txt` / `HU-*.md` y los diagramas en `docs/diagramas/`, prevalecen siempre los diagramas.
3. **Resolución definitiva de discrepancias:**
   - **Base de datos:** Se usa **MongoDB Atlas** (NO PostgreSQL de R16/HU-11).
   - **Frontend:** Se usa **HTML5 + CSS3 + JavaScript puro (ES6)** sin frameworks (NO React, NO TypeScript, NO Tailwind, NO Node build), desplegado en **Render Static Site**.
   - **Archivos:** Se usa **MongoDB GridFS** dentro de MongoDB Atlas (PDF, PNG, JPG, DOCX ≤ 10 MB). No usar Cloudinary ni S3.
   - **Pagos:** **Simulador de pasarela** interno en el backend (sin almacenar datos de tarjeta).
   - **Notificaciones:** Guardadas únicamente en una **colección de MongoDB** y consultadas en la web (sin envío de correos reales ni SMTP/Resend).
   - **Chat:** API REST con polling periódico (sin WebSockets).
   - **Documentación API:** Solo Markdown en `docs/arquitectura/diseno-api.md` (no agregar Swagger/OpenAPI).
   - **Ignorar `SKILL.md`** si existiera en alguna carpeta.

---

## 2. Stack tecnológico exacto y minimalista
- **Backend:** Java 21 + Spring Boot 3.x + Maven Wrapper (`./mvnw`) + Spring Web + Spring Data MongoDB + Spring Validation + `spring-security-crypto` (BCrypt) + JWT liviano (`jjwt`). Empaquetado como `tutofast-backend.jar`.
- **Contenedor para Render Web Service:** `backend/Dockerfile` multi-stage sencillo (`maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre-alpine`) respetando `${PORT:8080}`.
- **Base de datos:** MongoDB Atlas (`mongodb+srv` + TLS 27017) + GridFS.
- **Frontend:** HTML5 semántico + CSS3 con variables nativas + JavaScript ES6 modular (`type="module"` + `fetch`).
- **Autenticación sencilla y funcional:** Contraseñas con `BCryptPasswordEncoder` + token JWT firmado con `JWT_SECRET` enviado en cabecera `Authorization: Bearer <token>` mediante un filtro HTTP liviano (`FiltroAutenticacionJwt`) que inyecta el usuario autenticado y valida roles (`ESTUDIANTE`, `TUTOR`, `ADMINISTRADOR`) sin sobrecargar el proyecto con flujos OAuth complejos.
- **Despliegue:** GitHub → Render Web Service (`backend/`) + Render Static Site (`frontend/`).

---

## 3. Arquitectura por capas y nomenclatura exacta en español

### Backend (`backend/src/main/java/com/tutofast/`)
```text
com.tutofast
├── TutoFastApplication.java
├── config/          (CorsConfig, GridFsConfig, DatosInicialesSeed, ProgramadorTareasConfig)
├── seguridad/       (JwtUtil, FiltroAutenticacionJwt, UsuarioAutenticado, SeguridadConfig)
├── excepcion/       (ExcepcionNegocio, ExcepcionNoEncontrado, ExcepcionAutorizacion, ManejadorGlobalExcepciones)
├── controlador/     (Controladores REST; CERO lógica de negocio aquí)
├── servicio/        (Los 11 servicios exactos de la Vista de Desarrollo)
├── repositorio/     (Interfaces Spring Data MongoRepository)
├── modelo/          (Las 22 clases exactas del Diagrama de Clases + enums de dominio)
├── dto/             (Records/clases de entrada y salida de la API)
├── mapper/          (Conversores DTO ↔ Modelo)
└── integracion/
    ├── puertos/     (IPasarelaPago, INotificador, IAlmacenamiento)
    └── adaptadores/ (AdaptadorPasarela, AdaptadorNotificaciones, AdaptadorAlmacenamiento)