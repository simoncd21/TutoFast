# TutoFast

Plataforma web universitaria de tutorías con negociación dinámica de precios (inspirada en inDrive).

- **Backend:** Java 21 + Spring Boot 3 + MongoDB Atlas (+ GridFS) → `backend/`
- **Frontend:** HTML5 + CSS3 + JavaScript ES6 puro → `frontend/`
- **Documentación:** `docs/` (arquitectura, decisiones, plan de implementación, API)

## Requisitos

| Herramienta | Versión | Notas |
|---|---|---|
| JDK | 21 o superior | `java -version`. El proyecto compila con `--release 21` |
| MongoDB | Atlas (recomendado) o local | Atlas M0 gratuito sirve. Ver `docs/guia-infraestructura-cero.md` |
| Python 3 **o** extensión Live Server de VS Code | cualquiera | Solo para servir el frontend |

Maven **no** hace falta instalarlo: se usa el Maven Wrapper (`mvnw` / `mvnw.cmd`).

## Backend en local

### 1. Variables de entorno

Copia `backend/.env.example` como referencia y define las variables en tu terminal. Spring **no** lee el archivo `.env` automáticamente.

| Variable | Obligatoria | Descripción |
|---|---|---|
| `MONGODB_URI` | Sí | Cadena `mongodb+srv://...` de Atlas (con la base `tutofast`) o `mongodb://localhost:27017/tutofast` |
| `JWT_SECRET` | Sí | Secreto de firma (≥ 32 caracteres) |
| `FRONTEND_URL` | No | Origen permitido por CORS (por defecto `http://localhost:5500`) |
| `PORT` | No | Puerto HTTP (por defecto `8080`) |
| `MONGO_TRANSACCIONES` | No | `true` en Atlas/Render; `false` (por defecto) con Mongo local sin replica set |

### 2. Windows PowerShell

```powershell
cd backend

$env:MONGODB_URI = "mongodb+srv://USUARIO:CLAVE@cluster0.xxxxx.mongodb.net/tutofast?retryWrites=true&w=majority"
$env:JWT_SECRET = "una-cadena-aleatoria-de-al-menos-32-caracteres"
$env:MONGO_TRANSACCIONES = "true"      # "false" si usas Mongo local

.\mvnw.cmd clean package -DskipTests   # genera target\tutofast-backend.jar
java -jar target\tutofast-backend.jar
```

Para desarrollar sin empaquetar: `.\mvnw.cmd spring-boot:run`.

### 3. macOS / Linux / Git Bash

```bash
cd backend
export MONGODB_URI="mongodb+srv://USUARIO:CLAVE@cluster0.xxxxx.mongodb.net/tutofast?retryWrites=true&w=majority"
export JWT_SECRET="una-cadena-aleatoria-de-al-menos-32-caracteres"
export MONGO_TRANSACCIONES=true
./mvnw clean package -DskipTests
java -jar target/tutofast-backend.jar
```

### 4. Probar

```powershell
Invoke-RestMethod http://localhost:8080/api/salud
# {"estado":"UP","baseDatos":"UP","servicio":"tutofast-backend"}
```

```bash
curl http://localhost:8080/api/salud
```

Si MongoDB no responde, devuelve HTTP 503 con `"baseDatos":"DOWN"`.

### 5. Pruebas

```powershell
.\mvnw.cmd test
```

### 6. Docker (el mismo que usa Render)

```powershell
cd backend
docker build -t tutofast-backend .
docker run --rm -p 8080:8080 -e MONGODB_URI="..." -e JWT_SECRET="..." tutofast-backend
```

## Frontend en local

El frontend usa módulos ES6, por lo que **debe servirse por HTTP** (abrir el archivo con doble clic no funciona). Usa el puerto **5500**, que es el origen permitido por CORS.

```powershell
cd frontend
python -m http.server 5500
```

Abre <http://localhost:5500>. Alternativa: en VS Code, clic derecho sobre `frontend/index.html` → *Open with Live Server* (puerto 5500).

- Con el backend en `http://localhost:8080`, la portada muestra "Servidor conectado".
- Botón 🌙 / ☀️ en la barra: alterna tema claro/oscuro y se recuerda en el navegador.
- La URL del backend se define en `frontend/js/config.js` (`URL_API_LOCAL` y `URL_API_PRODUCCION`).

## Estructura

```text
backend/    API Spring Boot (com.tutofast: config, seguridad, excepcion, controlador, servicio, repositorio, modelo, dto, mapper, integracion)
frontend/   Sitio estático (css/, js/config.js, js/servicios, js/componentes, js/paginas)
docs/       Arquitectura, ADR, plan de implementación, diagramas y requisitos
```

## Despliegue

GitHub → Render Web Service (Docker, carpeta `backend/`) + Render Static Site (carpeta `frontend/`). Guía completa en `docs/guia-infraestructura-cero.md`.
