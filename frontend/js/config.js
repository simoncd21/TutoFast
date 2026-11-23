// Configuración del frontend de TutoFast.
// Local: backend en http://localhost:8080.
// Producción: reemplaza URL_API_PRODUCCION por la URL del Web Service de Render
// (por ejemplo "https://tutofast-backend.onrender.com") y se usará automáticamente
// cuando la página no se sirva desde localhost.

const URL_API_LOCAL = 'http://localhost:8080';
const URL_API_PRODUCCION = 'https://tutofast-backend.onrender.com';

const esLocal = ['localhost', '127.0.0.1', ''].includes(window.location.hostname);

export const URL_API = esLocal ? URL_API_LOCAL : URL_API_PRODUCCION;
export const CLAVE_TOKEN = 'tutofast_token';
export const CLAVE_TEMA = 'tutofast_tema';
