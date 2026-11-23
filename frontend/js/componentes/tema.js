// Selector de tema Claro / Oscuro persistido en localStorage.
import { CLAVE_TEMA } from '../config.js';

function temaInicial() {
  const guardado = localStorage.getItem(CLAVE_TEMA);
  if (guardado === 'light' || guardado === 'dark') return guardado;
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function aplicarTema(tema) {
  document.documentElement.setAttribute('data-theme', tema);
  localStorage.setItem(CLAVE_TEMA, tema);
  document.querySelectorAll('[data-alternar-tema]').forEach((boton) => {
    const oscuro = tema === 'dark';
    boton.textContent = oscuro ? '☀️' : '🌙';
    boton.setAttribute('aria-label', oscuro ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro');
    boton.setAttribute('title', oscuro ? 'Tema claro' : 'Tema oscuro');
  });
}

export function alternarTema() {
  const actual = document.documentElement.getAttribute('data-theme');
  aplicarTema(actual === 'dark' ? 'light' : 'dark');
}

export function iniciarTema() {
  aplicarTema(temaInicial());
  document.querySelectorAll('[data-alternar-tema]').forEach((boton) => {
    boton.addEventListener('click', alternarTema);
  });
}
