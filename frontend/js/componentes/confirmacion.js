// Modal «Autorizar pago simulado y confirmar». No hay campos de tarjeta: la plataforma solo envía un token opaco
// del simulador (`tok_aprobar` o `tok_rechazar`) y jamás recibe ni guarda datos de tarjeta.
// Todo el contenido dinámico se construye con textContent (nunca innerHTML).
import { api, ErrorApi } from '../servicios/api.js';
import { abrirModal, cerrarModal, error as toastError } from './alertas.js';

const ID_MODAL = 'modal-confirmacion-pago';
const ETIQUETA_DURACION = { MIN_30: '30 min', MIN_45: '45 min', MIN_60: '60 min', MIN_90: '90 min', MIN_120: '120 min' };
const ETIQUETA_MODALIDAD = { VIRTUAL: 'Virtual', PRESENCIAL: 'Presencial' };

const formatoPesos = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
export const pesos = (valor) => formatoPesos.format(Number(valor));

/** Mismo cálculo que el servidor (HALF_UP a pesos enteros): solo orienta; la cifra definitiva la fija el backend. */
export function estimarLiquidacion(precio, comisionPct) {
  const comision = Math.round((Number(precio) * Number(comisionPct)) / 100);
  return { comision, neto: Math.round(Number(precio)) - comision };
}

function el(etiqueta, clase, texto) {
  const nodo = document.createElement(etiqueta);
  if (clase) nodo.className = clase;
  if (texto !== undefined && texto !== null) nodo.textContent = texto;
  return nodo;
}

function modal() {
  let fondo = document.getElementById(ID_MODAL);
  if (fondo) return fondo;
  fondo = el('div', 'modal-fondo');
  fondo.id = ID_MODAL;
  fondo.setAttribute('role', 'dialog');
  fondo.setAttribute('aria-modal', 'true');
  fondo.setAttribute('aria-labelledby', `${ID_MODAL}-titulo`);
  const caja = el('div', 'modal');
  const cabecera = el('div', 'modal__cabecera');
  const titulo = el('h2', '', 'Confirmar tutoría y pago');
  titulo.id = `${ID_MODAL}-titulo`;
  const cerrar = el('button', 'btn btn--icono', '×');
  cerrar.type = 'button';
  cerrar.setAttribute('aria-label', 'Cerrar');
  cerrar.addEventListener('click', () => cerrarModal(ID_MODAL));
  cabecera.append(titulo, cerrar);
  const cuerpo = el('div');
  cuerpo.id = `${ID_MODAL}-cuerpo`;
  caja.append(cabecera, cuerpo);
  fondo.appendChild(caja);
  fondo.addEventListener('click', (e) => { if (e.target === fondo) cerrarModal(ID_MODAL); });
  document.body.appendChild(fondo);
  return fondo;
}

async function comisionVigente() {
  try {
    const c = await api.get('/api/comision/vigente');
    return Number(c.porcentaje);
  } catch {
    return null; // sin dato: el desglose se muestra al confirmar
  }
}

/**
 * Abre el modal para confirmar al tutor de la propuesta aceptada.
 * @param {{solicitud: object, propuesta: object, alConfirmar: (servicio: object) => void, alFallar?: () => void}} opciones
 */
export async function abrirConfirmacionPago({ solicitud, propuesta, alConfirmar, alFallar }) {
  modal();
  const cuerpo = document.getElementById(`${ID_MODAL}-cuerpo`);
  const comisionPct = await comisionVigente();

  const resumen = el('dl', 'datos');
  const fila = (titulo, valor) => {
    const d = el('div');
    d.append(el('dt', '', titulo), el('dd', '', valor));
    resumen.appendChild(d);
  };
  fila('Tutoría', solicitud.tema);
  fila('Tutor', propuesta.tutorNombre);
  fila('Duración', ETIQUETA_DURACION[propuesta.duracion] ?? propuesta.duracion);
  fila('Modalidad', ETIQUETA_MODALIDAD[propuesta.modalidad] ?? propuesta.modalidad);

  const total = el('div', 'pago-resumen');
  total.append(el('span', 'texto-suave', 'Total a pagar'), el('strong', 'pago-resumen__monto', pesos(propuesta.precioCOP)));
  const detalle = el('p', 'texto-suave');
  if (comisionPct !== null) {
    const { comision, neto } = estimarLiquidacion(propuesta.precioCOP, comisionPct);
    detalle.textContent = `Los fondos quedan retenidos hasta finalizar el servicio. Comisión vigente de TutoFast: `
      + `${comisionPct.toLocaleString('es-CO')} % (${pesos(comision)}); el tutor recibirá ${pesos(neto)}.`;
  } else {
    detalle.textContent = 'Los fondos quedan retenidos hasta finalizar el servicio.';
  }

  const aviso = el('div', 'alerta alerta--advertencia', 'Pago simulado: no se solicitan ni se guardan datos de tarjeta.');
  const grupo = el('fieldset', 'selector-rol');
  grupo.appendChild(el('legend', '', 'Resultado de la pasarela simulada'));
  const opciones = [
    ['tok_aprobar', 'Aprobar el pago', true],
    ['tok_rechazar', 'Simular un pago rechazado (para probar el error)', false],
  ];
  opciones.forEach(([valor, texto, marcada]) => {
    const etiqueta = el('label', 'opcion-rol');
    const radio = el('input');
    radio.type = 'radio';
    radio.name = 'token-pago';
    radio.value = valor;
    radio.checked = marcada;
    etiqueta.append(radio, el('span', '', texto));
    grupo.appendChild(etiqueta);
  });

  const errorPago = el('div', 'alerta alerta--error');
  errorPago.setAttribute('role', 'alert');
  errorPago.hidden = true;

  const pie = el('div', 'modal__pie');
  const cancelar = el('button', 'btn btn--borde', 'Volver');
  cancelar.type = 'button';
  cancelar.addEventListener('click', () => cerrarModal(ID_MODAL));
  const confirmar = el('button', 'btn btn--primario', 'Autorizar pago simulado y confirmar');
  confirmar.type = 'button';
  confirmar.addEventListener('click', async () => {
    errorPago.hidden = true;
    confirmar.disabled = true;
    cancelar.disabled = true;
    const token = grupo.querySelector('input[name="token-pago"]:checked')?.value ?? 'tok_aprobar';
    try {
      const servicio = await api.post(`/api/solicitudes/${encodeURIComponent(solicitud.id)}/confirmar`,
        { tokenPagoSimulado: token });
      cerrarModal(ID_MODAL);
      alConfirmar(servicio);
    } catch (e) {
      const mensaje = e instanceof ErrorApi || e?.message ? e.message : 'No se pudo confirmar la tutoría';
      if (e instanceof ErrorApi && e.codigo === 'PAGO_RECHAZADO') {
        errorPago.textContent = `${mensaje}. No se realizó ningún cobro; puedes intentarlo de nuevo.`;
        errorPago.hidden = false;
      } else {
        cerrarModal(ID_MODAL);
        toastError(mensaje);
        alFallar?.();
      }
    } finally {
      confirmar.disabled = false;
      cancelar.disabled = false;
    }
  });
  pie.append(cancelar, confirmar);

  cuerpo.replaceChildren(resumen, total, detalle, aviso, grupo, errorPago, pie);
  abrirModal(ID_MODAL);
}
