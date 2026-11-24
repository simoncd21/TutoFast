// Campana de notificaciones del encabezado: contador con polling cada 30 s y panel con las últimas notificaciones.
// Todo el texto se inserta con textContent (anti-XSS).
import { api, ErrorApi } from '../servicios/api.js';

export const INTERVALO_MS = 30000;
export const EVENTO_NOTIFICACIONES = 'tutofast:notificaciones';

const TAMANO_PANEL = 10;

function elemento(etiqueta, clase, texto) {
  const el = document.createElement(etiqueta);
  if (clase) el.className = clase;
  if (texto !== undefined) el.textContent = texto;
  return el;
}

/** Avisa a la campana y a la lista del perfil de que cambió el estado de las notificaciones. */
export function avisarCambioNotificaciones() {
  window.dispatchEvent(new CustomEvent(EVENTO_NOTIFICACIONES));
}

function formatearFecha(iso) {
  const fecha = new Date(iso);
  return Number.isNaN(fecha.getTime())
    ? ''
    : fecha.toLocaleString('es-CO', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
}

// Solo se navega a páginas relativas del propio sitio (por ejemplo "perfil.html#soportes").
const ENLACE_SEGURO = /^[A-Za-z0-9_-]+\.html(#[A-Za-z0-9_-]*)?$/;

/** Crea el <li> de una notificación. `alMarcar` se invoca (sin esperar) cuando se marca como leída al activarla. */
export function crearElementoNotificacion(n, alMarcar) {
  const item = elemento('li', n.leida ? 'notificacion' : 'notificacion notificacion--no-leida');
  const boton = elemento('button', 'notificacion__cuerpo');
  boton.type = 'button';
  const titulo = elemento('span', 'notificacion__titulo', n.titulo);
  const mensaje = elemento('span', 'notificacion__mensaje', n.mensaje ?? '');
  const fecha = elemento('span', 'notificacion__fecha', formatearFecha(n.fecha));
  if (!n.leida) {
    const estado = elemento('span', 'solo-lectores', 'No leída. ');
    boton.append(estado);
  }
  boton.append(titulo, mensaje, fecha);

  boton.addEventListener('click', async () => {
    if (!n.leida) {
      try {
        await api.patch(`/api/notificaciones/${encodeURIComponent(n.id)}/leida`);
        n.leida = true;
        item.classList.remove('notificacion--no-leida');
        alMarcar?.(n);
        avisarCambioNotificaciones();
      } catch {
        // si falla el marcado igual se permite navegar
      }
    }
    if (n.enlace && ENLACE_SEGURO.test(n.enlace)) window.location.href = n.enlace;
  });

  item.appendChild(boton);
  return item;
}

export function construirCampana() {
  const contenedor = elemento('div', 'campana');

  const boton = elemento('button', 'btn btn--icono campana__boton');
  boton.type = 'button';
  boton.setAttribute('aria-haspopup', 'true');
  boton.setAttribute('aria-expanded', 'false');
  boton.setAttribute('aria-controls', 'panel-notificaciones');
  boton.setAttribute('aria-label', 'Notificaciones');
  const icono = elemento('span', 'campana__icono', '🔔');
  icono.setAttribute('aria-hidden', 'true');
  const insignia = elemento('span', 'campana__insignia');
  insignia.setAttribute('aria-hidden', 'true');
  insignia.hidden = true;
  boton.append(icono, insignia);

  const panel = elemento('div', 'campana__panel');
  panel.id = 'panel-notificaciones';
  panel.setAttribute('role', 'region');
  panel.setAttribute('aria-label', 'Notificaciones recientes');
  panel.hidden = true;

  const cabecera = elemento('div', 'campana__cabecera');
  const titulo = elemento('strong', '', 'Notificaciones');
  const leerTodas = elemento('button', 'campana__accion', 'Marcar todas como leídas');
  leerTodas.type = 'button';
  cabecera.append(titulo, leerTodas);

  const lista = elemento('ul', 'campana__lista');
  const vacio = elemento('p', 'campana__vacio texto-suave', 'No tienes notificaciones.');
  vacio.hidden = true;
  const pie = elemento('a', 'campana__pie', 'Ver todas en mi perfil');
  pie.href = 'perfil.html#notificaciones';
  panel.append(cabecera, lista, vacio, pie);
  contenedor.append(boton, panel);

  let temporizador = null;

  function pintarConteo(cantidad) {
    insignia.hidden = cantidad === 0;
    insignia.textContent = cantidad > 99 ? '99+' : String(cantidad);
    boton.setAttribute('aria-label', cantidad === 0
      ? 'Notificaciones'
      : `Notificaciones, ${cantidad} sin leer`);
    leerTodas.disabled = cantidad === 0;
  }

  async function refrescarConteo() {
    try {
      const { noLeidas } = await api.get('/api/notificaciones/conteo-no-leidas');
      pintarConteo(noLeidas);
    } catch (e) {
      if (e instanceof ErrorApi && e.estado === 401) detener(); // sesión vencida: no seguir consultando
    }
  }

  async function refrescarLista() {
    try {
      const pagina = await api.get(`/api/notificaciones?pagina=0&tamano=${TAMANO_PANEL}`);
      lista.replaceChildren(...pagina.contenido.map((n) => crearElementoNotificacion(n)));
      vacio.hidden = pagina.contenido.length > 0;
    } catch {
      lista.replaceChildren();
      vacio.textContent = 'No se pudieron cargar las notificaciones.';
      vacio.hidden = false;
    }
  }

  function abrir() {
    panel.hidden = false;
    boton.setAttribute('aria-expanded', 'true');
    refrescarLista();
  }

  function cerrar() {
    panel.hidden = true;
    boton.setAttribute('aria-expanded', 'false');
  }

  function detener() {
    if (temporizador) clearInterval(temporizador);
    temporizador = null;
  }

  boton.addEventListener('click', () => (panel.hidden ? abrir() : cerrar()));
  document.addEventListener('click', (e) => { if (!contenedor.contains(e.target)) cerrar(); });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !panel.hidden) { cerrar(); boton.focus(); }
  });

  leerTodas.addEventListener('click', async () => {
    try {
      await api.patch('/api/notificaciones/leer-todas');
      avisarCambioNotificaciones();
    } catch { /* el conteo se corrige en el próximo ciclo */ }
  });

  window.addEventListener(EVENTO_NOTIFICACIONES, () => {
    refrescarConteo();
    if (!panel.hidden) refrescarLista();
  });

  // Polling cada 30 s; en pestañas ocultas se pausa y se retoma al volver.
  refrescarConteo();
  temporizador = setInterval(() => { if (!document.hidden) refrescarConteo(); }, INTERVALO_MS);
  document.addEventListener('visibilitychange', () => { if (!document.hidden && temporizador) refrescarConteo(); });

  return contenedor;
}
