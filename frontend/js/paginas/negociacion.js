// Servicios confirmados: lista, ficha del servicio, desglose del pago, enlace del tutor, inicio, finalización,
// calificación bilateral y conversación. Todo el contenido dinámico se construye con textContent (nunca innerHTML).
import { iniciarEncabezado } from '../componentes/encabezado.js';
import { exito, error as toastError } from '../componentes/alertas.js';
import { montarChat } from '../componentes/chat.js';
import { pesos } from '../componentes/confirmacion.js';
import { api, ErrorApi } from '../servicios/api.js';
import { exigirSesion, rolActivo } from '../sesion.js';

iniciarEncabezado();
document.getElementById('anio').textContent = new Date().getFullYear();

const ETIQUETA_ESTADO = {
  CONFIRMADO: ['Confirmado', 'badge--info'],
  EN_DESARROLLO: ['En desarrollo', 'badge--advertencia'],
  FINALIZADO: ['Finalizado', 'badge--exito'],
  EN_REVISION: ['En revisión', 'badge--advertencia'],
  CANCELADO: ['Cancelado', 'badge--error'],
};
const ETIQUETA_PAGO = {
  RETENIDO: ['Fondos retenidos', 'badge--info'],
  LIBERADO: ['Pago liberado', 'badge--exito'],
  CONGELADO: ['Pago congelado', 'badge--advertencia'],
  REEMBOLSADO: ['Reembolsado', 'badge--error'],
  RECHAZADO: ['Rechazado', 'badge--error'],
};
const ETIQUETA_DURACION = { MIN_30: '30 min', MIN_45: '45 min', MIN_60: '60 min', MIN_90: '90 min', MIN_120: '120 min' };
const ETIQUETA_MODALIDAD = { VIRTUAL: 'Virtual', PRESENCIAL: 'Presencial' };

const aviso = document.getElementById('aviso');
const vistaLista = document.getElementById('vista-lista');
const vistaDetalle = document.getElementById('vista-detalle');
let sesion = null;

function el(etiqueta, clase, texto) {
  const nodo = document.createElement(etiqueta);
  if (clase) nodo.className = clase;
  if (texto !== undefined && texto !== null) nodo.textContent = texto;
  return nodo;
}

function fecha(iso) {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
}

function mostrarAviso(mensaje, tipo = 'error') {
  aviso.className = `alerta alerta--${tipo}`;
  aviso.textContent = mensaje;
  aviso.hidden = false;
}

function badge(mapa, clave) {
  const [texto, clase] = mapa[clave] ?? [clave, ''];
  return el('span', `badge ${clase}`.trim(), texto);
}

function mensajeDe(e) {
  return e instanceof ErrorApi || e?.message ? e.message : 'Ocurrió un error inesperado';
}

/** Con qué rol participa el usuario en este servicio (puede tener ambos roles, pero nunca en el mismo servicio). */
function miPapel(s) {
  if (s.estudiante.id === sesion.id) return 'ESTUDIANTE';
  if (s.tutor.id === sesion.id) return 'TUTOR';
  return null; // administrador
}

// ---------------------------------------------------------------- lista
function tarjetaServicio(s) {
  const papel = miPapel(s);
  const a = el('a', 'tarjeta tarjeta--elevable servicio');
  a.href = `negociacion.html?id=${encodeURIComponent(s.id)}`;
  const fila = el('div', 'fila-badges');
  fila.append(badge(ETIQUETA_ESTADO, s.estado));
  if (s.pago) fila.append(badge(ETIQUETA_PAGO, s.pago.estado));
  const contraparte = papel === 'TUTOR' ? `Estudiante: ${s.estudiante.nombre}` : `Tutor: ${s.tutor.nombre}`;
  a.append(
    el('h3', 'servicio__tema', s.tema ?? 'Tutoría'),
    fila,
    el('p', 'texto-suave', contraparte),
    el('p', 'texto-suave', `${fecha(s.fechaHora)} · ${ETIQUETA_DURACION[s.duracion] ?? s.duracion} · ${ETIQUETA_MODALIDAD[s.modalidad] ?? s.modalidad}`),
    el('strong', 'servicio__precio', pesos(papel === 'TUTOR' && s.pago?.montoNetoCOP != null ? s.pago.montoNetoCOP : s.precioFinalCOP)),
  );
  if (papel === 'TUTOR') a.appendChild(el('p', 'texto-suave', 'Ingreso neto tras la comisión'));
  return a;
}

async function cargarLista() {
  const estado = document.getElementById('fil-estado').value;
  const contenedor = document.getElementById('servicios');
  const vacio = document.getElementById('servicios-vacio');
  try {
    const lista = await api.get(`/api/servicios/mios${estado ? `?estado=${encodeURIComponent(estado)}` : ''}`);
    contenedor.replaceChildren(...lista.map(tarjetaServicio));
    vacio.hidden = lista.length > 0;
  } catch (e) {
    mostrarAviso(mensajeDe(e));
  }
}

// ---------------------------------------------------------------- detalle
function filaDato(lista, titulo, valor) {
  const d = el('div');
  d.append(el('dt', '', titulo), el('dd', '', valor));
  lista.appendChild(d);
}

function pintarFicha(s, papel) {
  const ficha = document.getElementById('ficha');
  const cabecera = el('div', 'oferta');
  const fila = el('div', 'fila-badges');
  fila.appendChild(badge(ETIQUETA_ESTADO, s.estado));
  cabecera.append(el('h2', '', s.tema ?? 'Tutoría'), fila);
  const datos = el('dl', 'datos');
  filaDato(datos, 'Estudiante', s.estudiante.nombre ?? '—');
  filaDato(datos, 'Tutor', s.tutor.nombre ?? '—');
  filaDato(datos, 'Fecha y hora', fecha(s.fechaHora));
  filaDato(datos, 'Duración', ETIQUETA_DURACION[s.duracion] ?? s.duracion);
  filaDato(datos, 'Modalidad', ETIQUETA_MODALIDAD[s.modalidad] ?? s.modalidad);
  filaDato(datos, s.modalidad === 'PRESENCIAL' ? 'Lugar de encuentro' : 'Enlace', s.lugarOEnlace ?? 'Por definir por el tutor');
  filaDato(datos, 'Confirmado el', fecha(s.fechaConfirmacion));
  if (s.fechaInicio) filaDato(datos, 'Iniciado el', fecha(s.fechaInicio));
  if (s.fechaFinalizacion) filaDato(datos, 'Finalizado el', fecha(s.fechaFinalizacion));
  ficha.replaceChildren(cabecera, datos);
  if (papel) {
    const enlace = el('a', 'btn btn--borde btn--pequeno', 'Ver la solicitud');
    enlace.href = `solicitudes.html?id=${encodeURIComponent(s.solicitudId)}`;
    ficha.appendChild(enlace);
  }
}

function textoEstadoPago(s, papel) {
  const tutor = papel === 'TUTOR';
  switch (s.pago.estado) {
    case 'RETENIDO':
      return tutor
        ? 'Los fondos están retenidos y se liberarán cuando finalice el servicio.'
        : `Pagaste ${pesos(s.pago.montoRetenidoCOP)}; se liberan al tutor cuando finalice el servicio.`;
    case 'LIBERADO':
      return tutor
        ? `Pago liberado: recibiste ${pesos(s.pago.montoNetoCOP)} netos tras la comisión de TutoFast.`
        : `Pago liberado al tutor: ${pesos(s.pago.montoNetoCOP)} netos y ${pesos(s.pago.comisionCOP)} de comisión de TutoFast.`;
    case 'CONGELADO':
      return 'El pago está congelado mientras un administrador revisa un reporte sobre este servicio.';
    case 'REEMBOLSADO':
      return tutor ? 'El pago fue reembolsado al estudiante.' : `Se reembolsó el monto completo (${pesos(s.pago.montoRetenidoCOP)}), sin comisión.`;
    default:
      return 'Estado del pago actualizado según el ciclo del servicio.';
  }
}

function pintarPago(s, papel) {
  const seccion = document.getElementById('pago');
  const titulo = el('div', 'oferta');
  titulo.appendChild(el('h2', '', papel === 'TUTOR' ? 'Tu ingreso' : 'Pago'));
  if (!s.pago) {
    seccion.replaceChildren(titulo, el('p', 'texto-suave', 'Este servicio todavía no tiene un pago registrado.'));
    return;
  }
  titulo.appendChild(badge(ETIQUETA_PAGO, s.pago.estado));
  const lista = el('dl', 'liquidacion');
  const fila = (nombre, valor, clase) => {
    const d = el('div', clase);
    d.append(el('dt', '', nombre), el('dd', '', valor));
    lista.appendChild(d);
  };
  fila('Precio acordado', pesos(s.precioFinalCOP));
  fila(`Comisión TutoFast (${Number(s.comisionPct).toLocaleString('es-CO')} %)`, `− ${pesos(s.pago.comisionCOP)}`);
  fila(papel === 'TUTOR' ? 'Recibirás (neto)' : 'Neto para el tutor', pesos(s.pago.montoNetoCOP), 'liquidacion__total');
  const notas = [el('p', 'texto-suave', textoEstadoPago(s, papel))];
  if (s.pago.referencia) notas.push(el('p', 'texto-suave', `Referencia de la pasarela simulada: ${s.pago.referencia}`));
  seccion.replaceChildren(titulo, lista, ...notas);
}

function pintarAcciones(s, papel, recargar) {
  const seccion = document.getElementById('acciones-servicio');
  const nodos = [];
  if (papel && s.estado === 'CONFIRMADO') {
    if (papel === 'TUTOR') nodos.push(formularioEnlace(s, recargar));
    const iniciar = el('button', 'btn btn--primario', 'Iniciar servicio');
    iniciar.type = 'button';
    iniciar.addEventListener('click', async () => {
      iniciar.disabled = true;
      try {
        await api.post(`/api/servicios/${encodeURIComponent(s.id)}/iniciar`);
        exito('El servicio está en desarrollo');
        await recargar();
      } catch (e) {
        toastError(mensajeDe(e));
        iniciar.disabled = false;
      }
    });
    nodos.push(el('p', 'texto-suave', 'Inicia el servicio cuando comience la sesión; también arranca solo a la hora programada.'), iniciar);
  }
  if (papel === 'ESTUDIANTE' && s.estado === 'EN_DESARROLLO') nodos.push(...bloqueFinalizar(s, recargar));
  if (papel === 'TUTOR' && s.estado === 'EN_DESARROLLO') {
    nodos.push(el('p', 'texto-suave', 'El servicio está en desarrollo. El estudiante lo finaliza al terminar la sesión; si no lo hace, '
      + 'se cierra automáticamente 24 horas después de la hora de fin programada y se libera tu pago.'));
  }
  if (papel && s.estado === 'EN_REVISION') {
    nodos.push(el('p', 'texto-suave', 'El servicio está en revisión por un reporte: el pago permanece congelado hasta que un administrador lo resuelva.'));
  }
  if (papel && s.estado !== 'CANCELADO') {
    const reportar = el('a', 'btn btn--borde btn--pequeno', 'Reportar un problema con este servicio');
    reportar.href = `reportes.html?servicioId=${encodeURIComponent(s.id)}`;
    nodos.push(reportar);
  }
  seccion.hidden = nodos.length === 0;
  seccion.replaceChildren(...nodos);
}

/** Finalizar con una confirmación explícita: libera el pago al tutor y no se puede deshacer. */
function bloqueFinalizar(s, recargar) {
  const neto = s.pago ? pesos(s.pago.montoNetoCOP) : 'el pago';
  const aviso = el('p', 'texto-suave', `Cuando termine la sesión, finaliza el servicio: se liberarán ${neto} al tutor (neto tras la comisión de TutoFast). `
    + 'Si no lo haces, se cerrará automáticamente 24 horas después de la hora de fin programada.');
  const finalizar = el('button', 'btn btn--primario', 'Finalizar servicio y liberar el pago');
  finalizar.type = 'button';
  const confirmacion = el('div', 'moderacion__form');
  confirmacion.hidden = true;
  const aceptar = el('button', 'btn btn--primario', 'Sí, finalizar');
  aceptar.type = 'button';
  const no = el('button', 'btn btn--borde', 'Todavía no');
  no.type = 'button';
  const fila = el('div', 'moderacion__acciones');
  fila.append(aceptar, no);
  confirmacion.append(el('p', '', 'Esta acción libera el pago al tutor y no se puede deshacer. ¿Confirmas que la tutoría se realizó?'), fila);
  finalizar.addEventListener('click', () => { confirmacion.hidden = false; finalizar.hidden = true; aceptar.focus(); });
  no.addEventListener('click', () => { confirmacion.hidden = true; finalizar.hidden = false; });
  aceptar.addEventListener('click', async () => {
    aceptar.disabled = true;
    try {
      await api.post(`/api/servicios/${encodeURIComponent(s.id)}/finalizar`);
      exito('Servicio finalizado: el pago fue liberado y ya puedes calificar');
      await recargar();
    } catch (e) {
      toastError(mensajeDe(e));
      aceptar.disabled = false;
      if (e instanceof ErrorApi && e.estado === 409) await recargar().catch(() => {});
    }
  });
  return [aviso, finalizar, confirmacion];
}

// ---------------------------------------------------------------- calificaciones
const CRITERIOS_AL_TUTOR = [
  ['claridadPedagogica', 'Claridad pedagógica'], ['dominioTematico', 'Dominio temático'], ['puntualidad', 'Puntualidad'],
  ['cumplimiento', 'Cumplimiento'], ['trato', 'Trato'], ['relacionCalidadPrecio', 'Relación calidad-precio'],
];
const CRITERIOS_AL_ESTUDIANTE = [
  ['puntualidad', 'Puntualidad'], ['comunicacion', 'Comunicación'], ['cumplimiento', 'Cumplimiento'],
];
const ETIQUETA_CRITERIO = Object.fromEntries([...CRITERIOS_AL_TUTOR, ...CRITERIOS_AL_ESTUDIANTE]);

function estrellasFijas(valor) {
  const llenas = Math.round(Number(valor));
  const nodo = el('span', 'estrellas-fijas');
  nodo.setAttribute('role', 'img');
  nodo.setAttribute('aria-label', `${Number(valor).toLocaleString('es-CO', { minimumFractionDigits: 2 })} de 5`);
  nodo.append(document.createTextNode('★'.repeat(llenas)), el('span', 'apagada', '★'.repeat(Math.max(0, 5 - llenas))));
  return nodo;
}

function bloqueResena(titulo, c) {
  const caja = el('div', 'resena-propia');
  const cab = el('div', 'oferta');
  cab.append(el('strong', '', titulo), estrellasFijas(c.promedio), el('strong', '', Number(c.promedio).toLocaleString('es-CO', { minimumFractionDigits: 2 })));
  const criterios = el('ul');
  Object.entries(c.criterios ?? {}).forEach(([clave, valor]) => criterios.appendChild(el('li', '', `${ETIQUETA_CRITERIO[clave] ?? clave}: ${valor}/5`)));
  caja.append(cab, criterios);
  if (c.comentario) caja.appendChild(el('p', 'resena__comentario', `«${c.comentario}»`));
  return caja;
}

function formularioCalificacion(s, papel, recargar) {
  const criterios = papel === 'ESTUDIANTE' ? CRITERIOS_AL_TUTOR : CRITERIOS_AL_ESTUDIANTE;
  const form = el('form', 'calificacion-form');
  form.noValidate = true;
  criterios.forEach(([clave, etiqueta]) => {
    const grupo = el('fieldset', 'criterio');
    grupo.appendChild(el('legend', '', etiqueta));
    const estrellas = el('div', 'estrellas');
    [5, 4, 3, 2, 1].forEach((v) => {
      const radio = el('input');
      radio.type = 'radio';
      radio.name = clave;
      radio.value = String(v);
      radio.id = `c-${clave}-${v}`;
      const label = el('label', '', '★');
      label.htmlFor = radio.id;
      label.title = `${v} de 5`;
      const lector = el('span', 'sr-only', `${v} de 5`);
      label.appendChild(lector);
      estrellas.append(radio, label);
    });
    grupo.appendChild(estrellas);
    form.appendChild(grupo);
  });
  const campo = el('div', 'campo');
  const etiqueta = el('label', '', 'Comentario (opcional)');
  etiqueta.htmlFor = 'comentario-calificacion';
  const comentario = el('textarea');
  comentario.id = 'comentario-calificacion';
  comentario.rows = 3;
  comentario.maxLength = 500;
  comentario.placeholder = 'La reseña será pública cuando ambas partes califiquen o venza el plazo. No incluyas datos de contacto.';
  const error = el('div', 'campo__error');
  error.setAttribute('role', 'alert');
  error.hidden = true;
  campo.append(etiqueta, comentario, error);
  const enviar = el('button', 'btn btn--primario', papel === 'ESTUDIANTE' ? 'Calificar al tutor' : 'Calificar al estudiante');
  enviar.type = 'submit';
  form.append(campo, enviar);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    error.hidden = true;
    const cuerpo = { comentario: comentario.value.trim() || undefined };
    const faltan = [];
    criterios.forEach(([clave, nombre]) => {
      const marcado = form.querySelector(`input[name="${clave}"]:checked`);
      if (marcado) cuerpo[clave] = Number(marcado.value); else faltan.push(nombre);
    });
    if (faltan.length) {
      error.textContent = `Falta calificar: ${faltan.join(', ')}`;
      error.hidden = false;
      return;
    }
    enviar.disabled = true;
    try {
      const creada = await api.post(`/api/servicios/${encodeURIComponent(s.id)}/calificaciones`, cuerpo);
      exito(creada.visible ? 'Calificación enviada: ambas reseñas ya son visibles' : 'Calificación enviada; se publicará cuando la otra parte califique o venza el plazo');
      await recargar();
    } catch (err) {
      error.textContent = mensajeDe(err);
      error.hidden = false;
      enviar.disabled = false;
    }
  });
  return form;
}

function botonReportarResena(c, recargar) {
  const abrir = el('button', 'btn btn--borde btn--pequeno', 'Reportar esta reseña');
  abrir.type = 'button';
  const form = el('form', 'moderacion__form');
  form.hidden = true;
  form.noValidate = true;
  const motivo = el('textarea');
  motivo.rows = 2;
  motivo.maxLength = 500;
  motivo.placeholder = '¿Por qué la reseña incumple las normas? (obligatorio)';
  motivo.setAttribute('aria-label', 'Motivo del reporte de la reseña');
  const enviar = el('button', 'btn btn--peligro btn--pequeno', 'Enviar reporte');
  enviar.type = 'submit';
  form.append(motivo, enviar);
  abrir.addEventListener('click', () => { form.hidden = !form.hidden; if (!form.hidden) motivo.focus(); });
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!motivo.value.trim()) { toastError('Indica el motivo del reporte'); return; }
    enviar.disabled = true;
    try {
      await api.post(`/api/calificaciones/${encodeURIComponent(c.id)}/reportar`, { motivo: motivo.value.trim() });
      exito('Reporte enviado; un administrador lo revisará');
      await recargar();
    } catch (err) {
      toastError(mensajeDe(err));
      enviar.disabled = false;
    }
  });
  const contenedor = el('div');
  contenedor.append(abrir, form);
  return contenedor;
}

async function pintarCalificaciones(s, papel, recargar) {
  const seccion = document.getElementById('calificaciones');
  if (!papel || s.estado !== 'FINALIZADO') {
    seccion.hidden = true;
    seccion.replaceChildren();
    return;
  }
  seccion.hidden = false;
  const titulo = el('h2', '', 'Calificaciones');
  let datos;
  try {
    datos = await api.get(`/api/servicios/${encodeURIComponent(s.id)}/calificaciones`);
  } catch (e) {
    seccion.replaceChildren(titulo, el('p', 'texto-suave', mensajeDe(e)));
    return;
  }
  const nodos = [titulo];
  const contraparteNombre = papel === 'ESTUDIANTE' ? s.tutor.nombre : s.estudiante.nombre;
  if (datos.fechaLimite) {
    nodos.push(el('p', 'texto-suave', datos.puedeCalificar
      ? `Puedes calificar hasta el ${fecha(datos.fechaLimite)}. Las reseñas se publican cuando ambas partes califican o al vencer el plazo.`
      : `El plazo para calificar ${new Date(datos.fechaLimite) < new Date() ? 'venció' : 'vence'} el ${fecha(datos.fechaLimite)}`));
  }
  if (datos.mia) {
    nodos.push(bloqueResena('Tu calificación', datos.mia));
    if (!datos.mia.visible) nodos.push(el('p', 'texto-suave', 'Tu reseña aún no es pública: se publicará cuando la otra parte califique o venza el plazo.'));
  } else if (datos.puedeCalificar) {
    nodos.push(el('h3', '', `Califica a ${contraparteNombre}`),
      formularioCalificacion(s, papel, recargar));
  } else {
    nodos.push(el('p', 'texto-suave', 'No calificaste este servicio y el plazo ya venció.'));
  }
  if (datos.contraparte) {
    nodos.push(bloqueResena(`Reseña de ${contraparteNombre}`, datos.contraparte));
    nodos.push(botonReportarResena(datos.contraparte, recargar));
  } else {
    nodos.push(el('p', 'texto-suave', `La reseña de ${contraparteNombre} se mostrará cuando ambos califiquen o venza el plazo.`));
  }
  seccion.replaceChildren(...nodos);
}

// ---------------------------------------------------------------- conversación
let chatActual = null;

function pintarConversacion(s, papel) {
  const tarjeta = document.getElementById('conversacion');
  chatActual?.destruir();
  chatActual = null;
  const rol = papel ?? (rolActivo() === 'ADMINISTRADOR' ? 'ADMINISTRADOR' : null);
  if (!rol) { tarjeta.hidden = true; return; }
  tarjeta.hidden = false;
  chatActual = montarChat(document.getElementById('conversacion-zona'), {
    solicitudId: s.solicitudId, tutorId: s.tutor.id, usuarioId: sesion.id, rol,
  });
}

function formularioEnlace(s, recargar) {
  const form = el('form');
  form.noValidate = true;
  const campo = el('div', 'campo');
  const etiqueta = el('label', '', s.modalidad === 'PRESENCIAL' ? 'Lugar de encuentro' : 'Enlace de la sesión');
  etiqueta.htmlFor = 'enlace-servicio';
  const entrada = el('input');
  entrada.id = 'enlace-servicio';
  entrada.type = 'text';
  entrada.maxLength = 300;
  entrada.value = s.lugarOEnlace ?? '';
  entrada.placeholder = s.modalidad === 'PRESENCIAL' ? 'Biblioteca central, sala 3' : 'https://meet.example/abc';
  const error = el('div', 'campo__error');
  error.hidden = true;
  campo.append(etiqueta, entrada, error);
  const guardar = el('button', 'btn btn--secundario', 'Guardar lugar o enlace');
  guardar.type = 'submit';
  form.append(campo, guardar);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    error.hidden = true;
    if (!entrada.value.trim()) {
      error.textContent = 'Indica el lugar o el enlace';
      error.hidden = false;
      return;
    }
    guardar.disabled = true;
    try {
      await api.patch(`/api/servicios/${encodeURIComponent(s.id)}/enlace`, { lugarOEnlace: entrada.value.trim() });
      exito('Lugar o enlace guardado');
      await recargar();
    } catch (err) {
      toastError(mensajeDe(err));
      guardar.disabled = false;
    }
  });
  return form;
}

async function mostrarDetalle(id) {
  const cargar = async () => {
    const s = await api.get(`/api/servicios/${encodeURIComponent(id)}`);
    const papel = miPapel(s);
    document.getElementById('titulo-pagina').textContent = 'Servicio';
    pintarFicha(s, papel);
    pintarPago(s, papel);
    pintarAcciones(s, papel, cargar);
    pintarConversacion(s, papel);
    await pintarCalificaciones(s, papel, cargar);
  };
  try {
    await cargar();
    vistaDetalle.hidden = false;
  } catch (e) {
    mostrarAviso(mensajeDe(e));
    vistaLista.hidden = rolActivo() === 'ADMINISTRADOR';
    if (rolActivo() !== 'ADMINISTRADOR') await cargarLista();
  }
}

// ---------------------------------------------------------------- inicio
(async function iniciar() {
  try {
    sesion = await exigirSesion();
    if (!sesion) return;
    document.getElementById('cargando').hidden = true;
    const id = new URLSearchParams(window.location.search).get('id');
    if (id) {
      await mostrarDetalle(id);
      return;
    }
    if (rolActivo() === 'ADMINISTRADOR') {
      mostrarAviso('Abre un servicio desde su enlace para consultarlo.', 'advertencia');
      return;
    }
    vistaLista.hidden = false;
    document.getElementById('fil-estado').addEventListener('change', cargarLista);
    await cargarLista();
  } catch (err) {
    document.getElementById('cargando').hidden = true;
    mostrarAviso(mensajeDe(err));
  }
})();
