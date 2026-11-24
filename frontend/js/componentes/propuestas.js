// Propuestas y negociación sobre una solicitud: el estudiante dueño compara, contraoferta, selecciona o rechaza;
// el tutor envía, modifica (también para responder a una contraoferta) y retira la suya.
// Todo el contenido dinámico se construye con textContent (nunca innerHTML).
import { exito, error as toastError } from './alertas.js';
import { api, ErrorApi } from '../servicios/api.js';
import { abrirConfirmacionPago } from './confirmacion.js';

export const ETIQUETA_TIPO_PROPUESTA = {
  ACEPTA_PRECIO: 'Acepta tu precio',
  CONTRAPROPUESTA_PRECIO: 'Contrapropuesta de precio',
  ALTERNATIVA_CONDICIONES: 'Alternativa de condiciones',
};
export const ETIQUETA_ESTADO_PROPUESTA = {
  ENVIADA: ['Enviada', 'badge--info'],
  CONTRAOFERTA_ESTUDIANTE: ['Contraoferta del estudiante', 'badge--advertencia'],
  ACEPTADA: ['Seleccionada', 'badge--exito'],
  DESCARTADA: ['Descartada', 'badge--error'],
  RETIRADA: ['Retirada', 'badge--error'],
};
const ETIQUETA_DURACION = {
  MIN_30: '30 min', MIN_45: '45 min', MIN_60: '60 min', MIN_90: '90 min', MIN_120: '120 min',
};
const ETIQUETA_MODALIDAD = { VIRTUAL: 'Virtual', PRESENCIAL: 'Presencial' };
const ESTADOS_ABIERTOS = ['PUBLICADA', 'PROPUESTAS_RECIBIDAS', 'NEGOCIACION_EN_CURSO'];
const PRECIO_MAXIMO = 5000000;

const formatoPesos = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
export const pesos = (valor) => formatoPesos.format(Number(valor));

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

function campo(id, etiqueta, control) {
  const div = el('div', 'campo');
  const label = el('label', '', etiqueta);
  label.htmlFor = id;
  control.id = id;
  const error = el('div', 'campo__error');
  error.dataset.errorDe = id;
  error.hidden = true;
  div.append(label, control, error);
  return div;
}

function select(opciones, valor) {
  const s = document.createElement('select');
  opciones.forEach(([v, t]) => s.appendChild(new Option(t, v)));
  if (valor) s.value = valor;
  return s;
}

function marcarError(formulario, id, mensaje) {
  const caja = formulario.querySelector(`[data-error-de="${id}"]`);
  if (caja) { caja.textContent = mensaje; caja.hidden = false; }
  const control = formulario.querySelector(`#${id}`);
  if (control) { control.classList.add('invalido'); control.setAttribute('aria-invalid', 'true'); }
}

function limpiarErrores(formulario) {
  formulario.querySelectorAll('[data-error-de]').forEach((n) => { n.hidden = true; n.textContent = ''; });
  formulario.querySelectorAll('.invalido').forEach((n) => { n.classList.remove('invalido'); n.removeAttribute('aria-invalid'); });
}

function mensajeDe(e) {
  return e instanceof ErrorApi || e?.message ? e.message : 'Ocurrió un error inesperado';
}

function badgeEstado(estado) {
  const [texto, clase] = ETIQUETA_ESTADO_PROPUESTA[estado] ?? [estado, ''];
  return el('span', `badge ${clase}`.trim(), texto);
}

function condiciones(p) {
  return `${ETIQUETA_DURACION[p.duracion] ?? p.duracion} · ${ETIQUETA_MODALIDAD[p.modalidad] ?? p.modalidad}`;
}

function valorarTutor(p) {
  const partes = [];
  partes.push(p.tutorPromedioCalificacion != null
    ? `★ ${Number(p.tutorPromedioCalificacion).toFixed(2)}` : 'Sin calificaciones aún');
  partes.push(`${p.tutorServiciosCompletados} servicio${p.tutorServiciosCompletados === 1 ? '' : 's'}`);
  return partes.join(' · ');
}

/**
 * Formulario de propuesta del tutor (alta o edición). `inicial` es la propuesta existente o null.
 * `alGuardar(cuerpo)` devuelve una promesa; `alCancelar` es opcional.
 */
function formularioPropuesta(solicitud, inicial, { alGuardar, alCancelar, textoEnviar }) {
  const form = el('form', 'form-propuesta');
  form.noValidate = true;
  const tipo = select(Object.entries(ETIQUETA_TIPO_PROPUESTA), inicial?.tipo ?? 'ACEPTA_PRECIO');
  const precio = el('input');
  precio.type = 'number';
  precio.min = '1';
  precio.max = String(PRECIO_MAXIMO);
  precio.step = '500';
  precio.inputMode = 'numeric';
  precio.value = Math.round(Number(inicial?.precioCOP ?? solicitud.precioInicialCOP));
  const duracion = select(Object.entries(ETIQUETA_DURACION).map(([v, t]) => [v, t]), inicial?.duracion ?? solicitud.duracion);
  const modalidad = select(Object.entries(ETIQUETA_MODALIDAD), inicial?.modalidad ?? solicitud.modalidad);
  const nota = el('textarea');
  nota.rows = 2;
  nota.maxLength = 500;
  nota.value = inicial?.nota ?? '';

  const cPrecio = campo('prop-precio', 'Precio (COP)', precio);
  const cDuracion = campo('prop-duracion', 'Duración', duracion);
  const cModalidad = campo('prop-modalidad', 'Modalidad', modalidad);
  const rejilla = el('div', 'rejilla-form');
  rejilla.append(cPrecio, cDuracion, cModalidad);

  const ayuda = el('p', 'texto-suave');
  const ajustar = () => {
    const t = tipo.value;
    cPrecio.hidden = t === 'ACEPTA_PRECIO';
    cDuracion.hidden = t !== 'ALTERNATIVA_CONDICIONES';
    cModalidad.hidden = t !== 'ALTERNATIVA_CONDICIONES';
    ayuda.textContent = t === 'ACEPTA_PRECIO'
      ? `Aceptas el precio (${pesos(solicitud.precioInicialCOP)}), la duración y la modalidad de la solicitud.`
      : t === 'CONTRAPROPUESTA_PRECIO'
        ? 'Solo cambia el precio; la duración y la modalidad se mantienen.'
        : 'Puedes proponer otra duración o modalidad (y, si quieres, otro precio).';
  };
  tipo.addEventListener('change', ajustar);
  ajustar();

  const enviar = el('button', 'btn btn--primario', textoEnviar);
  enviar.type = 'submit';
  const acciones = el('div', 'barra-acciones');
  acciones.appendChild(enviar);
  if (alCancelar) acciones.appendChild(boton('Cancelar', 'btn btn--borde', alCancelar));
  form.append(campo('prop-tipo', 'Tipo de propuesta', tipo), ayuda, rejilla,
    campo('prop-nota', 'Nota para el estudiante (opcional)', nota), acciones);

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    limpiarErrores(form);
    const cuerpo = { tipo: tipo.value, nota: nota.value.trim() || null };
    if (tipo.value !== 'ACEPTA_PRECIO') {
      const valor = Number(precio.value);
      if (!precio.value || valor <= 0) { marcarError(form, 'prop-precio', 'Indica un precio mayor que cero'); return; }
      if (valor > PRECIO_MAXIMO) { marcarError(form, 'prop-precio', `El precio no puede superar ${pesos(PRECIO_MAXIMO)}`); return; }
      if (tipo.value === 'CONTRAPROPUESTA_PRECIO' && valor === Math.round(Number(solicitud.precioInicialCOP))) {
        marcarError(form, 'prop-precio', 'Coincide con el precio de la solicitud; elige «Acepta tu precio»');
        return;
      }
      cuerpo.precioCOP = valor;
    }
    if (tipo.value === 'ALTERNATIVA_CONDICIONES') {
      cuerpo.duracion = duracion.value;
      cuerpo.modalidad = modalidad.value;
      if (duracion.value === solicitud.duracion && modalidad.value === solicitud.modalidad) {
        marcarError(form, 'prop-duracion', 'Una alternativa debe cambiar la duración o la modalidad');
        return;
      }
    }
    enviar.disabled = true;
    try {
      await alGuardar(cuerpo);
    } catch (err) {
      if (err instanceof ErrorApi && err.detalles?.length) {
        err.detalles.forEach((d) => marcarError(form, { precioCOP: 'prop-precio', nota: 'prop-nota', tipo: 'prop-tipo' }[d.campo] ?? 'prop-tipo', d.mensaje));
      }
      toastError(mensajeDe(err));
    } finally {
      enviar.disabled = false;
    }
  });
  return form;
}

/** Pequeño formulario de contraoferta del estudiante. */
function formularioContraoferta(propuesta, { alEnviar, alCancelar }) {
  const form = el('form', 'form-propuesta');
  form.noValidate = true;
  const precio = el('input');
  precio.type = 'number';
  precio.min = '1';
  precio.max = String(PRECIO_MAXIMO);
  precio.step = '500';
  precio.inputMode = 'numeric';
  precio.placeholder = `Menor que ${Math.round(Number(propuesta.precioCOP))}`;
  const nota = el('input');
  nota.type = 'text';
  nota.maxLength = 300;
  const enviar = el('button', 'btn btn--primario btn--pequeno', 'Enviar contraoferta');
  enviar.type = 'submit';
  const acciones = el('div', 'barra-acciones');
  acciones.append(enviar, boton('Cancelar', 'btn btn--borde btn--pequeno', alCancelar));
  const rejilla = el('div', 'rejilla-form');
  rejilla.append(campo(`contra-precio-${propuesta.id}`, 'Tu precio (COP)', precio),
    campo(`contra-nota-${propuesta.id}`, 'Mensaje (opcional)', nota));
  form.append(rejilla, acciones);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    limpiarErrores(form);
    const idPrecio = `contra-precio-${propuesta.id}`;
    const valor = Number(precio.value);
    if (!precio.value || valor <= 0) { marcarError(form, idPrecio, 'Indica un precio mayor que cero'); return; }
    if (valor > PRECIO_MAXIMO) { marcarError(form, idPrecio, `El precio no puede superar ${pesos(PRECIO_MAXIMO)}`); return; }
    if (valor === Math.round(Number(propuesta.precioCOP))) {
      marcarError(form, idPrecio, 'Es igual al precio propuesto; si te conviene, selecciona la propuesta');
      return;
    }
    enviar.disabled = true;
    try {
      await alEnviar({ precioCOP: valor, nota: nota.value.trim() || null });
    } catch (err) {
      toastError(mensajeDe(err));
    } finally {
      enviar.disabled = false;
    }
  });
  return form;
}

/**
 * Pinta la sección de propuestas dentro de `contenedor`.
 * @param {HTMLElement} contenedor
 * @param {object} solicitud  detalle de la solicitud (`/api/solicitudes/{id}`)
 * @param {{usuarioId: string, rol: string, alCambiar?: Function}} contexto
 * @returns {Promise<void>}
 */
export async function montarPropuestas(contenedor, solicitud, { usuarioId, rol, alCambiar }) {
  const esDueno = rol === 'ESTUDIANTE' && solicitud.estudianteId === usuarioId;
  const esTutor = rol === 'TUTOR' && solicitud.estudianteId !== usuarioId;
  contenedor.replaceChildren();
  if (!esDueno && !esTutor) return;

  const abierta = ESTADOS_ABIERTOS.includes(solicitud.estado);
  let propuestas;
  try {
    propuestas = await api.get(`/api/solicitudes/${encodeURIComponent(solicitud.id)}/propuestas`);
  } catch (e) {
    contenedor.append(el('h3', '', 'Propuestas'), el('p', 'texto-suave', mensajeDe(e)));
    return;
  }

  const recargar = async () => {
    const actual = await api.get(`/api/solicitudes/${encodeURIComponent(solicitud.id)}`);
    Object.assign(solicitud, actual);
    await montarPropuestas(contenedor, solicitud, { usuarioId, rol, alCambiar });
    alCambiar?.();
  };
  const ejecutar = async (accion, mensaje) => {
    try {
      await accion();
      if (mensaje) exito(mensaje);
      await recargar();
    } catch (e) {
      toastError(mensajeDe(e));
      await recargar().catch(() => {});
    }
  };

  const titulo = el('h3', '', esDueno ? `Propuestas recibidas (${propuestas.length})` : 'Tu propuesta');
  contenedor.appendChild(titulo);

  if (esDueno) {
    pintarParaEstudiante(contenedor, solicitud, propuestas, abierta, ejecutar, recargar);
  } else {
    pintarParaTutor(contenedor, solicitud, propuestas, abierta, ejecutar);
    if (solicitud.servicioId) contenedor.appendChild(enlaceServicio(solicitud.servicioId));
  }
}

function enlaceServicio(servicioId) {
  const a = el('a', 'btn btn--secundario btn--pequeno', 'Ver el servicio y el pago');
  a.href = `negociacion.html?id=${encodeURIComponent(servicioId)}`;
  return a;
}

function pintarParaEstudiante(contenedor, solicitud, propuestas, abierta, ejecutar, recargar) {
  if (!propuestas.length) {
    contenedor.appendChild(el('p', 'texto-suave', 'Aún no hay propuestas. Te avisaremos cuando un tutor responda.'));
    return;
  }
  const activas = propuestas.filter((p) => ['ENVIADA', 'CONTRAOFERTA_ESTUDIANTE', 'ACEPTADA'].includes(p.estado));
  const menorPrecio = Math.min(...activas.map((p) => Number(p.precioCOP)));
  const lista = el('div', 'propuestas');
  [...propuestas].sort((a, b) => Number(a.precioCOP) - Number(b.precioCOP)).forEach((p) => {
    const tarjeta = el('article', `propuesta${p.estado === 'ACEPTADA' ? ' propuesta--seleccionada' : ''}${activas.includes(p) ? '' : ' propuesta--inactiva'}`);
    const cab = el('div', 'oferta');
    const quien = el('div');
    const enlace = el('a', 'propuesta__tutor', p.tutorNombre);
    enlace.href = `tutores.html?id=${encodeURIComponent(p.tutorId)}`;
    const insignias = el('span', 'fila-badges');
    if (p.tutorVerificado) insignias.appendChild(el('span', 'badge badge--exito', 'Verificado'));
    insignias.appendChild(badgeEstado(p.estado));
    if (activas.includes(p) && activas.length > 1 && Number(p.precioCOP) === menorPrecio) {
      insignias.appendChild(el('span', 'badge badge--info', 'Mejor precio'));
    }
    quien.append(enlace, el('p', 'texto-suave', valorarTutor(p)));
    cab.append(quien, el('strong', 'propuesta__precio', pesos(p.precioCOP)));
    tarjeta.append(cab, insignias, el('p', 'texto-suave', `${ETIQUETA_TIPO_PROPUESTA[p.tipo] ?? p.tipo} · ${condiciones(p)}`));
    if (p.nota) tarjeta.appendChild(el('p', 'propuesta__nota', `«${p.nota}»`));
    if (p.estado === 'CONTRAOFERTA_ESTUDIANTE') {
      tarjeta.appendChild(el('p', 'texto-suave', `Esperando respuesta del tutor a tu contraoferta de ${pesos(p.contraofertaCOP)}.`));
    }
    if (p.estado === 'ACEPTADA') {
      const formalizada = solicitud.estado === 'FORMALIZADA';
      tarjeta.appendChild(el('p', 'texto-suave', formalizada
        ? 'Servicio confirmado: el pago quedó retenido hasta que finalice la tutoría.'
        : 'Seleccionaste esta propuesta. El servicio se formaliza al confirmarlo con el pago.'));
      const acciones = el('div', 'propuesta__acciones');
      if (!formalizada && solicitud.estado === 'TUTOR_SELECCIONADO') {
        acciones.appendChild(boton('Autorizar pago y confirmar', 'btn btn--primario btn--pequeno', () => abrirConfirmacionPago({
          solicitud, propuesta: p,
          alConfirmar: (servicio) => { exito('Tutoría confirmada y pago retenido'); window.location.href = `negociacion.html?id=${encodeURIComponent(servicio.id)}`; },
          alFallar: () => recargar().catch(() => {}),
        })));
      }
      if (solicitud.servicioId) acciones.appendChild(enlaceServicio(solicitud.servicioId));
      if (acciones.children.length) tarjeta.appendChild(acciones);
    }
    if (abierta && p.estado === 'ENVIADA') {
      const acciones = el('div', 'propuesta__acciones');
      const zona = el('div');
      acciones.append(
        boton('Seleccionar', 'btn btn--primario btn--pequeno', () => ejecutar(
          () => api.post(`/api/propuestas/${encodeURIComponent(p.id)}/seleccionar`), 'Propuesta seleccionada')),
        boton('Contraofertar', 'btn btn--secundario btn--pequeno', () => {
          zona.replaceChildren(formularioContraoferta(p, {
            alEnviar: (cuerpo) => ejecutar(
              () => api.post(`/api/propuestas/${encodeURIComponent(p.id)}/contraofertar`, cuerpo), 'Contraoferta enviada'),
            alCancelar: () => zona.replaceChildren(),
          }));
        }),
        boton('Rechazar', 'btn btn--borde btn--pequeno', () => ejecutar(
          () => api.post(`/api/propuestas/${encodeURIComponent(p.id)}/rechazar`), 'Propuesta rechazada')),
      );
      tarjeta.append(acciones, zona);
    } else if (abierta && p.estado === 'CONTRAOFERTA_ESTUDIANTE') {
      const acciones = el('div', 'propuesta__acciones');
      acciones.appendChild(boton('Rechazar', 'btn btn--borde btn--pequeno', () => ejecutar(
        () => api.post(`/api/propuestas/${encodeURIComponent(p.id)}/rechazar`), 'Propuesta rechazada')));
      tarjeta.appendChild(acciones);
    }
    lista.appendChild(tarjeta);
  });
  contenedor.appendChild(lista);
}

function pintarParaTutor(contenedor, solicitud, propuestas, abierta, ejecutar) {
  const activa = propuestas.find((p) => ['ENVIADA', 'CONTRAOFERTA_ESTUDIANTE', 'ACEPTADA'].includes(p.estado));
  const historial = propuestas.filter((p) => p !== activa);

  const zona = el('div');
  if (activa) {
    const tarjeta = el('article', `propuesta${activa.estado === 'ACEPTADA' ? ' propuesta--seleccionada' : ''}`);
    const cab = el('div', 'oferta');
    const estado = el('span', 'fila-badges');
    estado.appendChild(badgeEstado(activa.estado));
    cab.append(estado, el('strong', 'propuesta__precio', pesos(activa.precioCOP)));
    tarjeta.append(cab, el('p', 'texto-suave', `${ETIQUETA_TIPO_PROPUESTA[activa.tipo] ?? activa.tipo} · ${condiciones(activa)}`));
    if (activa.nota) tarjeta.appendChild(el('p', 'propuesta__nota', `«${activa.nota}»`));
    if (activa.estado === 'CONTRAOFERTA_ESTUDIANTE') {
      tarjeta.appendChild(el('p', 'propuesta__aviso',
        `El estudiante propone ${pesos(activa.contraofertaCOP)}. Responde con una propuesta actualizada o retírala.`));
    }
    if (activa.estado === 'ACEPTADA') {
      tarjeta.appendChild(el('p', 'texto-suave', solicitud.estado === 'FORMALIZADA'
        ? 'El estudiante confirmó el servicio y el pago quedó retenido.'
        : 'El estudiante seleccionó tu propuesta; falta que confirme el servicio con el pago.'));
    }
    if (abierta && activa.estado !== 'ACEPTADA') {
      const acciones = el('div', 'propuesta__acciones');
      acciones.append(
        boton(activa.estado === 'CONTRAOFERTA_ESTUDIANTE' ? 'Responder contraoferta' : 'Modificar', 'btn btn--secundario btn--pequeno', () => {
          zona.replaceChildren(formularioPropuesta(solicitud, activa, {
            textoEnviar: 'Guardar y reenviar',
            alGuardar: (cuerpo) => ejecutar(
              () => api.put(`/api/propuestas/${encodeURIComponent(activa.id)}`, cuerpo), 'Propuesta actualizada'),
            alCancelar: () => zona.replaceChildren(),
          }));
        }),
        boton('Retirar', 'btn btn--peligro btn--pequeno', () => ejecutar(
          () => api.post(`/api/propuestas/${encodeURIComponent(activa.id)}/retirar`), 'Propuesta retirada')),
      );
      tarjeta.appendChild(acciones);
    }
    contenedor.append(tarjeta, zona);
  } else if (abierta) {
    contenedor.append(el('p', 'texto-suave', 'Aún no has propuesto. Elige si aceptas el precio o negocias.'),
      formularioPropuesta(solicitud, null, {
        textoEnviar: 'Enviar propuesta',
        alGuardar: (cuerpo) => ejecutar(
          () => api.post(`/api/solicitudes/${encodeURIComponent(solicitud.id)}/propuestas`, cuerpo), 'Propuesta enviada'),
      }));
  } else {
    contenedor.appendChild(el('p', 'texto-suave', 'Esta solicitud ya no admite propuestas.'));
  }

  if (historial.length) {
    contenedor.appendChild(el('h4', '', 'Historial'));
    const lista = el('ul', 'propuesta__historial');
    historial.forEach((p) => {
      const li = el('li', '', `${pesos(p.precioCOP)} · ${ETIQUETA_TIPO_PROPUESTA[p.tipo] ?? p.tipo} · `);
      li.appendChild(badgeEstado(p.estado));
      lista.appendChild(li);
    });
    contenedor.appendChild(lista);
  }
}
