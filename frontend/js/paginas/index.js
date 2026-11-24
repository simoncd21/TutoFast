// Lógica de la portada: encabezado, accesos según la sesión y estado del servidor.
import { iniciarEncabezado } from '../componentes/encabezado.js';
import { api } from '../servicios/api.js';
import { estaAutenticado } from '../sesion.js';

iniciarEncabezado();

document.getElementById('anio').textContent = new Date().getFullYear();

const autenticado = estaAutenticado();
document.querySelectorAll('[data-solo-invitado]').forEach((el) => { el.hidden = autenticado; });
document.querySelectorAll('[data-solo-autenticado]').forEach((el) => { el.hidden = !autenticado; });

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
