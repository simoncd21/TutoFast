// Perfil: datos de la cuenta, perfiles de estudiante y tutor, y activación del otro rol.
import { iniciarEncabezado } from '../componentes/encabezado.js';
import { exito, error as toastError } from '../componentes/alertas.js';
import { api, ErrorApi, descargarArchivo } from '../servicios/api.js';
import { enlazarSelectsAcademicos, listarAsignaturas, consultarAsignatura } from '../servicios/catalogo.js';
import { crearElementoNotificacion, avisarCambioNotificaciones, EVENTO_NOTIFICACIONES } from '../componentes/campana.js';
import { exigirSesion, refrescarUsuario, rolActivo, ETIQUETA_ROL } from '../sesion.js';

iniciarEncabezado();
document.getElementById('anio').textContent = new Date().getFullYear();

const aviso = document.getElementById('aviso');
const formEstudiante = document.getElementById('form-estudiante');
const formTutor = document.getElementById('form-tutor');
const botonRol = document.getElementById('boton-activar-rol');
const formSoporte = document.getElementById('form-soporte');
const listaSoportes = document.getElementById('lista-soportes');
const listaNotificaciones = document.getElementById('lista-notificaciones');
const TAMANO_MAXIMO_BYTES = 10 * 1024 * 1024;
const EXTENSIONES_PERMITIDAS = ['pdf', 'png', 'jpg', 'jpeg', 'docx'];
const ETIQUETA_TIPO_SOPORTE = {
  CERTIFICADO_MONITORIA: 'Certificado de monitoría',
  HISTORIAL_CALIFICACIONES: 'Historial de calificaciones',
  CARNE_ESTUDIANTIL: 'Carné estudiantil',
  OTRO: 'Otro',
};
const ESTADO_SOPORTE = {
  EN_REVISION: { texto: 'En revisión', clase: 'badge--advertencia' },
  APROBADO: { texto: 'Aprobado', clase: 'badge--exito' },
  RECHAZADO: { texto: 'Rechazado', clase: 'badge--error' },
};

let perfilActual = null;

// ---- Selects académicos (catálogo) ----
const selectsEstudiante = enlazarSelectsAcademicos({
  universidad: document.getElementById('est-universidad'),
  facultad: document.getElementById('est-facultad'),
  programa: document.getElementById('est-programa'),
});

// Asignaturas que domina el tutor: id → { codigo, nombre }. Se pueden combinar varios programas.
const asignaturasElegidas = new Map();
const contenedorAsignaturas = document.getElementById('tut-asignaturas-programa');
const listaElegidas = document.getElementById('tut-asignaturas-elegidas');
let programaTutorMostrado = '';

const etiquetaAsignatura = (a) => (a.codigo ? `${a.codigo} · ${a.nombre}` : a.nombre);

function pintarAsignaturasElegidas() {
  listaElegidas.replaceChildren(...[...asignaturasElegidas.entries()].map(([id, a]) => {
    const item = document.createElement('li');
    item.className = 'badge badge--info';
    const texto = document.createElement('span');
    texto.textContent = etiquetaAsignatura(a);
    const quitar = document.createElement('button');
    quitar.type = 'button';
    quitar.className = 'badge__quitar';
    quitar.textContent = '×';
    quitar.setAttribute('aria-label', `Quitar ${etiquetaAsignatura(a)}`);
    quitar.addEventListener('click', () => {
      asignaturasElegidas.delete(id);
      pintarAsignaturasElegidas();
      const casilla = contenedorAsignaturas.querySelector(`input[value="${CSS.escape(id)}"]`);
      if (casilla) casilla.checked = false;
    });
    item.append(texto, quitar);
    return item;
  }));
}

async function mostrarAsignaturasDelPrograma(programaId) {
  programaTutorMostrado = programaId;
  if (!programaId) { contenedorAsignaturas.replaceChildren(); return; }
  try {
    const asignaturas = await listarAsignaturas(programaId);
    if (programaTutorMostrado !== programaId) return; // llegó tarde: la persona ya cambió de programa
    if (asignaturas.length === 0) {
      const vacio = document.createElement('p');
      vacio.className = 'texto-suave';
      vacio.textContent = 'Este programa aún no tiene asignaturas activas.';
      contenedorAsignaturas.replaceChildren(vacio);
      return;
    }
    contenedorAsignaturas.replaceChildren(...asignaturas.map((a) => {
      const etiqueta = document.createElement('label');
      etiqueta.className = 'casilla';
      const casilla = document.createElement('input');
      casilla.type = 'checkbox';
      casilla.value = a.id;
      casilla.checked = asignaturasElegidas.has(a.id);
      casilla.addEventListener('change', () => {
        if (casilla.checked) asignaturasElegidas.set(a.id, { codigo: a.codigo, nombre: a.nombre });
        else asignaturasElegidas.delete(a.id);
        pintarAsignaturasElegidas();
      });
      const texto = document.createElement('span');
      texto.textContent = etiquetaAsignatura(a);
      etiqueta.append(casilla, texto);
      return etiqueta;
    }));
  } catch (err) {
    toastError(err.message ?? 'No se pudieron cargar las asignaturas');
  }
}

const selectsTutor = enlazarSelectsAcademicos({
  universidad: document.getElementById('tut-universidad'),
  facultad: document.getElementById('tut-facultad'),
  programa: document.getElementById('tut-programa'),
}, { alCambiarPrograma: mostrarAsignaturasDelPrograma });

/** Carga el catálogo y resuelve el nombre de las asignaturas que el tutor ya tenía guardadas. */
async function iniciarAcademicoTutor(ids) {
  asignaturasElegidas.clear();
  contenedorAsignaturas.replaceChildren();
  selectsTutor.iniciar();
  const resueltas = await Promise.allSettled(ids.map((id) => consultarAsignatura(id)));
  ids.forEach((id, i) => {
    const r = resueltas[i];
    asignaturasElegidas.set(id, r.status === 'fulfilled'
      ? { codigo: r.value.asignatura.codigo, nombre: r.value.asignatura.nombre }
      : { codigo: '', nombre: 'Asignatura no disponible' });
  });
  pintarAsignaturasElegidas();
}

function mostrarAviso(mensaje, tipo = 'info') {
  aviso.textContent = mensaje;
  aviso.className = `alerta alerta--${tipo}`;
  aviso.hidden = false;
}

function limpiarErrores(formulario) {
  formulario.querySelectorAll('[data-error-de]').forEach((el) => { el.hidden = true; el.textContent = ''; });
  formulario.querySelectorAll('.invalido').forEach((el) => {
    el.classList.remove('invalido');
    el.removeAttribute('aria-invalid');
  });
}

function marcarError(formulario, campo, mensaje) {
  const contenedor = formulario.querySelector(`[data-error-de="${campo}"]`);
  if (contenedor) {
    contenedor.textContent = mensaje;
    contenedor.hidden = false;
  }
  const control = formulario.elements[campo];
  const primero = control instanceof RadioNodeList ? control[0] : control;
  if (primero?.classList) {
    primero.classList.add('invalido');
    primero.setAttribute('aria-invalid', 'true');
  }
}

function erroresServidor(formulario, e) {
  if (e instanceof ErrorApi && e.detalles?.length) {
    e.detalles.forEach((d) => marcarError(formulario, d.campo, d.mensaje));
    formulario.querySelector('.invalido')?.focus();
  }
  toastError(e.message ?? 'Ocurrió un error inesperado');
}

async function guardando(boton, accion) {
  const texto = boton.textContent;
  boton.disabled = true;
  boton.textContent = 'Guardando…';
  try {
    return await accion();
  } finally {
    boton.disabled = false;
    boton.textContent = texto;
  }
}

function formatearFecha(iso) {
  const fecha = new Date(iso);
  return Number.isNaN(fecha.getTime())
    ? '—'
    : fecha.toLocaleDateString('es-CO', { day: 'numeric', month: 'long', year: 'numeric' });
}

function pintarDatos(p) {
  document.getElementById('dato-nombre').textContent = p.nombre;
  document.getElementById('dato-correo').textContent = p.correo;
  document.getElementById('dato-documento').textContent = p.documento ?? '—';
  document.getElementById('dato-registro').textContent = formatearFecha(p.fechaRegistro);
  const estado = document.getElementById('dato-estado');
  estado.textContent = p.estadoCuenta;
  estado.className = `badge ${p.estadoCuenta === 'ACTIVA' ? 'badge--exito' : 'badge--error'}`;
  const roles = document.getElementById('dato-roles');
  roles.replaceChildren(...p.roles.map((rol) => {
    const b = document.createElement('span');
    b.className = rol === rolActivo() ? 'badge badge--info' : 'badge';
    b.textContent = ETIQUETA_ROL[rol] ?? rol;
    return b;
  }));
}

function pintarEstudiante(p) {
  formEstudiante.elements.semestre.value = p.perfilEstudiante?.semestre ?? '';
  selectsEstudiante.iniciar(p.perfilEstudiante?.programaId ?? null);
}

function pintarTutor(p) {
  const t = p.perfilTutor;
  const f = formTutor.elements;
  f.presentacion.value = t?.presentacion ?? '';
  f.tipoTutor.value = t?.tipoTutor ?? 'PAR';
  f.formacion.value = t?.formacion ?? '';
  f.experiencia.value = t?.experiencia ?? '';
  f.areasDeInteres.value = (t?.areasDeInteres ?? []).join(', ');
  f.disponibilidad.value = t?.disponibilidad ?? '';
  f.cuentaBancariaCobro.value = t?.cuentaBancariaCobro ?? '';
  const modalidades = new Set(t?.modalidades ?? []);
  formTutor.querySelectorAll('input[name="modalidades"]').forEach((c) => { c.checked = modalidades.has(c.value); });

  const etiqueta = document.getElementById('tutor-verificacion');
  etiqueta.textContent = t?.etiquetaVerificacion ?? 'Tutor Registrado (No Verificado)';
  etiqueta.className = `badge ${t?.nivelVerificacion === 'VERIFICADO' ? 'badge--exito' : 'badge--advertencia'}`;
}

function pintarActivacionRol(p) {
  const seccion = document.getElementById('seccion-roles');
  const faltante = ['ESTUDIANTE', 'TUTOR'].find((r) => !p.roles.includes(r));
  if (!faltante) { seccion.hidden = true; return; }
  const tienes = p.roles.includes('ESTUDIANTE') ? 'estudiante' : 'tutor';
  document.getElementById('roles-texto').textContent = faltante === 'TUTOR'
    ? `Hoy usas TutoFast como ${tienes}. Activa el rol de tutor para ofrecer tus conocimientos y recibir solicitudes.`
    : `Hoy usas TutoFast como ${tienes}. Activa el rol de estudiante para publicar solicitudes de tutoría.`;
  botonRol.textContent = faltante === 'TUTOR' ? 'Activar rol de tutor' : 'Activar rol de estudiante';
  botonRol.dataset.rol = faltante;
  seccion.hidden = false;
}

function pintar(p) {
  perfilActual = p;
  pintarDatos(p);

  const contenedor = document.getElementById('secciones');
  const activo = rolActivo();
  ['ESTUDIANTE', 'TUTOR'].forEach((rol) => {
    const seccion = document.getElementById(`seccion-${rol}`);
    const tiene = p.roles.includes(rol);
    seccion.hidden = !tiene;
    if (tiene && rol === activo) contenedor.prepend(seccion); // el rol activo se muestra primero
  });
  if (p.roles.includes('ESTUDIANTE')) pintarEstudiante(p);
  if (p.roles.includes('TUTOR')) {
    pintarTutor(p);
    iniciarAcademicoTutor(p.perfilTutor?.asignaturasDominadasIds ?? []);
    cargarSoportes();
  }
  document.getElementById('seccion-soportes').hidden = !p.roles.includes('TUTOR');
  pintarActivacionRol(p);

  document.getElementById('cargando').hidden = true;
  document.getElementById('contenido').hidden = false;
}

// ---- Guardar perfil de estudiante ----
formEstudiante.addEventListener('submit', async (e) => {
  e.preventDefault();
  limpiarErrores(formEstudiante);
  const semestre = Number(formEstudiante.elements.semestre.value);
  if (!Number.isInteger(semestre) || semestre < 1 || semestre > 14) {
    marcarError(formEstudiante, 'semestre', 'El semestre debe estar entre 1 y 14');
    formEstudiante.elements.semestre.focus();
    return;
  }
  const cuerpo = { semestre };
  const programaId = selectsEstudiante.programaId();
  if (programaId) cuerpo.programaId = programaId;
  try {
    const actualizado = await guardando(e.submitter ?? formEstudiante.querySelector('button'),
      () => api.put('/api/usuarios/yo/perfil-estudiante', cuerpo));
    pintar(actualizado);
    exito('Perfil de estudiante actualizado');
  } catch (err) {
    erroresServidor(formEstudiante, err);
  }
});

// ---- Guardar perfil de tutor ----
formTutor.addEventListener('submit', async (e) => {
  e.preventDefault();
  limpiarErrores(formTutor);
  const f = formTutor.elements;
  const areas = f.areasDeInteres.value.split(',').map((a) => a.trim()).filter(Boolean);
  const modalidades = [...formTutor.querySelectorAll('input[name="modalidades"]:checked')].map((c) => c.value);

  let valido = true;
  if (!f.presentacion.value.trim()) { marcarError(formTutor, 'presentacion', 'La presentación es obligatoria'); valido = false; }
  if (areas.length > 20) { marcarError(formTutor, 'areasDeInteres', 'Máximo 20 áreas de interés'); valido = false; }
  if (areas.some((a) => a.length > 80)) { marcarError(formTutor, 'areasDeInteres', 'Cada área admite hasta 80 caracteres'); valido = false; }
  if (modalidades.length === 0) { marcarError(formTutor, 'modalidades', 'Elige al menos una modalidad'); valido = false; }
  if (!valido) { formTutor.querySelector('.invalido')?.focus(); return; }

  const cuerpo = {
    presentacion: f.presentacion.value.trim(),
    areasDeInteres: areas,
    formacion: f.formacion.value.trim() || null,
    tipoTutor: f.tipoTutor.value,
    experiencia: f.experiencia.value.trim() || null,
    modalidades,
    disponibilidad: f.disponibilidad.value.trim() || null,
    asignaturasDominadasIds: [...asignaturasElegidas.keys()],
    cuentaBancariaCobro: f.cuentaBancariaCobro.value.trim() || null,
  };
  try {
    const actualizado = await guardando(e.submitter ?? formTutor.querySelector('button[type="submit"]'),
      () => api.put('/api/usuarios/yo/perfil-tutor', cuerpo));
    pintar(actualizado);
    exito('Perfil de tutor actualizado');
  } catch (err) {
    erroresServidor(formTutor, err);
  }
});

// ---- Activar el otro rol ----
botonRol.addEventListener('click', async () => {
  const rol = botonRol.dataset.rol;
  try {
    await guardando(botonRol, () => api.post('/api/usuarios/yo/roles', { rol }));
    await refrescarUsuario();
    exito(`Rol de ${ETIQUETA_ROL[rol].toLowerCase()} activado`);
    // Recarga para que el encabezado muestre el selector de rol y la navegación actualizada.
    setTimeout(() => window.location.reload(), 900);
  } catch (err) {
    toastError(err.message ?? 'No se pudo activar el rol');
  }
});

// ---- Soportes de verificación (tutor) ----
function pintarSoportes(soportes) {
  document.getElementById('soportes-vacio').hidden = soportes.length > 0;
  listaSoportes.replaceChildren(...soportes.map((s) => {
    const estado = ESTADO_SOPORTE[s.estado] ?? { texto: s.estado, clase: '' };
    const item = document.createElement('li');
    item.className = 'soporte';

    const cabecera = document.createElement('div');
    cabecera.className = 'soporte__cabecera';
    const nombre = document.createElement('span');
    nombre.className = 'soporte__nombre';
    nombre.textContent = s.nombreArchivo ?? 'Archivo';
    const badge = document.createElement('span');
    badge.className = `badge ${estado.clase}`;
    badge.textContent = estado.texto;
    cabecera.append(nombre, badge);

    const meta = document.createElement('div');
    meta.className = 'soporte__meta';
    meta.textContent = `${ETIQUETA_TIPO_SOPORTE[s.tipo] ?? s.tipo} · enviado el ${formatearFecha(s.fechaEnvio)}`;
    item.append(cabecera, meta);

    if (s.observacion) {
      const obs = document.createElement('div');
      obs.className = 'soporte__observacion';
      obs.textContent = `Observación del administrador: ${s.observacion}`;
      item.appendChild(obs);
    }

    const descargar = document.createElement('button');
    descargar.type = 'button';
    descargar.className = 'btn btn--borde btn--pequeno';
    descargar.textContent = 'Descargar';
    descargar.setAttribute('aria-label', `Descargar ${s.nombreArchivo ?? 'archivo'}`);
    descargar.addEventListener('click', async () => {
      descargar.disabled = true;
      try {
        await descargarArchivo(s.archivoId, s.nombreArchivo ?? 'soporte');
      } catch (err) {
        toastError(err.message ?? 'No se pudo descargar el archivo');
      } finally {
        descargar.disabled = false;
      }
    });
    item.appendChild(descargar);
    return item;
  }));
}

async function cargarSoportes() {
  try {
    pintarSoportes(await api.get('/api/usuarios/yo/soportes'));
  } catch (err) {
    toastError(err.message ?? 'No se pudieron cargar tus soportes');
  }
}

formSoporte.addEventListener('submit', async (e) => {
  e.preventDefault();
  limpiarErrores(formSoporte);
  const archivo = formSoporte.elements.archivo.files[0];
  if (!archivo) {
    marcarError(formSoporte, 'archivo', 'Selecciona un archivo');
    formSoporte.elements.archivo.focus();
    return;
  }
  const extension = archivo.name.split('.').pop().toLowerCase();
  if (!EXTENSIONES_PERMITIDAS.includes(extension)) {
    marcarError(formSoporte, 'archivo', 'Formato no permitido: usa PDF, PNG, JPG o DOCX');
    formSoporte.elements.archivo.focus();
    return;
  }
  if (archivo.size > TAMANO_MAXIMO_BYTES) {
    marcarError(formSoporte, 'archivo', 'El archivo supera el máximo de 10 MB');
    formSoporte.elements.archivo.focus();
    return;
  }
  const datos = new FormData();
  datos.append('datos', new Blob([JSON.stringify({ tipo: formSoporte.elements.tipo.value })], { type: 'application/json' }));
  datos.append('archivo', archivo);
  const boton = e.submitter ?? formSoporte.querySelector('button[type="submit"]');
  const texto = boton.textContent;
  boton.disabled = true;
  boton.textContent = 'Subiendo…';
  try {
    await api.post('/api/usuarios/yo/soportes', datos);
    formSoporte.reset();
    exito('Soporte enviado: quedó en revisión');
    pintar(await api.get('/api/usuarios/yo')); // actualiza el nivel de verificación y la lista
  } catch (err) {
    erroresServidor(formSoporte, err);
  } finally {
    boton.disabled = false;
    boton.textContent = texto;
  }
});

// ---- Notificaciones ----
const TAMANO_PAGINA_NOTIFICACIONES = 20;
let paginaNotificaciones = 0;

async function cargarNotificaciones(reiniciar = true) {
  if (reiniciar) paginaNotificaciones = 0;
  const soloNoLeidas = document.getElementById('notif-solo-no-leidas').checked;
  try {
    const datos = await api.get(`/api/notificaciones?soloNoLeidas=${soloNoLeidas}`
      + `&pagina=${paginaNotificaciones}&tamano=${TAMANO_PAGINA_NOTIFICACIONES}`);
    const items = datos.contenido.map((n) => crearElementoNotificacion(n));
    if (reiniciar) listaNotificaciones.replaceChildren(...items);
    else listaNotificaciones.append(...items);
    document.getElementById('notif-vacio').hidden = listaNotificaciones.children.length > 0;
    document.getElementById('notif-mas').hidden = datos.pagina + 1 >= datos.totalPaginas;
  } catch (err) {
    toastError(err.message ?? 'No se pudieron cargar las notificaciones');
  }
}

document.getElementById('notif-solo-no-leidas').addEventListener('change', () => cargarNotificaciones());
document.getElementById('notif-mas').addEventListener('click', () => {
  paginaNotificaciones += 1;
  cargarNotificaciones(false);
});
document.getElementById('notif-leer-todas').addEventListener('click', async () => {
  try {
    await api.patch('/api/notificaciones/leer-todas');
    avisarCambioNotificaciones();
  } catch (err) {
    toastError(err.message ?? 'No se pudieron marcar las notificaciones');
  }
});
// La campana del encabezado también dispara este evento al marcar notificaciones.
window.addEventListener(EVENTO_NOTIFICACIONES, () => cargarNotificaciones());

// ---- Carga inicial ----
(async function iniciar() {
  try {
    const usuario = await exigirSesion();
    if (!usuario) return;
    pintar(await api.get('/api/usuarios/yo'));
    cargarNotificaciones();
  } catch (err) {
    document.getElementById('cargando').hidden = true;
    mostrarAviso(err.message ?? 'No se pudo cargar tu perfil. Inténtalo de nuevo.', 'error');
  }
})();
