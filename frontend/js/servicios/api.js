// Cliente HTTP centralizado: todas las llamadas al backend pasan por aquí.
import { URL_API, CLAVE_TOKEN } from '../config.js';

export class ErrorApi extends Error {
  constructor(estado, codigo, mensaje, detalles = []) {
    super(mensaje);
    this.estado = estado;
    this.codigo = codigo;
    this.detalles = detalles;
  }
}

export const obtenerToken = () => localStorage.getItem(CLAVE_TOKEN);
export const guardarToken = (token) => localStorage.setItem(CLAVE_TOKEN, token);
export const borrarToken = () => localStorage.removeItem(CLAVE_TOKEN);

async function solicitar(metodo, ruta, cuerpo) {
  const cabeceras = {};
  const token = obtenerToken();
  if (token) cabeceras['Authorization'] = `Bearer ${token}`;

  const opciones = { method: metodo, headers: cabeceras };
  if (cuerpo instanceof FormData) {
    opciones.body = cuerpo; // el navegador fija el multipart/form-data con su boundary
  } else if (cuerpo !== undefined) {
    cabeceras['Content-Type'] = 'application/json';
    opciones.body = JSON.stringify(cuerpo);
  }

  let respuesta;
  try {
    respuesta = await fetch(`${URL_API}${ruta}`, opciones);
  } catch {
    throw new ErrorApi(0, 'SIN_CONEXION', 'No se pudo conectar con el servidor. Inténtalo de nuevo.');
  }

  const texto = await respuesta.text();
  let datos = null;
  if (texto) {
    try { datos = JSON.parse(texto); } catch { datos = null; }
  }

  if (!respuesta.ok) {
    throw new ErrorApi(
      respuesta.status,
      datos?.codigo ?? 'ERROR',
      datos?.mensaje ?? `Error ${respuesta.status}`,
      datos?.detalles ?? [],
    );
  }
  return datos;
}

export const api = {
  get: (ruta) => solicitar('GET', ruta),
  post: (ruta, cuerpo) => solicitar('POST', ruta, cuerpo ?? {}),
  put: (ruta, cuerpo) => solicitar('PUT', ruta, cuerpo ?? {}),
  patch: (ruta, cuerpo) => solicitar('PATCH', ruta, cuerpo ?? {}),
  eliminar: (ruta) => solicitar('DELETE', ruta),
};
