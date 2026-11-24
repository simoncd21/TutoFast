// Búsqueda de tutores y perfil público (con reseñas). Sin datos privados: la API nunca los devuelve.
// Todo el contenido dinámico se construye con textContent (nunca innerHTML).
import { iniciarEncabezado } from '../componentes/encabezado.js';
import { error as toastError, abrirModal, iniciarModales } from '../componentes/alertas.js';
import { api } from '../servicios/api.js';
import { enlazarSelectsAcademicos, listarAsignaturas, llenarSelect } from '../servicios/catalogo.js';
import { exigirSesion } from '../sesion.js';

iniciarEncabezado();
iniciarModales();
document.getElementById('anio').textContent = new Date().getFullYear();

const TAMANO_PAGINA = 9;
const ETIQUETA_MODALIDAD = { VIRTUAL: 'Virtual', PRESENCIAL: 'Presencial' };
const ETIQUETA_TIPO_TUTOR = { PAR: 'Tutor par', MONITOR_CERTIFICADO: 'Monitor certificado' };
const ETIQUETA_SOPORTE = {
  CERTIFICADO_MONITORIA: 'Certificado de monitoría',
  HISTORIAL_CALIFICACIONES: 'Historial de calificaciones',
  CARNE_ESTUDIANTIL: 'Carné estudiantil',
  OTRO: 'Otro soporte',
};
const ETIQUETA_CRITERIO = {
  claridadPedagogica: 'Claridad pedagógica',
  dominioTematico: 'Dominio temático',
  puntualidad: 'Puntualidad',
  cumplimiento: 'Cumplimiento',
  trato: 'Trato',
  relacionCalidadPrecio: 'Relación calidad-precio',
};

const aviso = document.getElementById('aviso');
const formFiltros = document.getElementById('form-filtros');
const selUni = document.getElementById('fil-universidad');
const selFac = document.getElementById('fil-facultad');
const selProg = document.getElementById('fil-programa');
const selAsig = document.getElementById('fil-asignatura');
let paginaActual = 0;

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

function fecha(iso) {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleDateString('es-CO', { dateStyle: 'medium' });
}

const calificacion = (valor) => (valor != null ? `★ ${Number(valor).toFixed(2)}` : 'Sin calificaciones aún');
const servicios = (n) => `${n} servicio${n === 1 ? '' : 's'} completado${n === 1 ? '' : 's'}`;

function insigniaVerificacion(verificado) {
  return verificado ? el('span', 'badge badge--exito', 'Tutor verificado') : el('span', 'badge', 'No verificado');
}

// ---------------------------------------------------------------- búsqueda
function tarjetaTutor(t) {
  const tarjeta = el('article', 'tarjeta solicitud');
  const cabecera = el('div', 'oferta');
  cabecera.append(el('h3', 'solicitud__tema', t.nombre), insigniaVerificacion(t.verificado));
  const datos = el('div', 'fila-badges');
  (t.modalidades ?? []).forEach((m) => datos.appendChild(el('span', 'badge', ETIQUETA_MODALIDAD[m] ?? m)));
  if (t.tipoTutor) datos.appendChild(el('span', 'badge badge--info', ETIQUETA_TIPO_TUTOR[t.tipoTutor] ?? t.tipoTutor));
  tarjeta.append(cabecera, el('p', 'tutor__nota', calificacion(t.promedioCalificacion)),
    el('p', 'texto-suave', servicios(t.serviciosCompletados)), datos);
  if (t.areasDeInteres?.length) tarjeta.appendChild(el('p', 'texto-suave', t.areasDeInteres.join(' · ')));
  const pie = el('div', 'solicitud__acciones');
  pie.appendChild(boton('Ver perfil', 'btn btn--borde btn--pequeno', () => abrirPerfil(t.id)));
  tarjeta.appendChild(pie);
  return tarjeta;
}

function parametros(pagina) {
  const p = new URLSearchParams();
  const poner = (clave, valor) => { if (valor !== '' && valor != null) p.set(clave, valor); };
  poner('programaId', selProg.value);
  poner('asignaturaId', selAsig.value);
  poner('modalidad', document.getElementById('fil-modalidad').value);
  poner('disponibilidad', document.getElementById('fil-disponibilidad').value.trim());
  poner('calificacionMin', document.getElementById('fil-calificacion').value);
  if (document.getElementById('fil-verificados').checked) p.set('soloVerificados', 'true');
  poner('orden', document.getElementById('fil-orden').value);
  p.set('pagina', pagina);
  p.set('tamano', TAMANO_PAGINA);
  return p;
}

async function buscar(pagina) {
  const resultados = document.getElementById('resultados');
  const vacio = document.getElementById('resultados-vacio');
  const barra = document.getElementById('paginacion');
  try {
    const datos = await api.get(`/api/tutores?${parametros(pagina)}`);
    paginaActual = datos.pagina;
    resultados.replaceChildren(...datos.contenido.map(tarjetaTutor));
    vacio.hidden = datos.contenido.length > 0;
    barra.hidden = datos.totalPaginas <= 1;
    document.getElementById('pagina-info').textContent = `Página ${datos.pagina + 1} de ${Math.max(datos.totalPaginas, 1)} · ${datos.totalElementos} resultado${datos.totalElementos === 1 ? '' : 's'}`;
    document.getElementById('pagina-anterior').disabled = datos.pagina <= 0;
    document.getElementById('pagina-siguiente').disabled = datos.pagina + 1 >= datos.totalPaginas;
  } catch (e) {
    toastError(e.message ?? 'No se pudieron cargar los tutores');
  }
}

async function cargarAsignaturas(programaId) {
  if (!programaId) {
    llenarSelect(selAsig, [], 'Todas');
    return;
  }
  try {
    llenarSelect(selAsig, await listarAsignaturas(programaId), 'Todas');
  } catch (e) {
    toastError(e.message ?? 'No se pudieron cargar las asignaturas');
  }
}

formFiltros.addEventListener('submit', (e) => { e.preventDefault(); buscar(0); });
document.getElementById('filtros-limpiar').addEventListener('click', () => {
  formFiltros.reset();
  llenarSelect(selFac, [], 'Selecciona una facultad');
  llenarSelect(selProg, [], 'Selecciona un programa');
  llenarSelect(selAsig, [], 'Todas');
  buscar(0);
});
document.getElementById('pagina-anterior').addEventListener('click', () => buscar(paginaActual - 1));
document.getElementById('pagina-siguiente').addEventListener('click', () => buscar(paginaActual + 1));

// ---------------------------------------------------------------- perfil público
function bloque(titulo, valor) {
  if (!valor) return null;
  const div = el('div');
  div.append(el('dt', '', titulo), el('dd', '', valor));
  return div;
}

function tarjetaResena(r) {
  const nodo = el('article', 'resena');
  const cab = el('div', 'oferta');
  cab.append(el('strong', '', r.autorNombre), el('span', 'tutor__nota', `★ ${Number(r.promedio).toFixed(2)}`));
  nodo.append(cab, el('p', 'texto-suave', fecha(r.fechaEmision)));
  if (r.comentario) nodo.appendChild(el('p', 'resena__comentario', r.comentario));
  const criterios = el('div', 'fila-badges');
  Object.entries(r.criterios ?? {}).forEach(([clave, valor]) => {
    criterios.appendChild(el('span', 'badge', `${ETIQUETA_CRITERIO[clave] ?? clave}: ${valor}`));
  });
  nodo.appendChild(criterios);
  return nodo;
}

async function abrirPerfil(id) {
  const cuerpo = document.getElementById('tutor-cuerpo');
  try {
    const t = await api.get(`/api/tutores/${encodeURIComponent(id)}`);
    document.getElementById('tutor-titulo').textContent = t.nombre;
    const cabecera = el('div', 'fila-badges');
    cabecera.append(el('span', 'badge badge--exito', t.verificado.etiqueta ?? 'Tutor'),
      el('span', 'badge badge--info', calificacion(t.promedioCalificacion)),
      el('span', 'badge', servicios(t.serviciosCompletados)));
    const datos = el('dl', 'datos');
    [
      bloque('Presentación', t.declarado.presentacion),
      bloque('Formación', t.declarado.formacion),
      bloque('Experiencia', t.declarado.experiencia),
      bloque('Modalidades', (t.declarado.modalidades ?? []).map((m) => ETIQUETA_MODALIDAD[m] ?? m).join(' · ')),
      bloque('Disponibilidad', t.declarado.disponibilidad),
      bloque('Áreas de interés', (t.declarado.areasDeInteres ?? []).join(' · ')),
      bloque('Asignaturas que domina', (t.declarado.asignaturas ?? []).join(' · ')),
    ].filter(Boolean).forEach((n) => datos.appendChild(n));

    const nodos = [cabecera, datos];
    const aprobados = t.verificado.soportesAprobados ?? [];
    nodos.push(el('h3', '', 'Verificación'));
    if (aprobados.length) {
      const lista = el('ul', 'propuesta__historial');
      aprobados.forEach((s) => lista.appendChild(el('li', '', `${ETIQUETA_SOPORTE[s.tipo] ?? s.tipo} · aprobado el ${fecha(s.fechaRevision)}`)));
      nodos.push(lista);
    } else {
      nodos.push(el('p', 'texto-suave', 'Aún no tiene soportes aprobados.'));
    }
    nodos.push(el('h3', '', `Reseñas (${t.resenas.length})`));
    if (t.resenas.length) {
      const lista = el('div', 'resenas');
      t.resenas.forEach((r) => lista.appendChild(tarjetaResena(r)));
      nodos.push(lista);
    } else {
      nodos.push(el('p', 'texto-suave', 'Todavía no tiene reseñas. Solo se publican las de servicios finalizados en la plataforma.'));
    }
    cuerpo.replaceChildren(...nodos);
    abrirModal('modal-tutor');
  } catch (e) {
    toastError(e.message ?? 'No se pudo cargar el perfil del tutor');
  }
}

// ---------------------------------------------------------------- inicio
(async function iniciar() {
  try {
    const usuario = await exigirSesion();
    if (!usuario) return;
    const enlace = enlazarSelectsAcademicos(
      { universidad: selUni, facultad: selFac, programa: selProg },
      { alCambiarPrograma: cargarAsignaturas },
    );
    await enlace.iniciar();
    selUni.options[0].textContent = 'Todas';
    document.getElementById('cargando').hidden = true;
    document.getElementById('contenido').hidden = false;
    await buscar(0);
    const idDirecto = new URLSearchParams(window.location.search).get('id');
    if (idDirecto) abrirPerfil(idDirecto);
  } catch (err) {
    document.getElementById('cargando').hidden = true;
    aviso.textContent = err.message ?? 'No se pudo cargar la página. Inténtalo de nuevo.';
    aviso.className = 'alerta alerta--error';
    aviso.hidden = false;
  }
})();
