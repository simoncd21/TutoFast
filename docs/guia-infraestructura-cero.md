# Guía de infraestructura desde cero — GitHub · MongoDB Atlas · Render

> Objetivo: dejar TutoFast desplegado con el **backend** en Render Web Service (Docker), el **frontend** en Render Static Site y los datos en **MongoDB Atlas** (base de datos `tutofast`). Todo con planes gratuitos.
> Las pantallas de estos servicios cambian con frecuencia; si un botón tiene otro nombre, busca la opción equivalente.

Lista de lo que vas a obtener (anótalo en un lugar seguro, **nunca** en el repositorio):

| Dato | Ejemplo |
|---|---|
| URL del repositorio | `https://github.com/<tu-usuario>/tutofast` |
| `MONGODB_URI` | `mongodb+srv://tutofast_app:<clave>@cluster0.abcde.mongodb.net/tutofast?retryWrites=true&w=majority&appName=Cluster0` |
| `JWT_SECRET` | 64 caracteres aleatorios |
| URL del backend | `https://tutofast-backend.onrender.com` |
| URL del frontend | `https://tutofast.onrender.com` |

---

## Parte 1 · GitHub

### 1.1 Crear la cuenta
1. Ve a <https://github.com/signup> y crea tu cuenta (verifica el correo).
2. Recomendado: activa la autenticación en dos pasos en *Settings → Password and authentication*.

### 1.2 Configurar Git en tu computador (una sola vez)
En Git Bash o PowerShell:
```bash
git --version                                # si no existe, instala Git desde https://git-scm.com
git config --global user.name  "Tu Nombre"
git config --global user.email "tu-correo@ejemplo.com"
git config --global init.defaultBranch main
```

### 1.3 Crear el repositorio remoto
1. En GitHub: **+ → New repository**.
2. Nombre: `tutofast`. Visibilidad: *Private* o *Public* (Render funciona con ambas).
3. **No** marques "Add a README", ".gitignore" ni "license": el proyecto local ya tiene archivos.
4. Pulsa **Create repository** y copia la URL HTTPS (`https://github.com/<tu-usuario>/tutofast.git`).

### 1.4 Conectar la carpeta local y subir `main`
Desde la carpeta del proyecto (`Proyecto_Tutorias_inDrive`):
```bash
git status                                   # el repositorio local ya existe (rama main)
git add CLAUDE.md docs/
git commit -m "docs: arquitectura y requisitos de TutoFast"
git remote add origin https://github.com/<tu-usuario>/tutofast.git
git push -u origin main
```
- Cuando Git pida credenciales, usa el inicio de sesión del navegador (Git Credential Manager) o un **Personal Access Token** (*Settings → Developer settings → Personal access tokens → Fine-grained*, con permiso *Contents: Read and write* sobre el repositorio).
- No subas `.env` ni archivos con claves: el `.gitignore` del módulo 01 los excluye.

### 1.5 Flujo de ramas `feature/...` → `main`
Para cada uno de los 10 módulos (ver `docs/plan-implementacion.md`):
```bash
git checkout main
git pull
git checkout -b feature/01-base-proyecto
# ... trabajar y probar ...
git add .
git commit -m "feat(01): base del proyecto y endpoint de salud"
git push -u origin feature/01-base-proyecto
```
1. En GitHub aparecerá **Compare & pull request** → crea el PR hacia `main`, revisa los cambios y pulsa **Merge pull request**.
2. En local:
   ```bash
   git checkout main
   git pull
   git branch -d feature/01-base-proyecto
   ```
3. Recomendado: *Settings → Branches → Add rule* para `main` con "Require a pull request before merging", así nadie sube directo a `main`.
4. Como Render despliega automáticamente cada cambio en `main`, cada merge produce un despliegue nuevo.

---

## Parte 2 · MongoDB Atlas

### 2.1 Crear la cuenta y el proyecto
1. Ve a <https://www.mongodb.com/cloud/atlas/register> y regístrate (puedes usar Google).
2. Crea una **Organization** (si te la pide) y un **Project** llamado `TutoFast`.

### 2.2 Crear el clúster gratuito M0
1. **Create** (o *Build a Database*) → elige **M0 Free**.
2. Proveedor: AWS. Región: la más cercana con M0 disponible (por ejemplo `São Paulo (sa-east-1)` o `N. Virginia (us-east-1)`). Render en Oregon u Ohio funciona bien con `us-east-1`.
3. Nombre del clúster: `Cluster0` (o `tutofast`). Pulsa **Create Deployment**.
4. M0 es un *replica set* de 3 nodos, así que admite las **transacciones** que usa TutoFast (`MONGO_TRANSACCIONES=true`).

### 2.3 Crear el usuario de la base de datos
1. Menú **Security → Database Access → Add New Database User**.
2. Método: *Password*. Usuario: `tutofast_app`. Pulsa **Autogenerate Secure Password** y **cópiala**.
   - Si eliges tu propia clave, evita `@ : / ? # [ ] %` o codifícalos en la URI (por ejemplo, `@` → `%40`).
3. Privilegios: *Built-in Role → Read and write to any database* (o, más restrictivo: *Specific privileges → readWrite* sobre `tutofast`).
4. **Add User**.

### 2.4 Habilitar el acceso de red (Network Access)
1. Menú **Security → Network Access → Add IP Address**.
2. Pulsa **Allow Access from Anywhere** → `0.0.0.0/0` y confirma.
   - Es necesario porque el plan gratuito de Render no tiene IPs de salida fijas. La protección real son el usuario y la contraseña (y TLS, que siempre está activo).
3. Opcional para desarrollo: agrega también tu IP actual (**Add Current IP Address**).

### 2.5 Obtener `MONGODB_URI` apuntando a `tutofast`
1. **Database → Clusters → Connect → Drivers**. Driver: *Java*, versión reciente.
2. Copia la cadena:
   ```
   mongodb+srv://tutofast_app:<db_password>@cluster0.abcde.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0
   ```
3. Reemplaza `<db_password>` por la clave del paso 2.3 e **inserta el nombre de la base de datos `tutofast`** entre `.net/` y `?`:
   ```
   mongodb+srv://tutofast_app:MiClaveSegura123@cluster0.abcde.mongodb.net/tutofast?retryWrites=true&w=majority&appName=Cluster0
   ```
4. No hace falta crear la base de datos a mano: el backend crea `tutofast`, las colecciones, los índices y el bucket GridFS `archivos` al arrancar.
5. Para verificar después del primer arranque: **Browse Collections** → base de datos `tutofast` → colecciones `usuarios`, `asignaturas`, `archivos.files`, etc.

> El puerto es 27017 con TLS; `mongodb+srv` lo resuelve automáticamente por DNS.

---

## Parte 3 · Secretos
Genera `JWT_SECRET` (mínimo 32 bytes):
```bash
# Git Bash
openssl rand -base64 48
```
```powershell
# PowerShell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
```
Define también `ADMIN_CORREO` (por ejemplo `admin@tutofast.co`) y una `ADMIN_CLAVE` robusta para el administrador inicial.

---

## Parte 4 · Render

### 4.1 Crear la cuenta y conectar GitHub
1. Ve a <https://render.com> → **Get Started** → **Sign up with GitHub** (es lo más sencillo).
2. Autoriza la aplicación de Render en GitHub y, en *Repository access*, selecciona **Only select repositories → tutofast**. Se puede cambiar después en GitHub: *Settings → Applications → Render → Configure*.

> Requisito previo: el módulo 01 debe estar en `main` (`backend/Dockerfile`, `frontend/index.html` y `GET /api/salud`). Antes de eso no hay nada que desplegar.

### 4.2 Desplegar el backend (Web Service · Docker)
1. Dashboard → **New + → Web Service** → **Build and deploy from a Git repository** → elige `tutofast`.
2. Configura:

| Campo | Valor |
|---|---|
| Name | `tutofast-backend` (define la URL `https://tutofast-backend.onrender.com`) |
| Region | la más cercana a tu clúster de Atlas (p. ej. *Ohio* o *Virginia* si Atlas está en `us-east-1`) |
| Branch | `main` |
| **Root Directory** | `backend` |
| **Runtime / Language** | **Docker** |
| Dockerfile Path | `./Dockerfile` (relativo a `backend`) |
| Instance Type | **Free** |

3. **Environment Variables** (Add Environment Variable):

| Clave | Valor |
|---|---|
| `MONGODB_URI` | la URI del paso 2.5 (con `/tutofast`) |
| `JWT_SECRET` | el secreto de la parte 3 |
| `PORT` | `8080` |
| `FRONTEND_URL` | provisionalmente `http://localhost:5500`; se actualiza en el paso 4.4 |
| `MONGO_TRANSACCIONES` | `true` |
| `JWT_EXPIRACION_HORAS` | `8` |
| `ADMIN_CORREO` | `admin@tutofast.co` |
| `ADMIN_CLAVE` | tu clave de administrador |

4. **Advanced → Health Check Path:** `/api/salud`. Auto-Deploy: *Yes*.
5. **Create Web Service**. La primera compilación de Docker tarda unos 5–10 min; sigue el progreso en **Logs**. Debe aparecer `Started TutoFastApplication`.
6. Prueba:
   ```bash
   curl https://tutofast-backend.onrender.com/api/salud
   # {"estado":"UP","servicio":"tutofast-backend","version":"1.0.0","baseDatos":"UP","fecha":"..."}
   ```
   Si `baseDatos` es `DOWN`, revisa la URI, la contraseña y el Network Access `0.0.0.0/0`.

> Plan gratuito: el servicio se duerme tras unos 15 min sin tráfico; la primera petición después tarda unos 30–60 s. Antes de una presentación, abre `/api/salud` un minuto antes.

### 4.3 Desplegar el frontend (Static Site)
1. Dashboard → **New + → Static Site** → repositorio `tutofast`.
2. Configura:

| Campo | Valor |
|---|---|
| Name | `tutofast` (define `https://tutofast.onrender.com`) |
| Branch | `main` |
| Root Directory | *(vacío)* |
| **Build Command** | *(vacío)*. Si el formulario lo exige, usa `echo "sin build"` |
| **Publish Directory** | `frontend` |

3. **Create Static Site**. Tarda menos de 1 min.
4. Opcional: en *Redirects/Rewrites* no hace falta nada; cada página es un `.html` real.

### 4.4 Conectar frontend y backend
1. **Backend → frontend (CORS):** en el Web Service `tutofast-backend` → **Environment** → cambia `FRONTEND_URL` a `https://tutofast.onrender.com` (sin `/` final) → **Save Changes** (Render vuelve a desplegar).
2. **Frontend → backend:** edita `frontend/js/config.js` en una rama:
   ```js
   const URL_PRODUCCION = 'https://tutofast-backend.onrender.com';
   const URL_LOCAL = 'http://localhost:8080';
   export const API_URL = ['localhost', '127.0.0.1'].includes(location.hostname) ? URL_LOCAL : URL_PRODUCCION;
   ```
   ```bash
   git checkout -b feature/config-produccion
   git commit -am "chore: URL del backend de producción"
   git push -u origin feature/config-produccion   # PR → merge a main
   ```
   Render vuelve a publicar el Static Site automáticamente.

### 4.5 Probar de punta a punta
1. `https://tutofast-backend.onrender.com/api/salud` → `UP`.
2. Abre `https://tutofast.onrender.com`, abre las DevTools (F12) → pestaña *Network*.
3. Regístrate como estudiante → debe aparecer "Registro exitoso en TutoFast" y la petición `POST /api/auth/registro` debe responder 201, sin errores de CORS en la consola.
4. Inicia sesión con `ADMIN_CORREO` / `ADMIN_CLAVE` → `admin.html`.
5. En Atlas → *Browse Collections* → `tutofast.usuarios` muestra los usuarios creados.

---

## Parte 5 · Ejecución local (opcional, para desarrollar)
```powershell
# PowerShell, desde backend/
$env:MONGODB_URI = "mongodb+srv://tutofast_app:...@cluster0.abcde.mongodb.net/tutofast?retryWrites=true&w=majority"
$env:JWT_SECRET = "<secreto>"
$env:FRONTEND_URL = "http://localhost:5500"
$env:MONGO_TRANSACCIONES = "true"      # "false" si usas un Mongo local sin replica set
.\mvnw.cmd spring-boot:run
```
```bash
# Otra terminal, desde frontend/
python -m http.server 5500             # abre http://localhost:5500
```
Spring no lee `.env` por sí solo; `.env.example` solo documenta las variables.

---

## Parte 6 · Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| `MongoTimeoutException` / `baseDatos: DOWN` | IP no permitida o URI incorrecta | Network Access `0.0.0.0/0`; revisa usuario, clave y `/tutofast` |
| `bad auth : authentication failed` | Clave mal escrita o con caracteres especiales sin codificar | Regenera la clave o codifícala en URL |
| Error de CORS en el navegador | `FRONTEND_URL` distinto del origen real | Igual a `https://tutofast.onrender.com`, sin barra final |
| `Transaction numbers are only allowed on a replica set` | Mongo local standalone con transacciones activas | `MONGO_TRANSACCIONES=false` en local |
| El build de Render falla con `mvnw: Permission denied` | El bit de ejecución de `mvnw` no se guardó en Git | `git update-index --chmod=+x backend/mvnw` y commit |
| La primera petición tarda unos 50 s | El servicio gratuito estaba dormido | Normal; precalienta con `/api/salud` |
| 401 en todas las rutas tras desplegar | Cambió `JWT_SECRET` | Cierra sesión y vuelve a iniciarla |
