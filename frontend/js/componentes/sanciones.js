// «Mis sanciones»: consulta de las sanciones del usuario y formulario de apelación (descargo + evidencias).
// Funciona también con una sesión restringida (cuenta suspendida o bloqueada). Todo el texto usa textContent.
import { api } from '../servicios/api.js';
import { exito, error as toastError } from './alertas.js';
import {
  el, badge, fechaHora, botonesEvidencias, campoEvidencias, construirMultipart, validarEvidencias,
  ETIQUETA_TIPO_SANCION, ETIQUETA_ESTADO_SANCION, ETIQUETA_RESULTADO_APELACION, ETIQUETA_TIPO_REPORTE,
} from './evidencias.js';

const MINIMO_DESCARGO = 10;
const MAXIMO_DESCARGO = 2000;

function vigencia(s) {
  if (!s.vigencia) return 'Sin vigencia: queda como constancia en tu historial';
  const inicio = fechaHora(s.vigencia.inicio);
  return s.vigencia.fin ? `Del ${inicio} al ${fechaHora(s.vigencia.fin)}` : `Desde el ${inicio}, de forma indefinida`;
}

function apelacion(a) {
  const caja = el('div', 'sancion__apelacion');
  const cab = el('div', 'cola__cabecera');
  const estado = ETIQUETA_RESULTADO_APELACION[a.resultado] ?? { texto: a.resultado, clase: '' };
  cab.append(el('strong', '', `Apelación del ${fechaHora(a.fechaPresentacion)}`), badge(estado.texto, estado.clase));
  caja.append(cab, el('p', 'moderacion__cita', a.descargo));
  if (a.evidencias?.length) caja.appendChild(botonesEvidencias(a.evidencias));
  if (a.fechaResolucion) {
    caja.appendChild(el('p', 'texto-suave', `Resuelta el ${fechaHora(a.fechaResolucion)}`
      + (a.observacionResolucion ? `: ${a.observacionResolucion}` : '')));
  }
  return caja;
}

function formularioApelacion(sancion, alApelar) {
  const form = el('form', 'moderacion__form');
  form.noValidate = true;
  const campo = el('div', 'campo');
  const etiqueta = el('label', '', 'Tu descargo');
  const descargo = el('textarea');
  descargo.rows = 4;
  descargo.maxLength = MAXIMO_DESCARGO;
  descargo.id = `descargo-${sancion.id}`;
  etiqueta.htmlFor = descargo.id;
  const ayuda = el('div', 'campo__ayuda', `Explica qué ocurrió (entre ${MINIMO_DESCARGO} y ${MAXIMO_DESCARGO} caracteres).`);
  const error = el('div', 'campo__error');
  error.hidden = true;
  campo.append(etiqueta, descargo, ayuda, error);
  const evidencias = campoEvidencias(`evidencias-${sancion.id}`);
  const enviar = el('button', 'btn btn--primario btn--pequeno', 'Enviar apelación');
  enviar.type = 'submit';
  const cancelar = el('button', 'btn btn--borde btn--pequeno', 'Cancelar');
  cancelar.type = 'button';
  const acciones = el('div', 'moderacion__acciones');
  acciones.append(enviar, cancelar);
  form.append(campo, evidencias.campo, acciones);
  cancelar.addEventListener('click', () => { form.hidden = true; });

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const texto = descargo.value.trim();
    const problema = texto.length < MINIMO_DESCARGO ? `El descargo debe tener al menos ${MINIMO_DESCARGO} caracteres`
      : validarEvidencias(evidencias.entrada.files);
    if (problema) {
      error.textContent = problema;
      error.hidden = false;
      descargo.classList.toggle('invalido', texto.length < MINIMO_DESCARGO);
      return;
    }
    error.hidden = true;
    enviar.disabled = true;
    enviar.textContent = 'Enviando…';
    try {
      await api.post(`/api/sanciones/${encodeURIComponent(sancion.id)}/apelaciones`,
        construirMultipart({ descargo: texto }, evidencias.entrada.files));
      exito('Apelación enviada: un administrador la revisará');
      await alApelar();
    } catch (err) {
      error.textContent = err.message ?? 'No se pudo enviar la apelación';
      error.hidden = false;
      toastError(err.message ?? 'No se pudo enviar la apelación');
      enviar.disabled = false;
      enviar.textContent = 'Enviar apelación';
    }
  });
  return form;
}

function itemSancion(s, alApelar) {
  const item = el('article', 'moderacion__item sancion');
  const estado = ETIQUETA_ESTADO_SANCION[s.estado] ?? { texto: s.estado, clase: '' };
  const cab = el('div', 'cola__cabecera');
  const fila = el('div', 'fila-badges');
  fila.append(badge(estado.texto, estado.clase));
  cab.append(el('strong', '', ETIQUETA_TIPO_SANCION[s.tipo] ?? s.tipo), fila);
  item.append(cab, el('p', 'texto-suave', vigencia(s)));
  if (s.tipoReporte || s.motivo) {
    item.appendChild(el('p', '', `Motivo: ${ETIQUETA_TIPO_REPORTE[s.tipoReporte] ?? s.tipoReporte ?? 'Reporte'}`
      + (s.motivo ? ` — ${s.motivo}` : '')));
  }
  s.apelaciones.forEach((a) => item.appendChild(apelacion(a)));

  if (s.puedeApelar) {
    const form = formularioApelacion(s, alApelar);
    form.hidden = true;
    const abrir = el('button', 'btn btn--secundario btn--pequeno', 'Apelar esta sanción');
    abrir.type = 'button';
    abrir.addEventListener('click', () => {
      form.hidden = !form.hidden;
      if (!form.hidden) form.querySelector('textarea').focus();
    });
    item.append(abrir, form);
  } else if (s.estado === 'EN_APELACION') {
    item.appendChild(el('p', 'texto-suave', 'Tu apelación está en revisión. Mientras tanto la sanción sigue vigente.'));
  }
  return item;
}

/**
 * Monta la lista de sanciones dentro de `contenedor` (`vacio` es el párrafo que se muestra si no hay ninguna).
 * Devuelve `{ recargar }`; `alCambiar` se invoca tras apelar con éxito.
 */
export function montarSanciones(contenedor, vacio, alCambiar) {
  async function recargar() {
    try {
      const sanciones = await api.get('/api/sanciones/mias');
      contenedor.replaceChildren(...sanciones.map((s) => itemSancion(s, async () => { await recargar(); alCambiar?.(); })));
      vacio.hidden = sanciones.length > 0;
      return sanciones;
    } catch (e) {
      toastError(e.message ?? 'No se pudieron cargar tus sanciones');
      return [];
    }
  }
  return { recargar };
}
