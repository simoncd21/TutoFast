// Lógica de la portada: tema, navbar, modal de demostración y estado del servidor.
import { iniciarTema } from '../componentes/tema.js';
import { iniciarNavbar } from '../componentes/navbar.js';
import { iniciarModales, info } from '../componentes/alertas.js';
import { api } from '../servicios/api.js';

iniciarTema();
iniciarNavbar();
iniciarModales();

document.getElementById('anio').textContent = new Date().getFullYear();

document.querySelector('[data-demo-toast]')?.addEventListener('click', () => {
  info('Pronto podrás publicar tu primera solicitud en TutoFast.');
});

async function comprobarServidor() {
  const punto = document.getElementById('estado-punto');
  const texto = document.getElementById('estado-texto');
  try {
    const salud = await api.get('/api/salud');
    punto.classList.add('punto--ok');
    texto.textContent = `Servidor conectado (BD: ${salud.baseDatos})`;
  } catch {
    punto.classList.add('punto--fallo');
    texto.textContent = 'Servidor no disponible';
  }
}
comprobarServidor();
