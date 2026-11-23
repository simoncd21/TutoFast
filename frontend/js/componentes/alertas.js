// Alertas tipo toast y modales de confirmación. Todo el texto se inserta con textContent (anti-XSS).
function contenedorToasts() {
  let contenedor = document.getElementById('toasts');
  if (!contenedor) {
    contenedor = document.createElement('div');
    contenedor.id = 'toasts';
    contenedor.className = 'toasts';
    contenedor.setAttribute('aria-live', 'polite');
    document.body.appendChild(contenedor);
  }
  return contenedor;
}

export function mostrarToast(mensaje, tipo = 'info', duracionMs = 4500) {
  const toast = document.createElement('div');
  toast.className = `toast toast--${tipo}`;
  toast.setAttribute('role', tipo === 'error' ? 'alert' : 'status');

  const texto = document.createElement('div');
  texto.className = 'toast__texto';
  texto.textContent = mensaje;

  const cerrar = document.createElement('button');
  cerrar.className = 'toast__cerrar';
  cerrar.type = 'button';
  cerrar.setAttribute('aria-label', 'Cerrar');
  cerrar.textContent = '×';

  const quitar = () => {
    toast.classList.add('toast--saliendo');
    setTimeout(() => toast.remove(), 250);
  };
  cerrar.addEventListener('click', quitar);

  toast.append(texto, cerrar);
  contenedorToasts().appendChild(toast);
  if (duracionMs > 0) setTimeout(quitar, duracionMs);
}

export const exito = (mensaje) => mostrarToast(mensaje, 'exito');
export const error = (mensaje) => mostrarToast(mensaje, 'error', 6500);
export const aviso = (mensaje) => mostrarToast(mensaje, 'advertencia');
export const info = (mensaje) => mostrarToast(mensaje, 'info');

export function abrirModal(id) {
  document.getElementById(id)?.classList.add('abierto');
}

export function cerrarModal(id) {
  document.getElementById(id)?.classList.remove('abierto');
}

export function iniciarModales() {
  document.querySelectorAll('[data-abrir-modal]').forEach((el) => {
    el.addEventListener('click', () => abrirModal(el.dataset.abrirModal));
  });
  document.querySelectorAll('[data-cerrar-modal]').forEach((el) => {
    el.addEventListener('click', () => cerrarModal(el.dataset.cerrarModal));
  });
  document.querySelectorAll('.modal-fondo').forEach((fondo) => {
    fondo.addEventListener('click', (e) => {
      if (e.target === fondo) fondo.classList.remove('abierto');
    });
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      document.querySelectorAll('.modal-fondo.abierto').forEach((m) => m.classList.remove('abierto'));
    }
  });
}
