// Reportes: radicar un reporte (con evidencias), consultar los radicados y recibidos, y ver/apelar las sanciones.
// Todo el contenido dinámico se construye con textContent (nunca innerHTML).
import { iniciarEncabezado } from '../componentes/encabezado.js';
import { exito, error as toastError } from '../componentes/alertas.js';
import { api, ErrorApi } from '../servicios/api.js';
import { exigirSesion } from '../sesion.js';
import { montarSanciones } from '../componentes/sanciones.js';
import {
  el, badge, fechaHora, botonesEvidencias, campoEvidencias, construirMultipart, validarEvidencias,
  ETIQUETA_TIPO_REPORTE, ETIQUETA_ESTADO_REPORTE, ETIQUETA_DECISION, ETIQUETA_ACCION_PAGO, TIPOS_QUE_EXIGEN_SERVICIO,
} from '../componentes/evidencias.js';

iniciarEncabezado();
document.getElementById('anio').textContent = new Date().getFullYear();

const aviso = document.getElementById('aviso');
const form = document.getElementById('form-reporte');
const selServicio = document.getElementById('rep-servicio');
const selTipo = document.getElementById('rep-tipo');
const textoDenunciado = document.getElementById('rep-denunciado');
const evidencias = campoEvidencias('rep-archivos');
document.getElementById('rep-evidencias').appendChild(evidencias.campo);

const parametros = new URLSearchParams(window.location.search);
// Un enlace puede traer un denunciado concreto (reporte sin servicio); el servidor valida cada dato.
const denunciadoDeLaUrl = /^[A-Za-z0-9]{1,64}$/.test(parametros.get('denunciadoId') ?? '') ? parametros.get('denunciadoId') : null;
const nombreDenunciadoUrl = (parametros.get('denunciado') ?? '').slice(0, 80);
let usuarioId = null;
let servicios = [];

function mostrarAviso(mensaje, tipo = 'info') {
  aviso.textContent = mensaje;
  aviso.className = `alerta alerta--${tipo}`;
  aviso.hidden = false;
}

function limpiarErrores() {
  form.querySelectorAll('[data-error-de]').forEach((n) => { n.hidden = true; n.textContent = ''; });
  form.querySelectorAll('.invalido').forEach((n) => { n.classList.remove('invalido'); n.removeAttribute('aria-invalid'); });
  document.getElementById('rep-evidencias-error').hidden = true;
}

function marcarError(campo, mensaje) {
  const contenedor = form.querySelector(`[data-error-de="${campo}"]`);
  if (contenedor) { contenedor.textContent = mensaje; contenedor.hidden = false; }
  const control = form.elements[campo];
  if (control?.classList) { control.classList.add('invalido'); control.setAttribute('aria-invalid', 'true'); }
}

// ---------------------------------------------------------------- formulario
function llenarTipos() {
  selTipo.replaceChildren(...Object.entries(ETIQUETA_TIPO_REPORTE).map(([valor, texto]) => new Option(texto, valor)));
}

function textoServicio(s) {
  const otro = s.estudiante.id === usuarioId ? s.tutor.nombre : s.estudiante.nombre;
  return `${s.tema ?? 'Tutoría'} · con ${otro ?? '—'} · ${fechaHora(s.fechaHora)}`;
}

function llenarServicios() {
  const opciones = servicios.map((s) => new Option(textoServicio(s), s.id));
  if (denunciadoDeLaUrl) opciones.unshift(new Option(`Sin servicio (conducta de ${nombreDenunciadoUrl || 'otro usuario'})`, ''));
  else opciones.unshift(new Option('Elige el servicio…', ''));
  selServicio.replaceChildren(...opciones);
  const deLaUrl = parametros.get('servicioId');
  if (deLaUrl && servicios.some((s) => s.id === deLaUrl)) selServicio.value = deLaUrl;
  else if (denunciadoDeLaUrl) selServicio.value = '';
  actualizarDenunciado();
}

/** Muestra a quién se reporta según el servicio elegido (siempre la otra parte del servicio). */
function actualizarDenunciado() {
  const s = servicios.find((x) => x.id === selServicio.value);
  if (s) {
    const otro = s.estudiante.id === usuarioId ? s.tutor : s.estudiante;
    textoDenunciado.textContent = `Se reportará a ${otro.nombre ?? 'la otra parte'} (la otra persona de este servicio).`;
    textoDenunciado.hidden = false;
  } else if (denunciadoDeLaUrl) {
    textoDenunciado.textContent = `Se reportará a ${nombreDenunciadoUrl || 'el usuario indicado'}.`;
    textoDenunciado.hidden = false;
  } else {
    textoDenunciado.hidden = true;
  }
}
selServicio.addEventListener('change', actualizarDenunciado);

form.addEventListener('submit', async (e) => {
  e.preventDefault();
  limpiarErrores();
  const servicioId = selServicio.value;
  const tipo = selTipo.value;
  const motivo = form.elements.motivo.value.trim();
  let valido = true;
  if (!servicioId && !denunciadoDeLaUrl) { marcarError('servicioId', 'Elige el servicio sobre el que quieres reportar'); valido = false; }
  if (!servicioId && TIPOS_QUE_EXIGEN_SERVICIO.includes(tipo)) {
    marcarError('tipo', 'Este motivo solo se puede reportar sobre un servicio'); valido = false;
  }
  if (motivo.length < 10 || motivo.length > 1000) { marcarError('motivo', 'El motivo debe tener entre 10 y 1000 caracteres'); valido = false; }
  const problemaArchivos = validarEvidencias(evidencias.entrada.files);
  if (problemaArchivos) {
    const caja = document.getElementById('rep-evidencias-error');
    caja.textContent = problemaArchivos;
    caja.hidden = false;
    valido = false;
  }
  if (!valido) { form.querySelector('.invalido')?.focus(); return; }

  const datos = { tipo, motivo };
  if (servicioId) datos.servicioId = servicioId;
  else datos.denunciadoId = denunciadoDeLaUrl;
  const boton = e.submitter ?? form.querySelector('button[type="submit"]');
  const textoBoton = boton.textContent;
  boton.disabled = true;
  boton.textContent = 'Radicando…';
  try {
    await api.post('/api/reportes', construirMultipart(datos, evidencias.entrada.files));
    form.reset();
    llenarServicios();
    exito('Reporte radicado: un administrador lo revisará');
    await Promise.all([cargarReportes(), cargarServicios()]);
  } catch (err) {
    if (err instanceof ErrorApi && err.detalles?.length) err.detalles.forEach((d) => marcarError(d.campo, d.mensaje));
    toastError(err.message ?? 'No se pudo radicar el reporte');
  } finally {
    boton.disabled = false;
    boton.textContent = textoBoton;
  }
});

// ---------------------------------------------------------------- mis reportes
function tarjetaReporte(r, propio) {
  const item = el('article', 'moderacion__item');
  const estado = ETIQUETA_ESTADO_REPORTE[r.estado] ?? { texto: r.estado, clase: '' };
  const cab = el('div', 'cola__cabecera');
  const fila = el('div', 'fila-badges');
  fila.append(badge(estado.texto, estado.clase),
    el('span', 'texto-suave', fechaHora(r.fechaRadicacion)));
  cab.append(el('strong', '', ETIQUETA_TIPO_REPORTE[r.tipo] ?? r.tipo), fila);
  item.appendChild(cab);
  item.appendChild(el('p', 'texto-suave', propio
    ? `Reportaste a ${r.denunciado?.nombre ?? '—'}` : 'Reporte radicado en tu contra'));
  item.appendChild(el('p', 'moderacion__cita', r.motivo));
  if (propio && r.evidencias?.length) item.appendChild(botonesEvidencias(r.evidencias));
  if (r.servicioId) {
    const enlace = el('a', 'btn btn--borde btn--pequeno', 'Ver el servicio');
    enlace.href = `negociacion.html?id=${encodeURIComponent(r.servicioId)}`;
    item.appendChild(enlace);
  }
  if (r.resolucion) {
    const res = el('div', 'sancion__apelacion');
    res.append(el('strong', '', ETIQUETA_DECISION[r.resolucion.decision] ?? r.resolucion.decision),
      el('p', 'texto-suave', ETIQUETA_ACCION_PAGO[r.resolucion.accionPago] ?? ''),
      el('p', '', r.resolucion.observacion ?? ''),
      el('p', 'texto-suave', `Resuelto por ${r.resolucion.resueltoPor?.nombre ?? 'un administrador'} · ${fechaHora(r.resolucion.fecha)}`));
    item.appendChild(res);
  } else {
    item.appendChild(el('p', 'texto-suave', 'Un administrador está revisando este reporte.'));
  }
  return item;
}

async function cargarReportes() {
  try {
    const { radicados, recibidos } = await api.get('/api/reportes/mios');
    document.getElementById('reportes-radicados').replaceChildren(...radicados.map((r) => tarjetaReporte(r, true)));
    document.getElementById('radicados-vacio').hidden = radicados.length > 0;
    document.getElementById('reportes-recibidos').replaceChildren(...recibidos.map((r) => tarjetaReporte(r, false)));
    document.getElementById('recibidos-vacio').hidden = recibidos.length > 0;
  } catch (err) {
    toastError(err.message ?? 'No se pudieron cargar tus reportes');
  }
}
document.getElementById('reportes-recargar').addEventListener('click', () => cargarReportes());

async function cargarServicios() {
  try {
    servicios = await api.get('/api/servicios/mios');
    llenarServicios();
  } catch (err) {
    toastError(err.message ?? 'No se pudieron cargar tus servicios');
  }
}

// ---------------------------------------------------------------- sanciones
const sanciones = montarSanciones(document.getElementById('lista-sanciones'), document.getElementById('sanciones-vacio'));

// ---------------------------------------------------------------- carga inicial
(async function iniciar() {
  try {
    const usuario = await exigirSesion(['ESTUDIANTE', 'TUTOR']);
    if (!usuario) return;
    usuarioId = usuario.id;
    llenarTipos();
    document.getElementById('cargando').hidden = true;
    document.getElementById('contenido').hidden = false;
    await Promise.all([cargarServicios(), cargarReportes(), sanciones.recargar()]);
    if (window.location.hash === '#sanciones') document.getElementById('sanciones').scrollIntoView();
  } catch (err) {
    document.getElementById('cargando').hidden = true;
    mostrarAviso(err.message ?? 'No se pudo cargar la página. Inténtalo de nuevo.', 'error');
  }
})();
