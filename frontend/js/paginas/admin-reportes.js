// Panel de administración · pestaña «Reportes»: cola de reportes con expediente (servicio, pago, chat y evidencias),
// resolución (decisión, acción sobre el pago y sanción opcional) y cola de sanciones con la resolución de apelaciones.
// Todo el contenido dinámico se construye con textContent (nunca innerHTML).
import { api, descargarArchivo } from '../servicios/api.js';
import { exito, error as toastError } from '../componentes/alertas.js';
import {
  el, badge, fechaHora, pesos, botonesEvidencias,
  ETIQUETA_TIPO_REPORTE, ETIQUETA_ESTADO_REPORTE, ETIQUETA_DECISION, ETIQUETA_ACCION_PAGO,
  ETIQUETA_TIPO_SANCION, ETIQUETA_ESTADO_SANCION, ETIQUETA_RESULTADO_APELACION,
} from '../componentes/evidencias.js';

const TAMANO_PAGINA = 10;
const ETIQUETA_ESTADO_SERVICIO = {
  CONFIRMADO: 'Confirmado', EN_DESARROLLO: 'En desarrollo', FINALIZADO: 'Finalizado', EN_REVISION: 'En revisión', CANCELADO: 'Cancelado',
};
const ETIQUETA_ESTADO_PAGO = {
  RETENIDO: 'Retenido', CONGELADO: 'Congelado', LIBERADO: 'Liberado', REEMBOLSADO: 'Reembolsado', RECHAZADO: 'Rechazado',
};

let paginaReportes = 0;
let paginaSanciones = 0;

function filaDato(lista, titulo, valor) {
  const d = el('div');
  d.append(el('dt', '', titulo), el('dd', '', valor));
  lista.appendChild(d);
}

function boton(texto, clase, alClic) {
  const b = el('button', clase, texto);
  b.type = 'button';
  b.addEventListener('click', alClic);
  return b;
}

function paginacion(prefijo, pagina, datos) {
  document.getElementById(`${prefijo}-pagina`).textContent = datos.totalPaginas === 0
    ? '' : `Página ${datos.pagina + 1} de ${datos.totalPaginas}`;
  document.getElementById(`${prefijo}-anterior`).disabled = datos.pagina <= 0;
  document.getElementById(`${prefijo}-siguiente`).disabled = datos.pagina + 1 >= datos.totalPaginas;
}

// ---------------------------------------------------------------- expediente de un reporte
function bloqueServicio(exp) {
  const s = exp.servicio;
  if (!s) return el('p', 'texto-suave', 'Reporte sin servicio asociado (conducta general): no hay pago ni historial de chat.');
  const datos = el('dl', 'datos');
  filaDato(datos, 'Servicio', s.tema ?? 'Tutoría');
  filaDato(datos, 'Estudiante', s.estudiante.nombre ?? '—');
  filaDato(datos, 'Tutor', s.tutor.nombre ?? '—');
  filaDato(datos, 'Fecha y hora', fechaHora(s.fechaHora));
  filaDato(datos, 'Estado del servicio', ETIQUETA_ESTADO_SERVICIO[s.estado] ?? s.estado);
  filaDato(datos, 'Precio acordado', pesos(s.precioFinalCOP));
  if (exp.pago) {
    filaDato(datos, 'Estado del pago', ETIQUETA_ESTADO_PAGO[exp.pago.estado] ?? exp.pago.estado);
    filaDato(datos, 'Comisión congelada', `${Number(exp.pago.comisionPct).toLocaleString('es-CO')} %`);
    filaDato(datos, 'Neto para el tutor', pesos(exp.pago.montoNetoCOP));
  }
  return datos;
}

function bloqueChat(exp) {
  const caja = el('div', 'expediente__chat');
  caja.setAttribute('role', 'log');
  caja.setAttribute('aria-label', 'Historial del chat del servicio');
  if (exp.historialChat.length === 0) {
    caja.appendChild(el('p', 'texto-suave', 'No hay mensajes en el chat de este servicio.'));
    return caja;
  }
  exp.historialChat.forEach((m) => {
    const msg = el('div', m.retirado ? 'expediente__mensaje expediente__mensaje--retirado' : 'expediente__mensaje');
    const cab = el('div', 'fila-badges');
    cab.append(el('strong', '', m.sistema ? 'TutoFast' : (m.emisorNombre ?? '—')), el('span', 'texto-suave', fechaHora(m.marcaTemporal)));
    if (m.retirado) cab.appendChild(badge('Retirado por moderación', 'badge--error'));
    msg.append(cab, el('p', '', m.contenido ?? '[Sin texto: solo adjunto]'));
    if (m.archivo) {
      msg.appendChild(boton(`📎 ${m.archivo.nombre}`, 'btn btn--borde btn--pequeno', async () => {
        try { await descargarArchivo(m.archivo.archivoId, m.archivo.nombre); } catch (e) { toastError(e.message); }
      }));
    }
    caja.appendChild(msg);
  });
  return caja;
}

function bloqueSanciones(exp) {
  if (exp.sanciones.length === 0) return null;
  const caja = el('div');
  caja.appendChild(el('h4', '', 'Sanciones derivadas'));
  exp.sanciones.forEach((s) => {
    const estado = ETIQUETA_ESTADO_SANCION[s.estado] ?? { texto: s.estado, clase: '' };
    const fila = el('div', 'fila-badges');
    fila.append(badge(ETIQUETA_TIPO_SANCION[s.tipo] ?? s.tipo), badge(estado.texto, estado.clase),
      el('span', 'texto-suave', `Sancionado: ${s.usuario?.nombre ?? '—'}`
        + (s.vigencia?.fin ? ` · hasta ${fechaHora(s.vigencia.fin)}` : '')));
    caja.appendChild(fila);
  });
  return caja;
}

function resumenResolucion(r) {
  const res = r.resolucion;
  const caja = el('div', 'sancion__apelacion');
  caja.append(el('strong', '', ETIQUETA_DECISION[res.decision] ?? res.decision),
    el('p', 'texto-suave', ETIQUETA_ACCION_PAGO[res.accionPago] ?? ''),
    el('p', '', res.observacion ?? ''),
    el('p', 'texto-suave', `Resuelto por ${res.resueltoPor?.nombre ?? '—'} · ${fechaHora(res.fecha)}`));
  return caja;
}

/** Formulario de resolución: decisión, acción sobre el pago, observación y sanción opcional (solo a favor de quien reporta). */
function formularioResolucion(exp, alResolver) {
  const r = exp.reporte;
  const form = el('form', 'moderacion__form');
  form.noValidate = true;

  const campo = (etiqueta, control, id) => {
    const c = el('div', 'campo');
    const l = el('label', '', etiqueta);
    control.id = id;
    l.htmlFor = id;
    c.append(l, control);
    return c;
  };
  const decision = document.createElement('select');
  Object.entries(ETIQUETA_DECISION).forEach(([v, t]) => decision.appendChild(new Option(t, v)));
  const accion = document.createElement('select');
  Object.entries(ETIQUETA_ACCION_PAGO).forEach(([v, t]) => accion.appendChild(new Option(t, v)));
  accion.value = 'NINGUNA';
  const observacion = el('textarea');
  observacion.rows = 3;
  observacion.maxLength = 1000;
  observacion.placeholder = 'Fundamenta la decisión (obligatorio)';
  const sancion = document.createElement('select');
  [['', 'Sin sanción'], ...Object.entries(ETIQUETA_TIPO_SANCION)]
    .forEach(([v, t]) => sancion.appendChild(new Option(v ? `Sancionar a ${r.denunciado?.nombre ?? 'la persona reportada'}: ${t.toLowerCase()}` : t, v)));
  const fin = el('input');
  fin.type = 'date';
  fin.hidden = true;
  fin.setAttribute('aria-label', 'Último día de la suspensión');
  const ayuda = el('p', 'texto-suave');

  const id = r.id;
  const filaSelects = el('div', 'rejilla-form');
  filaSelects.append(campo('Decisión', decision, `res-decision-${id}`), campo('Acción sobre el pago', accion, `res-accion-${id}`));
  const bloqueSancion = el('div', 'campo');
  const etiquetaSancion = el('label', '', 'Sanción (opcional)');
  sancion.id = `res-sancion-${id}`;
  etiquetaSancion.htmlFor = sancion.id;
  bloqueSancion.append(etiquetaSancion, sancion, fin);

  const confirmar = el('button', 'btn btn--peligro btn--pequeno', 'Resolver reporte');
  confirmar.type = 'submit';
  const error = el('div', 'campo__error');
  error.hidden = true;
  form.append(filaSelects, ayuda, campo('Observación', observacion, `res-obs-${id}`), bloqueSancion, error, confirmar);

  const sincronizar = () => {
    const aFavor = decision.value === 'A_FAVOR_REPORTANTE';
    const sinServicio = !exp.servicio;
    // Un reporte desestimado no mueve dinero y sin servicio no hay pago que mover.
    accion.disabled = sinServicio || decision.value === 'DESESTIMADO';
    if (accion.disabled) accion.value = 'NINGUNA';
    bloqueSancion.hidden = !aFavor;
    if (!aFavor) { sancion.value = ''; }
    fin.hidden = !(aFavor && sancion.value === 'SUSPENSION_TEMPORAL');
    ayuda.textContent = accion.value === 'REEMBOLSAR'
      ? 'El estudiante recibirá el monto completo y el servicio quedará cancelado.'
      : accion.value === 'LIBERAR' ? 'El pago se liquidará al tutor (menos la comisión) y el servicio quedará finalizado.'
        : exp.servicio ? 'Sin movimiento de pago: el servicio y el pago se reanudan si no hay otros reportes abiertos.' : '';
  };
  decision.addEventListener('change', sincronizar);
  accion.addEventListener('change', sincronizar);
  sancion.addEventListener('change', sincronizar);
  sincronizar();

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    error.hidden = true;
    const texto = observacion.value.trim();
    if (!texto) { error.textContent = 'La observación es obligatoria'; error.hidden = false; observacion.focus(); return; }
    const cuerpo = { decision: decision.value, accionPago: accion.value, observacion: texto };
    if (sancion.value) {
      cuerpo.sancion = { tipo: sancion.value };
      if (sancion.value === 'SUSPENSION_TEMPORAL') {
        const finIso = fin.value ? new Date(`${fin.value}T23:59:59Z`) : null;
        if (!finIso || finIso <= new Date()) {
          error.textContent = 'Indica un último día de suspensión posterior a hoy';
          error.hidden = false;
          fin.focus();
          return;
        }
        cuerpo.sancion.fin = finIso.toISOString();
      }
    }
    if (accion.value !== 'NINGUNA'
      && !window.confirm(`${ETIQUETA_ACCION_PAGO[accion.value]}. Esta acción mueve dinero y no se puede deshacer. ¿Continuar?`)) return;
    confirmar.disabled = true;
    try {
      await api.post(`/api/admin/reportes/${encodeURIComponent(id)}/resolver`, cuerpo);
      exito('Reporte resuelto');
      await alResolver();
    } catch (err) {
      error.textContent = err.message ?? 'No se pudo resolver el reporte';
      error.hidden = false;
      toastError(err.message ?? 'No se pudo resolver el reporte');
      confirmar.disabled = false;
    }
  });
  return form;
}

function pintarExpediente(zona, exp, alResolver) {
  const r = exp.reporte;
  const nodos = [
    el('h4', '', 'Servicio y pago'), bloqueServicio(exp),
    el('h4', '', 'Descripción del reporte'), el('p', 'moderacion__cita', r.motivo),
  ];
  if (r.evidencias.length) nodos.push(el('h4', '', 'Evidencias'), botonesEvidencias(r.evidencias));
  if (exp.servicio) nodos.push(el('h4', '', 'Historial del chat'), bloqueChat(exp));
  const sanciones = bloqueSanciones(exp);
  if (sanciones) nodos.push(sanciones);
  if (r.resolucion) nodos.push(el('h4', '', 'Resolución'), resumenResolucion(r));
  else nodos.push(el('h4', '', 'Resolver'), formularioResolucion(exp, alResolver));
  zona.replaceChildren(...nodos);
}

function itemReporte(r) {
  const item = el('article', 'moderacion__item');
  const estado = ETIQUETA_ESTADO_REPORTE[r.estado] ?? { texto: r.estado, clase: '' };
  const cab = el('div', 'cola__cabecera');
  const fila = el('div', 'fila-badges');
  fila.append(badge(estado.texto, estado.clase), el('span', 'texto-suave', fechaHora(r.fechaRadicacion)));
  cab.append(el('strong', '', ETIQUETA_TIPO_REPORTE[r.tipo] ?? r.tipo), fila);
  item.append(cab,
    el('p', 'texto-suave', `${r.reportante?.nombre ?? '—'} reportó a ${r.denunciado?.nombre ?? '—'}`
      + (r.servicioId ? ' · sobre un servicio' : ' · sin servicio')),
    el('p', '', r.motivo.length > 160 ? `${r.motivo.slice(0, 157)}…` : r.motivo));

  const zona = el('div', 'expediente');
  zona.hidden = true;
  let cargado = false;
  const abrir = boton('Abrir expediente', 'btn btn--secundario btn--pequeno', async () => {
    zona.hidden = !zona.hidden;
    abrir.textContent = zona.hidden ? 'Abrir expediente' : 'Cerrar expediente';
    if (zona.hidden || cargado) return;
    abrir.disabled = true;
    try {
      const exp = await api.get(`/api/admin/reportes/${encodeURIComponent(r.id)}`);
      pintarExpediente(zona, exp, async () => { await Promise.all([cargarReportesAdmin(), cargarSancionesAdmin()]); });
      cargado = true;
    } catch (e) {
      zona.hidden = true;
      abrir.textContent = 'Abrir expediente';
      toastError(e.message ?? 'No se pudo cargar el expediente');
    } finally {
      abrir.disabled = false;
    }
  });
  item.append(abrir, zona);
  return item;
}

export async function cargarReportesAdmin(pagina = paginaReportes) {
  const estado = document.getElementById('rep-admin-estado').value;
  try {
    const datos = await api.get(`/api/admin/reportes?pagina=${pagina}&tamano=${TAMANO_PAGINA}${estado ? `&estado=${estado}` : ''}`);
    paginaReportes = datos.pagina;
    document.getElementById('reportes-admin').replaceChildren(...datos.contenido.map(itemReporte));
    document.getElementById('reportes-admin-vacio').hidden = datos.contenido.length > 0;
    paginacion('reportes-admin', pagina, datos);
  } catch (e) {
    toastError(e.message ?? 'No se pudieron cargar los reportes');
  }
}

// ---------------------------------------------------------------- sanciones y apelaciones
function formularioApelacion(sancion, apelacion, alResolver) {
  const form = el('form', 'moderacion__form');
  form.noValidate = true;
  const resultado = document.createElement('select');
  resultado.setAttribute('aria-label', 'Resultado de la apelación');
  resultado.append(new Option('Revocar la sanción (aceptar la apelación)', 'REVOCADA'),
    new Option('Ratificar la sanción (rechazar la apelación)', 'RATIFICADA'));
  const observacion = el('textarea');
  observacion.rows = 2;
  observacion.maxLength = 1000;
  observacion.placeholder = 'Fundamenta la decisión (obligatorio)';
  observacion.setAttribute('aria-label', 'Observación de la resolución');
  const error = el('div', 'campo__error');
  error.hidden = true;
  const enviar = el('button', 'btn btn--primario btn--pequeno', 'Resolver apelación');
  enviar.type = 'submit';
  form.append(resultado, observacion, error, enviar);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const texto = observacion.value.trim();
    if (!texto) { error.textContent = 'La observación es obligatoria'; error.hidden = false; observacion.focus(); return; }
    error.hidden = true;
    enviar.disabled = true;
    try {
      await api.post(`/api/admin/sanciones/${encodeURIComponent(sancion.id)}/apelaciones/${encodeURIComponent(apelacion.id)}/resolver`,
        { resultado: resultado.value, observacion: texto });
      exito(resultado.value === 'REVOCADA' ? 'Sanción revocada: la cuenta fue restablecida' : 'Sanción ratificada');
      await alResolver();
    } catch (err) {
      error.textContent = err.message ?? 'No se pudo resolver la apelación';
      error.hidden = false;
      toastError(err.message ?? 'No se pudo resolver la apelación');
      enviar.disabled = false;
    }
  });
  return form;
}

function itemSancion(s) {
  const item = el('article', 'moderacion__item');
  const estado = ETIQUETA_ESTADO_SANCION[s.estado] ?? { texto: s.estado, clase: '' };
  const cab = el('div', 'cola__cabecera');
  const fila = el('div', 'fila-badges');
  fila.append(badge(estado.texto, estado.clase));
  cab.append(el('strong', '', `${ETIQUETA_TIPO_SANCION[s.tipo] ?? s.tipo} · ${s.usuario?.nombre ?? '—'}`), fila);
  item.append(cab,
    el('p', 'texto-suave', s.vigencia
      ? `Del ${fechaHora(s.vigencia.inicio)}${s.vigencia.fin ? ` al ${fechaHora(s.vigencia.fin)}` : ' (indefinida)'}` : 'Constancia sin vigencia'),
    el('p', '', `Motivo: ${ETIQUETA_TIPO_REPORTE[s.tipoReporte] ?? s.tipoReporte ?? 'Reporte'}${s.motivo ? ` — ${s.motivo}` : ''}`));
  s.apelaciones.forEach((a) => {
    const caja = el('div', 'sancion__apelacion');
    const res = ETIQUETA_RESULTADO_APELACION[a.resultado] ?? { texto: a.resultado, clase: '' };
    const cabA = el('div', 'cola__cabecera');
    cabA.append(el('strong', '', `Apelación del ${fechaHora(a.fechaPresentacion)}`), badge(res.texto, res.clase));
    caja.append(cabA, el('p', 'moderacion__cita', a.descargo));
    if (a.evidencias.length) caja.appendChild(botonesEvidencias(a.evidencias));
    if (a.resultado === 'PENDIENTE') {
      caja.appendChild(formularioApelacion(s, a, async () => { await Promise.all([cargarSancionesAdmin(), cargarReportesAdmin()]); }));
    } else {
      caja.appendChild(el('p', 'texto-suave', `Resuelta el ${fechaHora(a.fechaResolucion)}${a.observacionResolucion ? `: ${a.observacionResolucion}` : ''}`));
    }
    item.appendChild(caja);
  });
  const enlace = el('p', 'texto-suave', `Reporte de origen: ${s.reporteId}`);
  item.appendChild(enlace);
  return item;
}

export async function cargarSancionesAdmin(pagina = paginaSanciones) {
  const estado = document.getElementById('san-admin-estado').value;
  try {
    const datos = await api.get(`/api/admin/sanciones?pagina=${pagina}&tamano=${TAMANO_PAGINA}${estado ? `&estado=${estado}` : ''}`);
    paginaSanciones = datos.pagina;
    document.getElementById('sanciones-admin').replaceChildren(...datos.contenido.map(itemSancion));
    document.getElementById('sanciones-admin-vacio').hidden = datos.contenido.length > 0;
    paginacion('sanciones-admin', pagina, datos);
  } catch (e) {
    toastError(e.message ?? 'No se pudieron cargar las sanciones');
  }
}

// ---------------------------------------------------------------- eventos
document.getElementById('rep-admin-estado').addEventListener('change', () => cargarReportesAdmin(0));
document.getElementById('san-admin-estado').addEventListener('change', () => cargarSancionesAdmin(0));
document.getElementById('reportes-admin-recargar').addEventListener('click', () => Promise.all([cargarReportesAdmin(), cargarSancionesAdmin()]));
document.getElementById('reportes-admin-anterior').addEventListener('click', () => cargarReportesAdmin(paginaReportes - 1));
document.getElementById('reportes-admin-siguiente').addEventListener('click', () => cargarReportesAdmin(paginaReportes + 1));
document.getElementById('sanciones-admin-anterior').addEventListener('click', () => cargarSancionesAdmin(paginaSanciones - 1));
document.getElementById('sanciones-admin-siguiente').addEventListener('click', () => cargarSancionesAdmin(paginaSanciones + 1));

export function cargarPanelReportes() {
  return Promise.all([cargarReportesAdmin(0), cargarSancionesAdmin(0)]);
}
