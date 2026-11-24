// Solicitudes de tutoría: explorar con filtros, publicar, editar y cancelar las propias.
// Todo el contenido dinámico se construye con textContent (nunca innerHTML).
import { iniciarEncabezado } from '../componentes/encabezado.js';
import { exito, error as toastError, abrirModal, cerrarModal, iniciarModales } from '../componentes/alertas.js';
import { api, ErrorApi, descargarArchivo } from '../servicios/api.js';
import {
  enlazarSelectsAcademicos, listarAsignaturas, llenarSelect,
} from '../servicios/catalogo.js';
import { exigirSesion, obtenerUsuario, rolActivo } from '../sesion.js';
import {
  montarPropuestas, ETIQUETA_ESTADO_PROPUESTA, ETIQUETA_TIPO_PROPUESTA,
} from '../componentes/propuestas.js';
import { montarMensajeria } from '../componentes/chat.js';

iniciarEncabezado();
iniciarModales();
document.getElementById('anio').textContent = new Date().getFullYear();

const aviso = document.getElementById('aviso');
const TAMANO_PAGINA = 9;
const MAX_ARCHIVOS = 5;
const MAX_BYTES = 10 * 1024 * 1024;
const EXTENSIONES = ['pdf', 'png', 'jpg', 'jpeg', 'docx'];

const ETIQUETA_ESTADO = {
  PUBLICADA: ['Publicada', 'badge--info'],
  PROPUESTAS_RECIBIDAS: ['Con propuestas', 'badge--exito'],
  NEGOCIACION_EN_CURSO: ['En negociación', 'badge--advertencia'],
  TUTOR_SELECCIONADO: ['Tutor seleccionado', 'badge--advertencia'],
  FORMALIZADA: ['Formalizada', 'badge--exito'],
  CANCELADA: ['Cancelada', 'badge--error'],
};
const ETIQUETA_TIPO = {
  EXPLICACION_TEMA: 'Explicación de un tema',
  RESOLUCION_GUIADA_EJERCICIOS: 'Resolución guiada de ejercicios',
  PREPARACION_EXAMEN: 'Preparación de examen',
  REVISION_TRABAJO: 'Revisión de trabajo',
};
const ETIQUETA_DURACION = { MIN_30: '30 min', MIN_60: '60 min', MIN_120: '120 min' };
const ETIQUETA_MODALIDAD = { VIRTUAL: 'Virtual', PRESENCIAL: 'Presencial' };

// ---------------------------------------------------------------- utilidades
function el(etiqueta, clase, texto) {
  const nodo = document.createElement(etiqueta);
  if (clase) nodo.className = clase;
  if (texto !== undefined && texto !== null) nodo.textContent = texto;
  return nodo;
}

function mostrarAviso(mensaje, tipo = 'info') {
  aviso.textContent = mensaje;
  aviso.className = `alerta alerta--${tipo}`;
  aviso.hidden = false;
}

function fecha(iso) {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
}

const formatoPesos = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
const pesos = (valor) => formatoPesos.format(Number(valor));

function badgeEstado(estado) {
  const [texto, clase] = ETIQUETA_ESTADO[estado] ?? [estado, ''];
  return el('span', `badge ${clase}`.trim(), texto);
}

function boton(texto, clase, alClic) {
  const b = el('button', clase, texto);
  b.type = 'button';
  b.addEventListener('click', alClic);
  return b;
}

function limpiarErrores(formulario) {
  formulario.querySelectorAll('[data-error-de]').forEach((n) => { n.hidden = true; n.textContent = ''; });
  formulario.querySelectorAll('.invalido').forEach((n) => {
    n.classList.remove('invalido');
    n.removeAttribute('aria-invalid');
  });
}

function marcarError(formulario, campo, mensaje) {
  const contenedor = formulario.querySelector(`[data-error-de="${campo}"]`);
  if (contenedor) { contenedor.textContent = mensaje; contenedor.hidden = false; }
  const control = formulario.elements[campo];
  if (control?.classList) {
    control.classList.add('invalido');
    control.setAttribute('aria-invalid', 'true');
  }
}

function errorServidor(formulario, e) {
  if (formulario && e instanceof ErrorApi && e.detalles?.length) {
    e.detalles.forEach((d) => marcarError(formulario, d.campo, d.mensaje));
    formulario.querySelector('.invalido')?.focus();
  }
  toastError(e.message ?? 'Ocurrió un error inesperado');
}

// ---------------------------------------------------------------- pestañas
let pestanas = ['explorar'];
const cargadas = new Set();
const cargadores = {
  explorar: () => iniciarExplorar(),
  publicar: () => iniciarFormulario(),
  mias: () => cargarMias(),
  propuestas: () => cargarMisPropuestas(),
};

function activarPestana(nombre, enfocar = false) {
  ['explorar', 'publicar', 'mias', 'propuestas'].forEach((p) => {
    const b = document.getElementById(`pestana-${p}`);
    const activa = p === nombre;
    b.setAttribute('aria-selected', String(activa));
    b.tabIndex = activa ? 0 : -1;
    document.getElementById(`panel-${p}`).hidden = !activa;
  });
  if (enfocar) document.getElementById(`pestana-${nombre}`).focus();
  if (!cargadas.has(nombre)) {
    cargadas.add(nombre);
    cargadores[nombre]();
  } else if (nombre === 'propuestas') {
    cargarMisPropuestas();
  } else if (nombre === 'mias') {
    cargarMias();
  }
}

function enlazarPestanas() {
  pestanas.forEach((nombre, i) => {
    const b = document.getElementById(`pestana-${nombre}`);
    b.hidden = false;
    b.addEventListener('click', () => activarPestana(nombre));
    b.addEventListener('keydown', (e) => {
      const paso = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
      if (!paso) return;
      e.preventDefault();
      activarPestana(pestanas[(i + paso + pestanas.length) % pestanas.length], true);
    });
  });
}

// ---------------------------------------------------------------- tarjetas
function tarjetaResumen(s, acciones = []) {
  const tarjeta = el('article', 'tarjeta solicitud');
  const cabecera = el('div', 'oferta');
  cabecera.append(el('h3', 'solicitud__tema', s.tema), badgeEstado(s.estado));
  const meta = el('p', 'texto-suave', [s.asignaturaNombre, s.programaNombre].filter(Boolean).join(' · '));
  const datos = el('div', 'fila-badges');
  datos.append(
    el('span', 'badge', ETIQUETA_MODALIDAD[s.modalidad] ?? s.modalidad),
    el('span', 'badge', ETIQUETA_DURACION[s.duracion] ?? s.duracion),
    el('span', 'badge', `${s.totalPropuestas} propuesta${s.totalPropuestas === 1 ? '' : 's'}`),
  );
  const precio = el('p', 'solicitud__precio', pesos(s.precioInicialCOP));
  const cuando = el('p', 'texto-suave', fecha(s.fechaHora));
  tarjeta.append(cabecera, meta, precio, cuando, datos);
  if (s.estudianteNombre) {
    const conf = s.estudianteConfiabilidad != null ? ` · confiabilidad ${Number(s.estudianteConfiabilidad).toFixed(1)}` : '';
    tarjeta.appendChild(el('p', 'texto-suave', `Publicada por ${s.estudianteNombre}${conf}`));
  }
  const pie = el('div', 'solicitud__acciones');
  pie.appendChild(boton('Ver detalle', 'btn btn--borde btn--pequeno', () => abrirDetalle(s.id)));
  acciones.forEach((a) => pie.appendChild(a));
  tarjeta.appendChild(pie);
  return tarjeta;
}

// ---------------------------------------------------------------- explorar
const formFiltros = document.getElementById('form-filtros');
const selFilUni = document.getElementById('fil-universidad');
const selFilFac = document.getElementById('fil-facultad');
const selFilProg = document.getElementById('fil-programa');
const selFilAsig = document.getElementById('fil-asignatura');
let paginaActual = 0;

async function cargarAsignaturasEn(select, programaId, marcador) {
  if (!programaId) {
    llenarSelect(select, [], marcador);
    return;
  }
  try {
    llenarSelect(select, await listarAsignaturas(programaId), marcador);
  } catch (e) {
    toastError(e.message ?? 'No se pudieron cargar las asignaturas');
  }
}

async function iniciarExplorar() {
  const enlace = enlazarSelectsAcademicos(
    { universidad: selFilUni, facultad: selFilFac, programa: selFilProg },
    { alCambiarPrograma: (id) => cargarAsignaturasEn(selFilAsig, id, 'Todas') },
  );
  await enlace.iniciar();
  selFilUni.options[0].textContent = 'Todas';
  buscar(0);
}

function parametrosFiltro(pagina) {
  const p = new URLSearchParams();
  const poner = (clave, valor) => { if (valor !== '' && valor != null) p.set(clave, valor); };
  poner('universidadId', selFilUni.value);
  poner('facultadId', selFilFac.value);
  poner('programaId', selFilProg.value);
  poner('asignaturaId', selFilAsig.value);
  poner('tema', document.getElementById('fil-tema').value.trim());
  poner('modalidad', document.getElementById('fil-modalidad').value);
  poner('precioMin', document.getElementById('fil-precio-min').value);
  poner('precioMax', document.getElementById('fil-precio-max').value);
  const desde = document.getElementById('fil-desde').value;
  const hasta = document.getElementById('fil-hasta').value;
  if (desde) poner('fechaDesde', new Date(`${desde}T00:00:00`).toISOString());
  if (hasta) poner('fechaHasta', new Date(`${hasta}T23:59:59`).toISOString());
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
    const datos = await api.get(`/api/solicitudes?${parametrosFiltro(pagina)}`);
    paginaActual = datos.pagina;
    resultados.replaceChildren(...datos.contenido.map((s) => tarjetaResumen(s)));
    vacio.hidden = datos.contenido.length > 0;
    barra.hidden = datos.totalPaginas <= 1;
    document.getElementById('pagina-info').textContent = `Página ${datos.pagina + 1} de ${Math.max(datos.totalPaginas, 1)} · ${datos.totalElementos} resultado${datos.totalElementos === 1 ? '' : 's'}`;
    document.getElementById('pagina-anterior').disabled = datos.pagina <= 0;
    document.getElementById('pagina-siguiente').disabled = datos.pagina + 1 >= datos.totalPaginas;
  } catch (e) {
    toastError(e.message ?? 'No se pudieron cargar las solicitudes');
  }
}

formFiltros.addEventListener('submit', (e) => { e.preventDefault(); buscar(0); });
document.getElementById('filtros-limpiar').addEventListener('click', () => {
  formFiltros.reset();
  llenarSelect(selFilFac, [], 'Selecciona una facultad');
  llenarSelect(selFilProg, [], 'Selecciona un programa');
  llenarSelect(selFilAsig, [], 'Todas');
  buscar(0);
});
document.getElementById('pagina-anterior').addEventListener('click', () => buscar(paginaActual - 1));
document.getElementById('pagina-siguiente').addEventListener('click', () => buscar(paginaActual + 1));

// ---------------------------------------------------------------- publicar / editar
const form = document.getElementById('form-solicitud');
const selUni = document.getElementById('sol-universidad');
const selFac = document.getElementById('sol-facultad');
const selProg = document.getElementById('sol-programa');
const selAsig = document.getElementById('sol-asignatura');
const selModalidad = document.getElementById('sol-modalidad');
const campoZona = document.getElementById('campo-zona');
let enlaceSelects = null;
let editandoId = null;
let formularioListo = false;

function alternarZona() {
  campoZona.hidden = selModalidad.value !== 'PRESENCIAL';
}
selModalidad.addEventListener('change', alternarZona);

async function iniciarFormulario() {
  enlaceSelects = enlazarSelectsAcademicos(
    { universidad: selUni, facultad: selFac, programa: selProg },
    { alCambiarPrograma: (id) => cargarAsignaturasEn(selAsig, id, 'Selecciona una asignatura') },
  );
  await enlaceSelects.iniciar();
  formularioListo = true;
  alternarZona();
}

function aInputFecha(iso) {
  const d = new Date(iso);
  const dos = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${dos(d.getMonth() + 1)}-${dos(d.getDate())}T${dos(d.getHours())}:${dos(d.getMinutes())}`;
}

function salirDeEdicion() {
  editandoId = null;
  form.reset();
  limpiarErrores(form);
  document.getElementById('form-titulo').textContent = 'Publicar una solicitud';
  document.getElementById('form-enviar').textContent = 'Publicar solicitud';
  document.getElementById('form-cancelar-edicion').hidden = true;
  alternarZona();
}
document.getElementById('form-cancelar-edicion').addEventListener('click', salirDeEdicion);

async function empezarEdicion(id) {
  try {
    const s = await api.get(`/api/solicitudes/${encodeURIComponent(id)}`);
    activarPestana('publicar');
    while (!formularioListo) await new Promise((r) => setTimeout(r, 50));
    await enlaceSelects.iniciar(s.programaId);
    await cargarAsignaturasEn(selAsig, s.programaId, 'Selecciona una asignatura');
    selAsig.value = s.asignaturaId;
    editandoId = s.id;
    form.elements.tema.value = s.tema;
    form.elements.descripcion.value = s.descripcion;
    form.elements.tipoServicio.value = s.tipoServicio;
    form.elements.fechaHora.value = aInputFecha(s.fechaHora);
    form.elements.duracion.value = s.duracion;
    form.elements.precioInicialCOP.value = Math.round(Number(s.precioInicialCOP));
    form.elements.modalidad.value = s.modalidad;
    form.elements.zonaEncuentro.value = s.zonaEncuentro ?? '';
    alternarZona();
    document.getElementById('form-titulo').textContent = 'Editar solicitud';
    document.getElementById('form-enviar').textContent = 'Guardar cambios';
    document.getElementById('form-cancelar-edicion').hidden = false;
    form.scrollIntoView({ behavior: 'smooth', block: 'start' });
  } catch (e) {
    toastError(e.message ?? 'No se pudo cargar la solicitud');
  }
}

function validarArchivos(archivos) {
  if (archivos.length > MAX_ARCHIVOS) return `Puedes adjuntar hasta ${MAX_ARCHIVOS} archivos`;
  for (const a of archivos) {
    const ext = a.name.split('.').pop().toLowerCase();
    if (!EXTENSIONES.includes(ext)) return `«${a.name}»: solo se admiten PDF, PNG, JPG o DOCX`;
    if (a.size > MAX_BYTES) return `«${a.name}» supera los 10 MB`;
  }
  return null;
}

function validarCliente() {
  const faltantes = [];
  const obligatorio = (campo, mensaje) => {
    if (!String(form.elements[campo].value ?? '').trim()) { marcarError(form, campo, mensaje); faltantes.push(campo); }
  };
  obligatorio('asignaturaId', 'Selecciona la asignatura');
  obligatorio('tema', 'El tema es obligatorio');
  obligatorio('descripcion', 'La descripción es obligatoria');
  obligatorio('fechaHora', 'Indica la fecha y hora');
  obligatorio('precioInicialCOP', 'Indica el precio inicial');
  if (form.elements.descripcion.value.trim() && form.elements.descripcion.value.trim().length < 20) {
    marcarError(form, 'descripcion', 'La descripción debe tener al menos 20 caracteres');
    faltantes.push('descripcion');
  }
  if (form.elements.fechaHora.value && new Date(form.elements.fechaHora.value) <= new Date()) {
    marcarError(form, 'fechaHora', 'La fecha y hora deben ser futuras');
    faltantes.push('fechaHora');
  }
  if (Number(form.elements.precioInicialCOP.value) <= 0 && form.elements.precioInicialCOP.value) {
    marcarError(form, 'precioInicialCOP', 'El precio debe ser mayor que cero');
    faltantes.push('precioInicialCOP');
  }
  if (selModalidad.value === 'PRESENCIAL' && !form.elements.zonaEncuentro.value.trim()) {
    marcarError(form, 'zonaEncuentro', 'La modalidad presencial exige una zona de encuentro segura');
    faltantes.push('zonaEncuentro');
  }
  return faltantes;
}

function datosFormulario() {
  return {
    asignaturaId: form.elements.asignaturaId.value,
    tema: form.elements.tema.value.trim(),
    descripcion: form.elements.descripcion.value.trim(),
    tipoServicio: form.elements.tipoServicio.value,
    fechaHora: new Date(form.elements.fechaHora.value).toISOString(),
    modalidad: form.elements.modalidad.value,
    zonaEncuentro: selModalidad.value === 'PRESENCIAL' ? form.elements.zonaEncuentro.value.trim() : null,
    duracion: form.elements.duracion.value,
    precioInicialCOP: Number(form.elements.precioInicialCOP.value),
  };
}

form.addEventListener('submit', async (e) => {
  e.preventDefault();
  limpiarErrores(form);
  const archivos = [...document.getElementById('sol-archivos').files];
  const errorArchivos = validarArchivos(archivos);
  const faltantes = validarCliente();
  if (errorArchivos) { marcarError(form, 'archivos', errorArchivos); faltantes.push('archivos'); }
  if (faltantes.length) {
    form.querySelector('.invalido')?.focus();
    return;
  }
  const enviar = document.getElementById('form-enviar');
  enviar.disabled = true;
  try {
    const datos = datosFormulario();
    if (editandoId) {
      await api.put(`/api/solicitudes/${encodeURIComponent(editandoId)}`, datos);
      if (archivos.length) {
        const adj = new FormData();
        archivos.forEach((a) => adj.append('archivos', a));
        await api.post(`/api/solicitudes/${encodeURIComponent(editandoId)}/archivos`, adj);
      }
      exito('Solicitud actualizada');
    } else {
      const cuerpo = new FormData();
      cuerpo.append('datos', new Blob([JSON.stringify(datos)], { type: 'application/json' }));
      archivos.forEach((a) => cuerpo.append('archivos', a));
      await api.post('/api/solicitudes', cuerpo);
      exito('Solicitud publicada');
    }
    salirDeEdicion();
    document.getElementById('sol-archivos').value = '';
    activarPestana('mias');
  } catch (err) {
    errorServidor(form, err);
  } finally {
    enviar.disabled = false;
  }
});

// ---------------------------------------------------------------- mis solicitudes
let solicitudACancelar = null;

async function cargarMias() {
  const lista = document.getElementById('mias-lista');
  const vacio = document.getElementById('mias-vacio');
  try {
    const datos = await api.get('/api/solicitudes/mias');
    lista.replaceChildren(...datos.map((s) => {
      const editable = s.estado !== 'FORMALIZADA' && s.estado !== 'CANCELADA';
      const acciones = [];
      if (editable) {
        acciones.push(boton('Editar', 'btn btn--secundario btn--pequeno', () => empezarEdicion(s.id)));
        acciones.push(boton('Cancelar', 'btn btn--peligro btn--pequeno', () => pedirCancelacion(s)));
      }
      return tarjetaResumen(s, acciones);
    }));
    vacio.hidden = datos.length > 0;
  } catch (e) {
    toastError(e.message ?? 'No se pudieron cargar tus solicitudes');
  }
}

function pedirCancelacion(s) {
  solicitudACancelar = s;
  document.getElementById('cancelar-tema').textContent = s.tema;
  cerrarModal('modal-detalle');
  abrirModal('modal-cancelar');
}

document.getElementById('cancelar-confirmar').addEventListener('click', async (e) => {
  if (!solicitudACancelar) return;
  e.currentTarget.disabled = true;
  try {
    await api.post(`/api/solicitudes/${encodeURIComponent(solicitudACancelar.id)}/cancelar`);
    exito('Solicitud cancelada');
    cerrarModal('modal-cancelar');
    solicitudACancelar = null;
    cargarMias();
  } catch (err) {
    toastError(err.message ?? 'No se pudo cancelar la solicitud');
    cerrarModal('modal-cancelar');
  } finally {
    e.currentTarget.disabled = false;
  }
});

// ---------------------------------------------------------------- mis propuestas (tutor)
async function cargarMisPropuestas() {
  const lista = document.getElementById('propuestas-lista');
  const vacio = document.getElementById('propuestas-vacio');
  try {
    const datos = await api.get('/api/propuestas/mias');
    lista.replaceChildren(...datos.map((p) => {
      const tarjeta = el('article', 'tarjeta solicitud');
      const cabecera = el('div', 'oferta');
      const [textoEstado, claseEstado] = ETIQUETA_ESTADO_PROPUESTA[p.estado] ?? [p.estado, ''];
      cabecera.append(el('h3', 'solicitud__tema', p.solicitudTema ?? 'Solicitud'),
        el('span', `badge ${claseEstado}`.trim(), textoEstado));
      tarjeta.append(cabecera, el('p', 'solicitud__precio', pesos(p.precioCOP)),
        el('p', 'texto-suave', ETIQUETA_TIPO_PROPUESTA[p.tipo] ?? p.tipo));
      if (p.estado === 'CONTRAOFERTA_ESTUDIANTE') {
        tarjeta.appendChild(el('p', 'propuesta__aviso', `El estudiante propone ${pesos(p.contraofertaCOP)}. Respóndele desde el detalle.`));
      }
      if (p.solicitudEstado) {
        tarjeta.appendChild(el('p', 'texto-suave', `Solicitud: ${(ETIQUETA_ESTADO[p.solicitudEstado] ?? [p.solicitudEstado])[0]}`));
      }
      const pie = el('div', 'solicitud__acciones');
      pie.appendChild(boton('Ver solicitud', 'btn btn--borde btn--pequeno', () => abrirDetalle(p.solicitudId)));
      tarjeta.appendChild(pie);
      return tarjeta;
    }));
    vacio.hidden = datos.length > 0;
  } catch (e) {
    toastError(e.message ?? 'No se pudieron cargar tus propuestas');
  }
}

/** Tras cambiar una propuesta se actualizan las listas que ya estaban cargadas. */
function refrescarListas() {
  if (cargadas.has('mias')) cargarMias();
  if (cargadas.has('propuestas')) cargarMisPropuestas();
  if (cargadas.has('explorar')) buscar(paginaActual);
}

// ---------------------------------------------------------------- detalle
async function abrirDetalle(id) {
  const cuerpo = document.getElementById('detalle-cuerpo');
  const pie = document.getElementById('detalle-pie');
  try {
    const s = await api.get(`/api/solicitudes/${encodeURIComponent(id)}`);
    document.getElementById('detalle-titulo').textContent = s.tema;
    const datos = el('dl', 'datos');
    const fila = (titulo, valor) => {
      const d = el('div');
      d.append(el('dt', '', titulo), el('dd', '', valor));
      datos.appendChild(d);
    };
    fila('Estado', (ETIQUETA_ESTADO[s.estado] ?? [s.estado])[0]);
    fila('Tipo de servicio', ETIQUETA_TIPO[s.tipoServicio] ?? s.tipoServicio);
    fila('Fecha y hora', fecha(s.fechaHora));
    fila('Duración', ETIQUETA_DURACION[s.duracion] ?? s.duracion);
    fila('Modalidad', ETIQUETA_MODALIDAD[s.modalidad] ?? s.modalidad);
    if (s.zonaEncuentro) fila('Zona de encuentro', s.zonaEncuentro);
    fila('Precio inicial', pesos(s.precioInicialCOP));
    fila('Propuestas', String(s.totalPropuestas));
    const nodos = [el('p', 'solicitud__descripcion', s.descripcion), datos];
    if (s.archivos?.length) {
      const lista = el('ul', 'fila-badges');
      lista.setAttribute('aria-label', 'Archivos adjuntos');
      s.archivos.forEach((a) => {
        const li = el('li');
        li.appendChild(boton(`${a.nombre} (${Number(a.tamanoMB).toFixed(2)} MB)`, 'btn btn--borde btn--pequeno', async () => {
          try { await descargarArchivo(a.archivoId, a.nombre); } catch (err) { toastError(err.message ?? 'No se pudo descargar'); }
        }));
        lista.appendChild(li);
      });
      nodos.push(el('h3', '', 'Archivos adjuntos'), lista);
    }
    const seccionPropuestas = el('section', 'propuestas-seccion');
    seccionPropuestas.setAttribute('aria-live', 'polite');
    nodos.push(seccionPropuestas);
    const seccionChat = el('section', 'chat-seccion');
    nodos.push(seccionChat);
    cuerpo.replaceChildren(...nodos);
    const sesion = obtenerUsuario();
    if (sesion) {
      montarPropuestas(seccionPropuestas, s, { usuarioId: sesion.id, rol: rolActivo(), alCambiar: refrescarListas });
      // Chat por tutor (el enlace de una notificación puede traer ?tutorId= para abrir ese hilo).
      montarMensajeria(seccionChat, s, {
        usuarioId: sesion.id, rol: rolActivo(),
        tutorIdInicial: new URLSearchParams(window.location.search).get('tutorId'),
      });
    }

    const acciones = [boton('Cerrar', 'btn btn--borde', () => cerrarModal('modal-detalle'))];
    const usuario = obtenerUsuario();
    const esDueno = usuario && usuario.id === s.estudianteId && rolActivo() === 'ESTUDIANTE';
    if (esDueno && s.estado !== 'FORMALIZADA' && s.estado !== 'CANCELADA') {
      acciones.unshift(
        boton('Cancelar solicitud', 'btn btn--peligro', () => pedirCancelacion({ id: s.id, tema: s.tema })),
        boton('Editar', 'btn btn--secundario', () => { cerrarModal('modal-detalle'); empezarEdicion(s.id); }),
      );
    }
    pie.replaceChildren(...acciones);
    abrirModal('modal-detalle');
  } catch (e) {
    toastError(e.message ?? 'No se pudo cargar la solicitud');
  }
}

// ---------------------------------------------------------------- inicio
(async function iniciar() {
  try {
    const usuario = await exigirSesion();
    if (!usuario) return;
    if (rolActivo() === 'ESTUDIANTE') pestanas = ['explorar', 'publicar', 'mias'];
    if (rolActivo() === 'TUTOR') pestanas = ['explorar', 'propuestas'];
    enlazarPestanas();
    document.getElementById('cargando').hidden = true;
    document.getElementById('contenido').hidden = false;
    activarPestana('explorar');
    const idDirecto = new URLSearchParams(window.location.search).get('id');
    if (idDirecto) abrirDetalle(idDirecto);
  } catch (err) {
    document.getElementById('cargando').hidden = true;
    mostrarAviso(err.message ?? 'No se pudo cargar la página. Inténtalo de nuevo.', 'error');
  }
})();
