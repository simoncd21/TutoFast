// Acceso: pestañas de inicio de sesión y registro (con consentimiento Ley 1581).
import { iniciarEncabezado } from '../componentes/encabezado.js';
import { exito, error as toastError } from '../componentes/alertas.js';
import { api, ErrorApi } from '../servicios/api.js';
import { enlazarSelectsAcademicos } from '../servicios/catalogo.js';
import { estaAutenticado, guardarSesion, destinoSeguro } from '../sesion.js';

const parametros = new URLSearchParams(window.location.search);
const destino = destinoSeguro(parametros.get('siguiente'));

if (estaAutenticado()) {
  window.location.replace(destino);
}

iniciarEncabezado();
document.getElementById('anio').textContent = new Date().getFullYear();

const pestanas = {
  ingreso: { boton: document.getElementById('pestana-ingreso'), panel: document.getElementById('panel-ingreso') },
  registro: { boton: document.getElementById('pestana-registro'), panel: document.getElementById('panel-registro') },
};
const aviso = document.getElementById('aviso');

function mostrarAviso(mensaje, tipo = 'info') {
  aviso.textContent = mensaje;
  aviso.className = `alerta alerta--${tipo}`;
  aviso.hidden = false;
}

function ocultarAviso() {
  aviso.hidden = true;
  aviso.textContent = '';
}

function activarPestana(nombre, enfocar = false) {
  Object.entries(pestanas).forEach(([clave, { boton, panel }]) => {
    const activa = clave === nombre;
    boton.setAttribute('aria-selected', String(activa));
    boton.tabIndex = activa ? 0 : -1;
    panel.hidden = !activa;
  });
  if (enfocar) pestanas[nombre].boton.focus();
}

Object.entries(pestanas).forEach(([nombre, { boton }]) => {
  boton.addEventListener('click', () => { ocultarAviso(); activarPestana(nombre); });
  boton.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
      e.preventDefault();
      activarPestana(nombre === 'ingreso' ? 'registro' : 'ingreso', true);
    }
  });
});

// ---- Utilidades de formulario ----
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
  const entrada = formulario.elements[campo];
  const control = entrada instanceof RadioNodeList ? entrada[0] : entrada;
  if (control && control.classList) {
    control.classList.add('invalido');
    control.setAttribute('aria-invalid', 'true');
  }
}

function enfocarPrimerError(formulario) {
  formulario.querySelector('.invalido')?.focus();
}

function mostrarErroresServidor(formulario, e) {
  if (e instanceof ErrorApi && e.detalles?.length) {
    e.detalles.forEach((d) => marcarError(formulario, d.campo, d.mensaje));
    enfocarPrimerError(formulario);
    mostrarAviso(e.message, 'error');
  } else {
    mostrarAviso(e.message ?? 'Ocurrió un error inesperado', 'error');
  }
}

function ocupado(formulario, enCurso, textoEnCurso) {
  const boton = formulario.querySelector('button[type="submit"]');
  if (enCurso) {
    boton.dataset.texto = boton.textContent;
    boton.textContent = textoEnCurso;
  } else if (boton.dataset.texto) {
    boton.textContent = boton.dataset.texto;
  }
  boton.disabled = enCurso;
}

const REGEX_CORREO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// ---- Inicio de sesión ----
const formularioIngreso = pestanas.ingreso.panel;
formularioIngreso.addEventListener('submit', async (e) => {
  e.preventDefault();
  limpiarErrores(formularioIngreso);
  ocultarAviso();
  const correo = formularioIngreso.elements.correo.value.trim();
  const clave = formularioIngreso.elements.clave.value;

  let valido = true;
  if (!REGEX_CORREO.test(correo)) { marcarError(formularioIngreso, 'correo', 'Ingresa un correo válido'); valido = false; }
  if (!clave) { marcarError(formularioIngreso, 'clave', 'Ingresa tu contraseña'); valido = false; }
  if (!valido) { enfocarPrimerError(formularioIngreso); return; }

  ocupado(formularioIngreso, true, 'Ingresando…');
  try {
    const sesion = await api.post('/api/auth/iniciar-sesion', { correo, clave });
    guardarSesion(sesion);
    window.location.assign(destino);
  } catch (err) {
    mostrarErroresServidor(formularioIngreso, err);
    ocupado(formularioIngreso, false);
  }
});

// ---- Registro ----
const formularioRegistro = pestanas.registro.panel;
const campoSemestre = document.getElementById('campo-semestre');
const camposAcademicos = document.getElementById('campos-academicos');

// Selects encadenados universidad → facultad → programa (solo para estudiantes).
const selectsAcademicos = enlazarSelectsAcademicos({
  universidad: document.getElementById('registro-universidad'),
  facultad: document.getElementById('registro-facultad'),
  programa: document.getElementById('registro-programa'),
});
selectsAcademicos.iniciar();

function actualizarCamposPorRol() {
  const esEstudiante = formularioRegistro.elements.rol.value === 'ESTUDIANTE';
  campoSemestre.hidden = !esEstudiante;
  camposAcademicos.hidden = !esEstudiante;
}
formularioRegistro.querySelectorAll('input[name="rol"]').forEach((r) => r.addEventListener('change', actualizarCamposPorRol));
actualizarCamposPorRol();

function validarRegistro(f) {
  const v = {
    nombre: f.elements.nombre.value.trim(),
    correo: f.elements.correo.value.trim(),
    documento: f.elements.documento.value.trim(),
    clave: f.elements.clave.value,
    rol: f.elements.rol.value,
    acepta: f.elements.aceptaTratamientoDatos.checked,
    semestre: f.elements.semestre.value.trim(),
  };
  let valido = true;
  const falla = (campo, mensaje) => { marcarError(f, campo, mensaje); valido = false; };

  if (!v.nombre) falla('nombre', 'El nombre es obligatorio');
  if (!REGEX_CORREO.test(v.correo)) falla('correo', 'El correo no tiene un formato válido');
  if (!/^[0-9]{5,15}$/.test(v.documento)) falla('documento', 'El documento debe tener entre 5 y 15 dígitos');
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,64}$/.test(v.clave)) {
    falla('clave', 'La clave debe tener entre 8 y 64 caracteres, con al menos una letra y un número');
  }
  if (v.rol === 'ESTUDIANTE' && v.semestre) {
    const n = Number(v.semestre);
    if (!Number.isInteger(n) || n < 1 || n > 14) falla('semestre', 'El semestre debe estar entre 1 y 14');
  }
  if (!v.acepta) {
    falla('aceptaTratamientoDatos', 'Debes aceptar el tratamiento de datos (Ley 1581 de 2012) para continuar');
  }
  return valido ? v : null;
}

formularioRegistro.addEventListener('submit', async (e) => {
  e.preventDefault();
  limpiarErrores(formularioRegistro);
  ocultarAviso();
  const v = validarRegistro(formularioRegistro);
  if (!v) { enfocarPrimerError(formularioRegistro); return; }

  const cuerpo = {
    nombre: v.nombre,
    correo: v.correo,
    documento: v.documento,
    clave: v.clave,
    rol: v.rol,
    aceptaTratamientoDatos: v.acepta,
  };
  if (v.rol === 'ESTUDIANTE' && v.semestre) cuerpo.semestre = Number(v.semestre);
  if (v.rol === 'ESTUDIANTE' && selectsAcademicos.programaId()) cuerpo.programaId = selectsAcademicos.programaId();

  ocupado(formularioRegistro, true, 'Creando cuenta…');
  try {
    const respuesta = await api.post('/api/auth/registro', cuerpo);
    const mensaje = respuesta?.mensaje ?? 'Registro exitoso en TutoFast';
    exito(mensaje);
    formularioRegistro.reset();
    selectsAcademicos.iniciar();
    actualizarCamposPorRol();
    formularioIngreso.elements.correo.value = v.correo;
    activarPestana('ingreso');
    mostrarAviso(`${mensaje}. Ahora inicia sesión con tu correo y contraseña.`, 'exito');
    formularioIngreso.elements.clave.focus();
  } catch (err) {
    mostrarErroresServidor(formularioRegistro, err);
    if (!(err instanceof ErrorApi) || !err.detalles?.length) toastError(err.message);
  } finally {
    ocupado(formularioRegistro, false);
  }
});

// ---- Estado inicial según la URL ----
if (parametros.get('modo') === 'registro') activarPestana('registro');
const motivo = parametros.get('motivo');
if (motivo === 'expirada') mostrarAviso('Tu sesión expiró. Inicia sesión de nuevo.', 'advertencia');
if (motivo === 'cuenta') mostrarAviso('Tu cuenta no puede acceder a la plataforma en este momento.', 'error');
