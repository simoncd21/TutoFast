// Encabezado común: navegación según el rol activo, selector de rol y botón de tema.
// Se dibuja dentro de <header class="navbar" data-encabezado>. Todo el texto usa textContent.
import { iniciarTema } from './tema.js';
import { iniciarNavbar } from './navbar.js';
import { construirCampana } from './campana.js';
import { estaAutenticado, obtenerUsuario, rolActivo, rolesDisponibles, cambiarRolActivo, salir, ETIQUETA_ROL } from '../sesion.js';

// Cada módulo posterior agrega aquí sus páginas por rol.
const ENLACES_PUBLICOS = [
  { texto: 'Cómo funciona', href: 'index.html#como-funciona' },
  { texto: 'Beneficios', href: 'index.html#beneficios' },
];
const ENLACES_POR_ROL = {
  ESTUDIANTE: [{ texto: 'Inicio', href: 'index.html' }, { texto: 'Mi perfil', href: 'perfil.html' }],
  TUTOR: [{ texto: 'Inicio', href: 'index.html' }, { texto: 'Mi perfil', href: 'perfil.html' }],
  ADMINISTRADOR: [{ texto: 'Inicio', href: 'index.html' }, { texto: 'Mi perfil', href: 'perfil.html' }],
};

function elemento(etiqueta, clase, texto) {
  const el = document.createElement(etiqueta);
  if (clase) el.className = clase;
  if (texto !== undefined) el.textContent = texto;
  return el;
}

function enlace(texto, href) {
  const a = elemento('a', '', texto);
  a.href = href;
  return a;
}

function construirMarca() {
  const marca = elemento('a', 'navbar__marca');
  marca.href = 'index.html';
  marca.setAttribute('aria-label', 'TutoFast, inicio');
  const logo = elemento('span', 'navbar__logo', 'T');
  logo.setAttribute('aria-hidden', 'true');
  marca.append(logo, elemento('span', '', 'TutoFast'));
  return marca;
}

function construirNavegacion(autenticado) {
  const nav = elemento('nav', 'navbar__enlaces');
  nav.setAttribute('data-menu-enlaces', '');
  nav.setAttribute('aria-label', 'Principal');
  const enlaces = autenticado ? (ENLACES_POR_ROL[rolActivo()] ?? []) : ENLACES_PUBLICOS;
  enlaces.forEach((e) => nav.appendChild(enlace(e.texto, e.href)));
  return nav;
}

function construirSelectorRol() {
  const roles = rolesDisponibles();
  if (roles.length < 2) return null;
  const etiqueta = elemento('label', 'navbar__rol');
  etiqueta.append(elemento('span', 'solo-lectores', 'Rol activo'));
  const select = document.createElement('select');
  select.setAttribute('aria-label', 'Rol activo');
  roles.forEach((rol) => {
    const opcion = elemento('option', '', ETIQUETA_ROL[rol] ?? rol);
    opcion.value = rol;
    opcion.selected = rol === rolActivo();
    select.appendChild(opcion);
  });
  select.addEventListener('change', () => {
    if (cambiarRolActivo(select.value)) window.location.reload();
  });
  etiqueta.appendChild(select);
  return etiqueta;
}

function construirAcciones(autenticado) {
  const acciones = elemento('div', 'navbar__acciones');

  if (autenticado) {
    const selector = construirSelectorRol();
    if (selector) acciones.appendChild(selector);
    acciones.appendChild(construirCampana());
    const usuario = obtenerUsuario();
    const primerNombre = (usuario?.nombre ?? '').split(' ')[0];
    const chip = elemento('span', 'navbar__usuario', primerNombre);
    chip.title = usuario?.correo ?? '';
    acciones.appendChild(chip);
  } else if ((window.location.pathname.split('/').pop() || 'index.html') !== 'acceso.html') {
    const entrar = enlace('Ingresar', 'acceso.html');
    entrar.className = 'btn btn--borde navbar__ingresar';
    acciones.appendChild(entrar);
  }

  const tema = elemento('button', 'btn btn--icono', '🌙');
  tema.type = 'button';
  tema.setAttribute('data-alternar-tema', '');
  tema.setAttribute('aria-label', 'Cambiar tema');
  acciones.appendChild(tema);

  if (autenticado) {
    const salirBtn = elemento('button', 'btn btn--borde navbar__salir', 'Salir');
    salirBtn.type = 'button';
    salirBtn.addEventListener('click', salir);
    acciones.appendChild(salirBtn);
  }

  const menu = elemento('button', 'btn btn--icono navbar__menu', '☰');
  menu.type = 'button';
  menu.setAttribute('data-menu-boton', '');
  menu.setAttribute('aria-expanded', 'false');
  menu.setAttribute('aria-label', 'Abrir menú');
  acciones.appendChild(menu);
  return acciones;
}

export function iniciarEncabezado() {
  const cabecera = document.querySelector('[data-encabezado]');
  if (cabecera) {
    const autenticado = estaAutenticado();
    const interior = elemento('div', 'contenedor navbar__interior');
    interior.append(construirMarca(), construirNavegacion(autenticado), construirAcciones(autenticado));
    cabecera.replaceChildren(interior);
  }
  iniciarTema();
  iniciarNavbar();
}
