// Sesión del navegador: token JWT, usuario autenticado, rol activo y cierre de sesión.
import { CLAVE_TOKEN } from './config.js';
import { api, guardarToken, borrarToken, obtenerToken, ErrorApi } from './servicios/api.js';

const CLAVE_USUARIO = 'tutofast_usuario';
const CLAVE_EXPIRA = 'tutofast_expira';
const CLAVE_ROL = 'tutofast_rol_activo';

export const ORDEN_ROLES = ['ADMINISTRADOR', 'TUTOR', 'ESTUDIANTE'];
export const ETIQUETA_ROL = {
  ESTUDIANTE: 'Estudiante',
  TUTOR: 'Tutor',
  ADMINISTRADOR: 'Administrador',
};

function leerJson(clave) {
  try { return JSON.parse(localStorage.getItem(clave)); } catch { return null; }
}

export function obtenerUsuario() {
  return leerJson(CLAVE_USUARIO);
}

export function estaAutenticado() {
  const token = localStorage.getItem(CLAVE_TOKEN);
  if (!token || !obtenerUsuario()) return false;
  const expira = Date.parse(localStorage.getItem(CLAVE_EXPIRA) ?? '');
  return Number.isNaN(expira) || expira > Date.now();
}

/** Guarda la respuesta de `POST /api/auth/iniciar-sesion` ({token, expiraEn, usuario}). */
export function guardarSesion(sesion) {
  guardarToken(sesion.token);
  localStorage.setItem(CLAVE_EXPIRA, sesion.expiraEn ?? '');
  guardarUsuario(sesion.usuario);
  localStorage.removeItem(CLAVE_ROL);
  fijarRolPorDefecto(sesion.usuario);
}

function guardarUsuario(usuario) {
  localStorage.setItem(CLAVE_USUARIO, JSON.stringify(usuario));
}

function fijarRolPorDefecto(usuario) {
  const roles = usuario?.roles ?? [];
  const guardado = localStorage.getItem(CLAVE_ROL);
  if (guardado && roles.includes(guardado)) return;
  const preferido = ORDEN_ROLES.slice().reverse().find((r) => roles.includes(r)) ?? roles[0];
  if (preferido) localStorage.setItem(CLAVE_ROL, preferido);
  else localStorage.removeItem(CLAVE_ROL);
}

export function rolesDisponibles() {
  return obtenerUsuario()?.roles ?? [];
}

export function rolActivo() {
  const roles = rolesDisponibles();
  const guardado = localStorage.getItem(CLAVE_ROL);
  if (guardado && roles.includes(guardado)) return guardado;
  fijarRolPorDefecto(obtenerUsuario());
  return localStorage.getItem(CLAVE_ROL);
}

export function cambiarRolActivo(rol) {
  if (!rolesDisponibles().includes(rol)) return false;
  localStorage.setItem(CLAVE_ROL, rol);
  document.dispatchEvent(new CustomEvent('tutofast:rol', { detail: { rol } }));
  return true;
}

export function cerrarSesion() {
  borrarToken();
  [CLAVE_USUARIO, CLAVE_EXPIRA, CLAVE_ROL].forEach((c) => localStorage.removeItem(c));
}

export function irAAcceso(motivo) {
  const actual = window.location.pathname.split('/').pop() || 'index.html';
  const siguiente = encodeURIComponent(actual + window.location.search);
  const extra = motivo ? `&motivo=${motivo}` : '';
  window.location.replace(`acceso.html?siguiente=${siguiente}${extra}`);
}

/** Cierra la sesión y vuelve a la portada. */
export function salir() {
  cerrarSesion();
  window.location.assign('index.html');
}

/** Recarga el usuario vigente desde el servidor. Si el token ya no sirve, limpia la sesión. */
export async function refrescarUsuario() {
  if (!obtenerToken()) return null;
  try {
    const usuario = await api.get('/api/auth/yo');
    guardarUsuario(usuario);
    fijarRolPorDefecto(usuario);
    return usuario;
  } catch (e) {
    if (e instanceof ErrorApi && (e.estado === 401 || e.estado === 403)) cerrarSesion();
    throw e;
  }
}

/**
 * Para páginas privadas: devuelve el usuario vigente o redirige a `acceso.html`.
 * `rolesPermitidos` (opcional) limita el acceso a esos roles.
 */
export async function exigirSesion(rolesPermitidos = null) {
  if (!estaAutenticado()) {
    cerrarSesion();
    irAAcceso();
    return null;
  }
  try {
    const usuario = await refrescarUsuario();
    if (rolesPermitidos && !usuario.roles.some((r) => rolesPermitidos.includes(r))) {
      window.location.replace('perfil.html');
      return null;
    }
    return usuario;
  } catch (e) {
    if (e instanceof ErrorApi && (e.estado === 401 || e.estado === 403)) {
      irAAcceso(e.estado === 403 ? 'cuenta' : 'expirada');
      return null;
    }
    throw e; // sin conexión u otro fallo: la página decide cómo avisar
  }
}

/** Solo se aceptan rutas relativas del mismo sitio como destino tras iniciar sesión. */
export function destinoSeguro(siguiente, porDefecto = 'perfil.html') {
  if (typeof siguiente !== 'string' || !/^[A-Za-z0-9_\-./?=&%]+$/.test(siguiente)) return porDefecto;
  if (siguiente.startsWith('/') || siguiente.includes('..') || siguiente.includes('//')) return porDefecto;
  return siguiente;
}
