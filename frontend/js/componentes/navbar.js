// Comportamiento de la barra de navegación: menú móvil y enlace activo.
export function iniciarNavbar() {
  const boton = document.querySelector('[data-menu-boton]');
  const enlaces = document.querySelector('[data-menu-enlaces]');
  if (boton && enlaces) {
    boton.addEventListener('click', () => {
      const abierto = enlaces.classList.toggle('abierto');
      boton.setAttribute('aria-expanded', String(abierto));
    });
    enlaces.addEventListener('click', (e) => {
      if (e.target.closest('a')) {
        enlaces.classList.remove('abierto');
        boton.setAttribute('aria-expanded', 'false');
      }
    });
  }

  const actual = window.location.pathname.split('/').pop() || 'index.html';
  document.querySelectorAll('[data-menu-enlaces] a[href]').forEach((a) => {
    if (a.getAttribute('href') === actual) a.setAttribute('aria-current', 'page');
  });
}
