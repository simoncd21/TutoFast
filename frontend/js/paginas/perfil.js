// Perfil: datos de la cuenta, perfiles de estudiante y tutor, y activación del otro rol.
import { iniciarEncabezado } from '../componentes/encabezado.js';
import { exito, error as toastError } from '../componentes/alertas.js';
import { api, ErrorApi } from '../servicios/api.js';
import { exigirSesion, refrescarUsuario, rolActivo, ETIQUETA_ROL } from '../sesion.js';

iniciarEncabezado();
document.getElementById('anio').textContent = new Date().getFullYear();

const aviso = document.getElementById('aviso');
const formEstudiante = document.getElementById('form-estudiante');
const formTutor = document.getElementById('form-tutor');
const botonRol = document.getElementById('boton-activar-rol');

// Campos que el formulario no edita todavía (se necesita el catálogo del módulo 04): se conservan tal cual.
let perfilActual = null;

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
  if (p.roles.includes('TUTOR')) pintarTutor(p);
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
  const programaId = perfilActual?.perfilEstudiante?.programaId;
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
    asignaturasDominadasIds: perfilActual?.perfilTutor?.asignaturasDominadasIds ?? [],
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

// ---- Carga inicial ----
(async function iniciar() {
  try {
    const usuario = await exigirSesion();
    if (!usuario) return;
    pintar(await api.get('/api/usuarios/yo'));
  } catch (err) {
    document.getElementById('cargando').hidden = true;
    mostrarAviso(err.message ?? 'No se pudo cargar tu perfil. Inténtalo de nuevo.', 'error');
  }
})();
