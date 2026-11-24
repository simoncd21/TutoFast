// Chat entre el estudiante dueño de una solicitud y cada tutor que propuso sobre ella.
// - Polling cada 4 s con `desde` (solo trae mensajes nuevos), en pausa mientras la pestaña está oculta o el chat no
//   se ve, y con retroceso exponencial (hasta 30 s) si el servidor no responde.
// - Hilo en solo lectura (propuesta descartada, solicitud cancelada, servicio concluido o administrador): sin formulario.
// - Adjuntos PDF, PNG, JPG o DOCX de hasta 10 MB; reporte de mensajes de la otra parte.
// Todo el contenido dinámico se construye con textContent (nunca innerHTML).
import { exito, error as toastError } from './alertas.js';
import { api, ErrorApi, descargarArchivo } from '../servicios/api.js';

const INTERVALO_BASE_MS = 4000;
const INTERVALO_MAXIMO_MS = 30000;
const REFRESCO_HILOS_MS = 15000;
const MAXIMO_CARACTERES = 2000;
const TAMANO_MAXIMO_BYTES = 10 * 1024 * 1024;
const EXTENSIONES = ['pdf', 'png', 'jpg', 'jpeg', 'docx'];

const MOTIVOS = [
  ['CONDUCTA_ABUSIVA', 'Conducta abusiva'],
  ['ACOSO', 'Acoso'],
  ['CONTACTO_EXTERNO', 'Contacto o pago fuera de la plataforma'],
  ['FRAUDE_ACADEMICO', 'Fraude académico'],
  ['SPAM', 'Spam'],
  ['OTRO', 'Otro'],
];

function el(etiqueta, clase, texto) {
  const nodo = document.createElement(etiqueta);
  if (clase) nodo.className = clase;
  if (texto !== undefined && texto !== null) nodo.textContent = texto;
  return nodo;
}

function boton(texto, clase, alClic) {
  const b = el('button', clase, texto);
  b.type = 'button';
  b.addEventListener('click', alClic);
  return b;
}

const mensajeDe = (e) => (e instanceof ErrorApi || e?.message ? e.message : 'Ocurrió un error inesperado');

function hora(iso) {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleString('es-CO', { dateStyle: 'short', timeStyle: 'short' });
}

/** Extensión en minúsculas de un nombre de archivo (sin punto). */
function extensionDe(nombre) {
  const i = nombre.lastIndexOf('.');
  return i < 0 ? '' : nombre.slice(i + 1).toLowerCase();
}

/**
 * Monta un chat dentro de `contenedor`. `rol` es el rol activo ('ESTUDIANTE' | 'TUTOR' | 'ADMINISTRADOR').
 * Para el estudiante y el administrador hay que indicar el `tutorId` del hilo; el tutor siempre usa el suyo.
 * Devuelve `{ destruir() }`.
 */
export function montarChat(contenedor, { solicitudId, tutorId, usuarioId, rol, alActividad }) {
  contenedor.replaceChildren();
  const administrador = rol === 'ADMINISTRADOR';
  const idsPintados = new Set();
  let ultimaMarca = null;
  let soloLectura = administrador;
  let intervalo = INTERVALO_BASE_MS;
  let temporizador = null;
  let activo = true;
  let consultando = false;
  let primeraCarga = true;

  const raiz = el('div', 'chat');
  const estado = el('div', 'chat__estado');
  estado.hidden = true;
  const lista = el('div', 'chat__mensajes');
  lista.setAttribute('role', 'log');
  lista.setAttribute('aria-live', 'polite');
  lista.setAttribute('aria-label', 'Mensajes de la conversación');
  lista.tabIndex = 0;
  const vacio = el('p', 'texto-suave chat__vacio', 'Todavía no hay mensajes. ¡Escribe el primero!');
  const conexion = el('p', 'chat__conexion', 'Sin conexión: reintentando…');
  conexion.hidden = true;
  const formulario = construirFormulario();
  raiz.append(estado, lista, conexion, formulario.nodo);
  contenedor.appendChild(raiz);
  lista.appendChild(vacio);

  // ---- pintado ----
  function pintarMensaje(m) {
    const propio = m.emisorId && m.emisorId === usuarioId;
    const burbuja = el('article', `chat__mensaje${propio ? ' chat__mensaje--propio' : ''}${m.sistema ? ' chat__mensaje--sistema' : ''}${m.retirado ? ' chat__mensaje--retirado' : ''}`);
    burbuja.dataset.id = m.id;
    if (!m.sistema) {
      const cabecera = el('div', 'chat__cabecera');
      cabecera.append(el('strong', '', propio ? 'Tú' : (m.emisorNombre ?? 'Participante')), el('time', 'texto-suave', hora(m.marcaTemporal)));
      burbuja.appendChild(cabecera);
    }
    if (m.contenido) burbuja.appendChild(el('p', 'chat__texto', m.contenido));
    if (m.archivo) {
      const b = boton(`📎 ${m.archivo.nombre} (${Number(m.archivo.tamanoMB).toFixed(2)} MB)`, 'btn btn--borde btn--pequeno chat__adjunto', async () => {
        try { await descargarArchivo(m.archivo.archivoId, m.archivo.nombre); } catch (e) { toastError(mensajeDe(e)); }
      });
      burbuja.appendChild(b);
    }
    const pie = el('div', 'chat__pie');
    if (m.reportado) pie.appendChild(el('span', 'badge badge--advertencia', 'Reportado'));
    if (m.retirado && administrador) pie.appendChild(el('span', 'badge badge--error', 'Retirado por moderación'));
    if (!propio && !m.sistema && !administrador && !m.reportado) {
      pie.appendChild(boton('Reportar', 'chat__reportar', () => abrirReporte(burbuja, m, pie)));
    }
    if (pie.childNodes.length) burbuja.appendChild(pie);
    return burbuja;
  }

  function abrirReporte(burbuja, m, pie) {
    if (burbuja.querySelector('.chat__form-reporte')) return;
    const form = el('form', 'chat__form-reporte');
    form.noValidate = true;
    const select = document.createElement('select');
    select.setAttribute('aria-label', 'Motivo del reporte');
    MOTIVOS.forEach(([v, t]) => select.appendChild(new Option(t, v)));
    const detalle = el('textarea');
    detalle.rows = 2;
    detalle.maxLength = 500;
    detalle.placeholder = 'Detalle (opcional)';
    detalle.setAttribute('aria-label', 'Detalle del reporte');
    const enviar = el('button', 'btn btn--peligro btn--pequeno', 'Enviar reporte');
    enviar.type = 'submit';
    const cancelar = boton('Cancelar', 'btn btn--borde btn--pequeno', () => form.remove());
    const acciones = el('div', 'fila-badges');
    acciones.append(enviar, cancelar);
    form.append(select, detalle, acciones);
    form.addEventListener('submit', async (ev) => {
      ev.preventDefault();
      enviar.disabled = true;
      try {
        await api.post(`/api/mensajes/${encodeURIComponent(m.id)}/reportar`, {
          motivo: select.value, detalle: detalle.value.trim() || undefined,
        });
        exito('Reporte enviado; un administrador lo revisará');
        form.remove();
        pie.replaceChildren(el('span', 'badge badge--advertencia', 'Reportado'));
      } catch (e) {
        toastError(mensajeDe(e));
        enviar.disabled = false;
      }
    });
    burbuja.appendChild(form);
    select.focus();
  }

  function agregar(mensajes) {
    const nuevos = mensajes.filter((m) => !idsPintados.has(m.id));
    if (!nuevos.length) return false;
    const cercaDelFinal = lista.scrollHeight - lista.scrollTop - lista.clientHeight < 80;
    vacio.remove();
    nuevos.forEach((m) => {
      idsPintados.add(m.id);
      lista.appendChild(pintarMensaje(m));
    });
    ultimaMarca = nuevos[nuevos.length - 1].marcaTemporal;
    if (cercaDelFinal || primeraCarga) lista.scrollTop = lista.scrollHeight;
    return true;
  }

  function aplicarModo(respuesta) {
    const lectura = administrador || respuesta.soloLectura;
    if (lectura !== soloLectura || primeraCarga) {
      soloLectura = lectura;
      formulario.nodo.hidden = soloLectura;
      estado.hidden = !soloLectura;
      estado.textContent = soloLectura
        ? `Conversación en solo lectura${respuesta.motivoSoloLectura ? `: ${respuesta.motivoSoloLectura}` : ''}.`
        : '';
    }
  }

  // ---- polling ----
  const visible = () => contenedor.isConnected && contenedor.offsetParent !== null && !document.hidden;

  async function consultar() {
    if (!activo || consultando) return;
    if (!contenedor.isConnected) { destruir(); return; }
    if (!visible()) { programar(INTERVALO_BASE_MS); return; } // en pausa: oculto o pestaña en segundo plano
    consultando = true;
    try {
      const parametros = new URLSearchParams();
      if (tutorId) parametros.set('tutorId', tutorId);
      // Se resta 1 ms (Mongo guarda milisegundos) y se descartan repetidos por id.
      if (ultimaMarca) parametros.set('desde', new Date(new Date(ultimaMarca).getTime() - 1).toISOString());
      const consulta = parametros.toString();
      const respuesta = await api.get(`/api/solicitudes/${encodeURIComponent(solicitudId)}/mensajes${consulta ? `?${consulta}` : ''}`);
      if (!activo) return;
      aplicarModo(respuesta);
      const hubo = agregar(respuesta.mensajes);
      if (hubo) alActividad?.();
      primeraCarga = false;
      conexion.hidden = true;
      intervalo = INTERVALO_BASE_MS;
    } catch (e) {
      if (!activo) return;
      if (e instanceof ErrorApi && [401, 403, 404].includes(e.estado)) {
        estado.hidden = false;
        estado.textContent = e.message;
        formulario.nodo.hidden = true;
        destruir();
        return;
      }
      conexion.hidden = false;
      intervalo = Math.min(intervalo * 2, INTERVALO_MAXIMO_MS); // retroceso exponencial
    } finally {
      consultando = false;
    }
    if (activo) programar(intervalo);
  }

  function programar(ms) {
    clearTimeout(temporizador);
    if (activo) temporizador = setTimeout(consultar, ms);
  }

  const alCambiarVisibilidad = () => {
    if (!activo || document.hidden) return;
    intervalo = INTERVALO_BASE_MS;
    clearTimeout(temporizador);
    consultar(); // al volver a la pestaña se actualiza al instante
  };
  document.addEventListener('visibilitychange', alCambiarVisibilidad);

  function destruir() {
    activo = false;
    clearTimeout(temporizador);
    document.removeEventListener('visibilitychange', alCambiarVisibilidad);
  }

  // ---- envío ----
  function construirFormulario() {
    const form = el('form', 'chat__formulario');
    form.noValidate = true;
    const texto = el('textarea');
    texto.rows = 2;
    texto.maxLength = MAXIMO_CARACTERES;
    texto.placeholder = 'Escribe un mensaje… (Enter envía, Shift+Enter salto de línea)';
    texto.setAttribute('aria-label', 'Mensaje');
    const archivo = el('input');
    archivo.type = 'file';
    archivo.accept = '.pdf,.png,.jpg,.jpeg,.docx';
    archivo.setAttribute('aria-label', 'Adjuntar archivo (PDF, PNG, JPG o DOCX de hasta 10 MB)');
    const error = el('div', 'campo__error');
    error.setAttribute('role', 'alert');
    error.hidden = true;
    const contador = el('span', 'texto-suave chat__contador', `0 / ${MAXIMO_CARACTERES}`);
    const enviar = el('button', 'btn btn--primario', 'Enviar');
    enviar.type = 'submit';
    const barra = el('div', 'chat__barra');
    barra.append(archivo, contador, enviar);
    form.append(texto, barra, error);

    texto.addEventListener('input', () => { contador.textContent = `${texto.value.length} / ${MAXIMO_CARACTERES}`; });
    texto.addEventListener('keydown', (ev) => {
      if (ev.key === 'Enter' && !ev.shiftKey) {
        ev.preventDefault();
        form.requestSubmit();
      }
    });
    form.addEventListener('submit', async (ev) => {
      ev.preventDefault();
      error.hidden = true;
      const contenido = texto.value.trim();
      const adjunto = archivo.files[0] ?? null;
      if (!contenido && !adjunto) {
        error.textContent = 'Escribe un mensaje o adjunta un archivo';
        error.hidden = false;
        return;
      }
      if (adjunto && !EXTENSIONES.includes(extensionDe(adjunto.name))) {
        error.textContent = 'Tipo de archivo no permitido. Solo se admiten formatos seguros (PDF, PNG, JPG, DOCX)';
        error.hidden = false;
        return;
      }
      if (adjunto && adjunto.size > TAMANO_MAXIMO_BYTES) {
        error.textContent = 'El archivo supera el tamaño máximo de 10 MB';
        error.hidden = false;
        return;
      }
      enviar.disabled = true;
      try {
        const datos = { contenido: contenido || undefined };
        if (tutorId && !administrador && rol === 'ESTUDIANTE') datos.tutorId = tutorId;
        let mensaje;
        if (adjunto) {
          const cuerpo = new FormData();
          cuerpo.append('datos', new Blob([JSON.stringify(datos)], { type: 'application/json' }));
          cuerpo.append('archivo', adjunto);
          mensaje = await api.post(`/api/solicitudes/${encodeURIComponent(solicitudId)}/mensajes`, cuerpo);
        } else {
          mensaje = await api.post(`/api/solicitudes/${encodeURIComponent(solicitudId)}/mensajes`, datos);
        }
        texto.value = '';
        archivo.value = '';
        contador.textContent = `0 / ${MAXIMO_CARACTERES}`;
        primeraCarga = true; // lleva la vista al mensaje recién enviado
        agregar([mensaje]);
        primeraCarga = false;
        alActividad?.();
      } catch (e) {
        error.textContent = mensajeDe(e); // por ejemplo, el aviso de contacto externo bloqueado (422)
        error.hidden = false;
        if (e instanceof ErrorApi && e.estado === 403) consultar(); // el hilo pudo haberse cerrado
      } finally {
        enviar.disabled = false;
        texto.focus();
      }
    });
    return { nodo: form };
  }

  formulario.nodo.hidden = administrador;
  consultar();
  return { destruir };
}

/**
 * Sección de conversaciones de una solicitud. El estudiante dueño y el administrador ven la lista de hilos (uno
 * por tutor con propuesta); el tutor, si ya propuso, ve directamente su conversación con el estudiante.
 */
export async function montarMensajeria(contenedor, solicitud, { usuarioId, rol, tutorIdInicial }) {
  contenedor.replaceChildren();
  const esDueno = rol === 'ESTUDIANTE' && solicitud.estudianteId === usuarioId;
  const esTutor = rol === 'TUTOR' && solicitud.estudianteId !== usuarioId;
  const esAdmin = rol === 'ADMINISTRADOR';
  if (!esDueno && !esTutor && !esAdmin) return { destruir() {} };

  const titulo = el('h3', '', 'Conversación');
  contenedor.appendChild(titulo);
  let chat = null;
  let temporizadorHilos = null;
  let activo = true;
  const destruir = () => {
    activo = false;
    chat?.destruir();
    clearInterval(temporizadorHilos);
  };

  try {
    if (esTutor) {
      const propias = await api.get(`/api/solicitudes/${encodeURIComponent(solicitud.id)}/propuestas`);
      if (!propias.length) {
        contenedor.appendChild(el('p', 'texto-suave', 'Envía una propuesta para poder conversar con el estudiante.'));
        return { destruir };
      }
      titulo.textContent = `Conversación con ${solicitud.estudianteNombre ?? 'el estudiante'}`;
      const zona = el('div');
      contenedor.appendChild(zona);
      chat = montarChat(zona, { solicitudId: solicitud.id, tutorId: usuarioId, usuarioId, rol });
      return { destruir };
    }

    const hilos = await api.get(`/api/solicitudes/${encodeURIComponent(solicitud.id)}/hilos`);
    if (!hilos.length) {
      contenedor.appendChild(el('p', 'texto-suave', 'Cuando un tutor envíe una propuesta podrás conversar con él aquí.'));
      return { destruir };
    }
    const barra = el('div', 'chat__hilos');
    barra.setAttribute('role', 'tablist');
    barra.setAttribute('aria-label', 'Conversaciones por tutor');
    const zona = el('div', 'chat__zona');
    contenedor.append(barra, zona);
    let seleccionado = hilos.some((h) => h.tutorId === tutorIdInicial) ? tutorIdInicial : hilos[0].tutorId;

    const pintarHilos = (lista) => {
      barra.replaceChildren(...lista.map((h) => {
        const b = el('button', 'pestana chat__hilo');
        b.type = 'button';
        b.setAttribute('role', 'tab');
        b.setAttribute('aria-selected', String(h.tutorId === seleccionado));
        b.append(el('span', '', h.tutorNombre ?? 'Tutor'));
        if (h.noLeidos > 0 && h.tutorId !== seleccionado) b.appendChild(el('span', 'badge badge--info', String(h.noLeidos)));
        if (h.soloLectura && !esAdmin) b.appendChild(el('span', 'badge', 'Solo lectura'));
        b.addEventListener('click', () => { seleccionado = h.tutorId; abrir(); pintarHilos(lista); });
        return b;
      }));
    };
    const abrir = () => {
      chat?.destruir();
      chat = montarChat(zona, { solicitudId: solicitud.id, tutorId: seleccionado, usuarioId, rol,
        alActividad: () => refrescarHilos() });
    };
    const refrescarHilos = async () => {
      if (!activo || !contenedor.isConnected || contenedor.offsetParent === null || document.hidden) return;
      try { pintarHilos(await api.get(`/api/solicitudes/${encodeURIComponent(solicitud.id)}/hilos`)); } catch { /* se reintenta */ }
    };
    pintarHilos(hilos);
    abrir();
    temporizadorHilos = setInterval(() => {
      if (!contenedor.isConnected) destruir(); else refrescarHilos();
    }, REFRESCO_HILOS_MS);
  } catch (e) {
    contenedor.appendChild(el('p', 'texto-suave', mensajeDe(e)));
  }
  return { destruir };
}
