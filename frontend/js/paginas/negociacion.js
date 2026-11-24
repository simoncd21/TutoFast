// Servicios confirmados: lista, ficha del servicio, desglose del pago retenido, enlace del tutor e inicio.
// Todo el contenido dinámico se construye con textContent (nunca innerHTML).
import { iniciarEncabezado } from '../componentes/encabezado.js';
import { exito, error as toastError } from '../componentes/alertas.js';
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
  const notas = [el('p', 'texto-suave', s.pago.estado === 'RETENIDO'
    ? (papel === 'TUTOR'
      ? 'Los fondos están retenidos y se liberarán cuando finalice el servicio.'
      : `Pagaste ${pesos(s.pago.montoRetenidoCOP)}; se liberan al tutor cuando finalice el servicio.`)
    : 'Estado del pago actualizado según el ciclo del servicio.')];
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
    nodos.push(el('p', 'texto-suave', 'Inicia el servicio cuando comience la sesión; cualquiera de los dos participantes puede hacerlo.'), iniciar);
  }
  seccion.hidden = nodos.length === 0;
  seccion.replaceChildren(...nodos);
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
