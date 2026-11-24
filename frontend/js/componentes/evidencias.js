// Utilidades compartidas de reportes, sanciones y apelaciones: etiquetas, validación de evidencias y botones de descarga.
// Todo el contenido dinámico se construye con textContent (nunca innerHTML).
import { descargarArchivo } from '../servicios/api.js';
import { error as toastError } from './alertas.js';

export const MAXIMO_EVIDENCIAS = 5;
export const TAMANO_MAXIMO_BYTES = 10 * 1024 * 1024;
export const EXTENSIONES_PERMITIDAS = ['pdf', 'png', 'jpg', 'jpeg', 'docx'];

export const ETIQUETA_TIPO_REPORTE = {
  INASISTENCIA: 'Inasistencia',
  COBRO_EXTERNO: 'Cobro fuera de la plataforma',
  FRAUDE_ACADEMICO: 'Fraude académico',
  CONDUCTA_ABUSIVA: 'Conducta abusiva',
  ACOSO: 'Acoso',
  FALSEDAD_CREDENCIALES: 'Credenciales falsas',
  DESACUERDO_ACADEMICO: 'Desacuerdo académico',
};
/** Tipos que solo se pueden reportar sobre un servicio concreto (igual que en el servidor). */
export const TIPOS_QUE_EXIGEN_SERVICIO = ['INASISTENCIA', 'COBRO_EXTERNO', 'DESACUERDO_ACADEMICO'];
export const ETIQUETA_ESTADO_REPORTE = {
  RADICADO: { texto: 'Radicado', clase: 'badge--info' },
  EN_REVISION: { texto: 'En revisión', clase: 'badge--advertencia' },
  RESUELTO: { texto: 'Resuelto', clase: 'badge--exito' },
  DESESTIMADO: { texto: 'Desestimado', clase: '' },
};
export const ETIQUETA_DECISION = {
  A_FAVOR_REPORTANTE: 'A favor de quien reportó',
  A_FAVOR_DENUNCIADO: 'A favor de la persona reportada',
  DESESTIMADO: 'Reporte desestimado',
};
export const ETIQUETA_ACCION_PAGO = {
  REEMBOLSAR: 'Reembolso al estudiante (servicio cancelado)',
  LIBERAR: 'Pago liberado al tutor (servicio finalizado)',
  NINGUNA: 'Sin movimiento de pago',
};
export const ETIQUETA_TIPO_SANCION = {
  ADVERTENCIA: 'Advertencia',
  SUSPENSION_TEMPORAL: 'Suspensión temporal',
  BLOQUEO_PERMANENTE: 'Bloqueo permanente',
};
export const ETIQUETA_ESTADO_SANCION = {
  VIGENTE: { texto: 'Vigente', clase: 'badge--error' },
  EN_APELACION: { texto: 'En apelación', clase: 'badge--advertencia' },
  RATIFICADA: { texto: 'Ratificada', clase: 'badge--error' },
  REVOCADA: { texto: 'Revocada', clase: 'badge--exito' },
  CUMPLIDA: { texto: 'Cumplida', clase: '' },
};
export const ETIQUETA_RESULTADO_APELACION = {
  PENDIENTE: { texto: 'Pendiente de resolución', clase: 'badge--advertencia' },
  RATIFICADA: { texto: 'Sanción ratificada', clase: 'badge--error' },
  REVOCADA: { texto: 'Sanción revocada', clase: 'badge--exito' },
};

export function el(etiqueta, clase, texto) {
  const nodo = document.createElement(etiqueta);
  if (clase) nodo.className = clase;
  if (texto !== undefined && texto !== null) nodo.textContent = texto;
  return nodo;
}

export function badge(texto, clase = '') {
  return el('span', `badge ${clase}`.trim(), texto);
}

export function fechaHora(iso) {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
}

export function pesos(valor) {
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(Number(valor));
}

/**
 * Valida los archivos de un `<input type="file" multiple>`: máximo 5, formatos PDF/PNG/JPG/DOCX y 10 MB cada uno.
 * Devuelve el mensaje de error o `null` si todo está bien.
 */
export function validarEvidencias(archivos) {
  const lista = [...(archivos ?? [])];
  if (lista.length > MAXIMO_EVIDENCIAS) return `Adjunta como máximo ${MAXIMO_EVIDENCIAS} evidencias`;
  for (const f of lista) {
    const extension = f.name.split('.').pop().toLowerCase();
    if (!EXTENSIONES_PERMITIDAS.includes(extension)) return `«${f.name}»: formato no permitido (usa PDF, PNG, JPG o DOCX)`;
    if (f.size > TAMANO_MAXIMO_BYTES) return `«${f.name}» supera el máximo de 10 MB`;
    if (f.size === 0) return `«${f.name}» está vacío`;
  }
  return null;
}

/** Cuerpo multipart: parte `datos` (JSON) y las partes `evidencias`. */
export function construirMultipart(datos, archivos) {
  const form = new FormData();
  form.append('datos', new Blob([JSON.stringify(datos)], { type: 'application/json' }));
  [...(archivos ?? [])].forEach((f) => form.append('evidencias', f));
  return form;
}

/** Botones para descargar evidencias (el acceso lo valida el servidor con el token). */
export function botonesEvidencias(ids, prefijo = 'Evidencia') {
  const fila = el('div', 'fila-badges');
  (ids ?? []).forEach((id, i) => {
    const b = el('button', 'btn btn--borde btn--pequeno', `📎 ${prefijo} ${i + 1}`);
    b.type = 'button';
    b.addEventListener('click', async () => {
      try { await descargarArchivo(id, `${prefijo.toLowerCase()}-${i + 1}`); } catch (e) { toastError(e.message); }
    });
    fila.appendChild(b);
  });
  return fila;
}

/** Campo `<label>` + `<input type=file multiple>` con su ayuda; devuelve { campo, entrada }. */
export function campoEvidencias(idEntrada) {
  const campo = el('div', 'campo');
  const etiqueta = el('label', '', 'Evidencias (opcional)');
  etiqueta.htmlFor = idEntrada;
  const entrada = el('input');
  entrada.type = 'file';
  entrada.id = idEntrada;
  entrada.multiple = true;
  entrada.accept = '.pdf,.png,.jpg,.jpeg,.docx';
  const ayuda = el('div', 'campo__ayuda', `Hasta ${MAXIMO_EVIDENCIAS} archivos PDF, PNG, JPG o DOCX de 10 MB cada uno.`);
  campo.append(etiqueta, entrada, ayuda);
  return { campo, entrada };
}
