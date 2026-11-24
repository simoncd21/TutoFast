// Panel de administración: catálogos, comisión, verificación de tutores, usuarios y auditoría.
// Todo el contenido dinámico se construye con textContent (nunca innerHTML).
import { iniciarEncabezado } from '../componentes/encabezado.js';
import { exito, error as toastError } from '../componentes/alertas.js';
import { api, ErrorApi, descargarArchivo } from '../servicios/api.js';
import {
  listarUniversidades, listarFacultades, listarProgramas, listarAsignaturas, llenarSelect,
} from '../servicios/catalogo.js';
import { montarChat } from '../componentes/chat.js';
import { cargarPanelReportes } from './admin-reportes.js';
import { exigirSesion } from '../sesion.js';

iniciarEncabezado();
document.getElementById('anio').textContent = new Date().getFullYear();

const aviso = document.getElementById('aviso');

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

/** Muestra los errores de validación del servidor junto a cada campo y avisa con un toast. */
function errorServidor(formulario, e) {
  if (formulario && e instanceof ErrorApi && e.detalles?.length) {
    e.detalles.forEach((d) => marcarError(formulario, d.campo, d.mensaje));
    formulario.querySelector('.invalido')?.focus();
  }
  toastError(e.message ?? 'Ocurrió un error inesperado');
}

async function ocupado(boton, accion) {
  if (!boton) return accion();
  const texto = boton.textContent;
  boton.disabled = true;
  try {
    return await accion();
  } finally {
    boton.disabled = false;
    boton.textContent = texto;
  }
}

function fecha(iso) {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
}

function porcentaje(valor) {
  return `${Number(valor).toLocaleString('es-CO', { maximumFractionDigits: 2 })} %`;
}

function boton(texto, clase, alClic) {
  const b = el('button', clase, texto);
  b.type = 'button';
  b.addEventListener('click', alClic);
  return b;
}

function badge(texto, clase = '') {
  return el('span', `badge ${clase}`.trim(), texto);
}

/** Construye una tabla: `columnas` = [{ titulo, celda(fila) → nodo|texto }]. */
function pintarTabla(tabla, columnas, filas, vacio) {
  const cabecera = el('thead');
  const filaCab = el('tr');
  columnas.forEach((c) => { const th = el('th', '', c.titulo); th.scope = 'col'; filaCab.appendChild(th); });
  cabecera.appendChild(filaCab);

  const cuerpo = el('tbody');
  if (filas.length === 0) {
    const tr = el('tr');
    const td = el('td', 'texto-suave', vacio);
    td.colSpan = columnas.length;
    tr.appendChild(td);
    cuerpo.appendChild(tr);
  }
  filas.forEach((fila) => {
    const tr = el('tr');
    columnas.forEach((c) => {
      const td = el('td', c.clase ?? '');
      const contenido = c.celda(fila);
      if (contenido instanceof Node) td.appendChild(contenido);
      else td.textContent = contenido ?? '—';
      tr.appendChild(td);
    });
    cuerpo.appendChild(tr);
  });
  tabla.replaceChildren(cabecera, cuerpo);
}

// ---------------------------------------------------------------- pestañas
const PESTANAS = ['catalogos', 'comision', 'verificacion', 'usuarios', 'moderacion', 'reportes', 'cierre', 'auditoria'];
const cargadas = new Set();
const cargadores = {
  catalogos: () => cargarUniversidades(),
  comision: () => cargarComision(),
  verificacion: () => cargarCola(),
  usuarios: () => cargarUsuarios(0),
  moderacion: () => Promise.all([cargarMensajesReportados(), cargarResenasReportadas()]),
  reportes: () => cargarPanelReportes(),
  cierre: () => {},
  auditoria: () => cargarAuditoria(0),
};

function activarPestana(nombre, enfocar = false) {
  PESTANAS.forEach((p) => {
    const boton = document.getElementById(`pestana-${p}`);
    const activa = p === nombre;
    boton.setAttribute('aria-selected', String(activa));
    boton.tabIndex = activa ? 0 : -1;
    document.getElementById(`panel-${p}`).hidden = !activa;
  });
  if (enfocar) document.getElementById(`pestana-${nombre}`).focus();
  if (!cargadas.has(nombre)) {
    cargadas.add(nombre);
    cargadores[nombre]();
  }
}

PESTANAS.forEach((nombre, i) => {
  const b = document.getElementById(`pestana-${nombre}`);
  b.addEventListener('click', () => activarPestana(nombre));
  b.addEventListener('keydown', (e) => {
    const paso = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
    if (!paso) return;
    e.preventDefault();
    activarPestana(PESTANAS[(i + paso + PESTANAS.length) % PESTANAS.length], true);
  });
});

// ---------------------------------------------------------------- catálogos
const selUniversidad = document.getElementById('cat-universidad');
const selFacultad = document.getElementById('cat-facultad');
const selPrograma = document.getElementById('cat-programa');
const formFacultad = document.getElementById('form-facultad');
const formPrograma = document.getElementById('form-programa');
const formAsignatura = document.getElementById('form-asignatura');
const formUniversidad = document.getElementById('form-universidad');

const nombreConEstado = (e) => (e.activa === false || e.activo === false ? `${e.nombre} (inactivo)` : e.nombre);
const estaActivo = (e) => (e.activa ?? e.activo) !== false;

function columnasCatalogo(tipo, recargar, conCodigo = false) {
  const columnas = [];
  if (conCodigo) columnas.push({ titulo: 'Código', celda: (e) => e.codigo });
  columnas.push(
    { titulo: 'Nombre', celda: (e) => el('span', estaActivo(e) ? '' : 'inactivo', e.nombre) },
    { titulo: 'Estado', celda: (e) => (estaActivo(e) ? badge('Activo', 'badge--exito') : badge('Inactivo', 'badge--error')) },
    {
      titulo: 'Acciones',
      clase: 'acciones',
      celda: (e) => {
        const caja = el('div', 'fila-badges');
        caja.append(
          boton('Editar', 'btn btn--borde btn--pequeno', () => editarElemento(tipo, e, conCodigo, recargar)),
          boton(estaActivo(e) ? 'Desactivar' : 'Reactivar', 'btn btn--borde btn--pequeno',
            () => cambiarActivo(tipo, e, recargar)),
        );
        return caja;
      },
    },
  );
  return columnas;
}

async function editarElemento(tipo, elemento, conCodigo, recargar) {
  const nombre = window.prompt('Nombre', elemento.nombre);
  if (nombre === null) return;
  const cuerpo = { nombre: nombre.trim() };
  if (conCodigo) {
    const codigo = window.prompt('Código', elemento.codigo);
    if (codigo === null) return;
    cuerpo.codigo = codigo.trim();
  }
  if (!cuerpo.nombre || (conCodigo && !cuerpo.codigo)) { toastError('El nombre y el código no pueden estar vacíos'); return; }
  try {
    await api.put(`/api/catalogos/${tipo}/${encodeURIComponent(elemento.id)}`, cuerpo);
    exito('Cambios guardados');
    await recargar();
  } catch (e) {
    errorServidor(null, e);
  }
}

async function cambiarActivo(tipo, elemento, recargar) {
  const activar = !estaActivo(elemento);
  try {
    await api.patch(`/api/catalogos/${tipo}/${encodeURIComponent(elemento.id)}/activa`, { activa: activar });
    exito(activar ? 'Elemento reactivado' : 'Elemento desactivado');
    await recargar();
  } catch (e) {
    errorServidor(null, e);
  }
}

function conservar(select, id) {
  if ([...select.options].some((o) => o.value === id)) select.value = id;
}

async function cargarUniversidades() {
  try {
    const items = await listarUniversidades(false);
    const previa = selUniversidad.value;
    llenarSelect(selUniversidad, items, 'Selecciona una universidad', nombreConEstado);
    conservar(selUniversidad, previa);
    pintarTabla(document.getElementById('tabla-universidades'),
      columnasCatalogo('universidades', cargarUniversidades), items, 'Aún no hay universidades.');
    await cargarFacultades();
  } catch (e) { toastError(e.message); }
}

async function cargarFacultades() {
  const universidadId = selUniversidad.value;
  document.getElementById('vacio-facultades').hidden = Boolean(universidadId);
  document.getElementById('contenedor-facultades').hidden = !universidadId;
  formFacultad.hidden = !universidadId;
  if (!universidadId) {
    llenarSelect(selFacultad, [], 'Selecciona una facultad');
    await cargarProgramas();
    return;
  }
  try {
    const items = await listarFacultades(universidadId, false);
    const previa = selFacultad.value;
    llenarSelect(selFacultad, items, 'Selecciona una facultad', nombreConEstado);
    conservar(selFacultad, previa);
    pintarTabla(document.getElementById('tabla-facultades'),
      columnasCatalogo('facultades', cargarFacultades), items, 'Esta universidad aún no tiene facultades.');
    await cargarProgramas();
  } catch (e) { toastError(e.message); }
}

async function cargarProgramas() {
  const facultadId = selFacultad.value;
  document.getElementById('vacio-programas').hidden = Boolean(facultadId);
  document.getElementById('contenedor-programas').hidden = !facultadId;
  formPrograma.hidden = !facultadId;
  if (!facultadId) {
    llenarSelect(selPrograma, [], 'Selecciona un programa');
    await cargarAsignaturas();
    return;
  }
  try {
    const items = await listarProgramas(facultadId, false);
    const previa = selPrograma.value;
    llenarSelect(selPrograma, items, 'Selecciona un programa', nombreConEstado);
    conservar(selPrograma, previa);
    pintarTabla(document.getElementById('tabla-programas'),
      columnasCatalogo('programas', cargarProgramas), items, 'Esta facultad aún no tiene programas.');
    await cargarAsignaturas();
  } catch (e) { toastError(e.message); }
}

async function cargarAsignaturas() {
  const programaId = selPrograma.value;
  document.getElementById('vacio-asignaturas').hidden = Boolean(programaId);
  document.getElementById('contenedor-asignaturas').hidden = !programaId;
  formAsignatura.hidden = !programaId;
  if (!programaId) return;
  try {
    const items = await listarAsignaturas(programaId, false);
    pintarTabla(document.getElementById('tabla-asignaturas'),
      columnasCatalogo('asignaturas', cargarAsignaturas, true), items, 'Este programa aún no tiene asignaturas.');
  } catch (e) { toastError(e.message); }
}

selUniversidad.addEventListener('change', () => { selFacultad.value = ''; cargarFacultades(); });
selFacultad.addEventListener('change', () => { selPrograma.value = ''; cargarProgramas(); });
selPrograma.addEventListener('change', cargarAsignaturas);

function enlazarAlta(formulario, ruta, construirCuerpo, validar, alCrear, mensaje) {
  formulario.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    limpiarErrores(formulario);
    const cuerpo = construirCuerpo();
    const fallos = validar(cuerpo);
    if (fallos.length) {
      fallos.forEach(([campo, texto]) => marcarError(formulario, campo, texto));
      formulario.querySelector('.invalido')?.focus();
      return;
    }
    try {
      await ocupado(ev.submitter, () => api.post(ruta, cuerpo));
      formulario.reset();
      exito(mensaje);
      await alCrear();
    } catch (e) {
      errorServidor(formulario, e);
    }
  });
}

const exigirNombre = (c) => (c.nombre ? [] : [['nombre', 'El nombre es obligatorio']]);

enlazarAlta(formUniversidad, '/api/catalogos/universidades',
  () => ({ nombre: formUniversidad.elements.nombre.value.trim() }),
  exigirNombre, cargarUniversidades, 'Universidad agregada');

enlazarAlta(formFacultad, '/api/catalogos/facultades',
  () => ({ universidadId: selUniversidad.value, nombre: formFacultad.elements.nombre.value.trim() }),
  exigirNombre, cargarFacultades, 'Facultad agregada');

enlazarAlta(formPrograma, '/api/catalogos/programas',
  () => ({ facultadId: selFacultad.value, nombre: formPrograma.elements.nombre.value.trim() }),
  exigirNombre, cargarProgramas, 'Programa agregado');

enlazarAlta(formAsignatura, '/api/catalogos/asignaturas',
  () => ({
    programaId: selPrograma.value,
    codigo: formAsignatura.elements.codigo.value.trim(),
    nombre: formAsignatura.elements.nombre.value.trim(),
  }),
  (c) => [...(c.codigo ? [] : [['codigo', 'El código es obligatorio']]), ...exigirNombre(c)],
  cargarAsignaturas, 'Asignatura agregada');

// ---------------------------------------------------------------- comisión
const formComision = document.getElementById('form-comision');

async function cargarComision() {
  try {
    const [vigente, historial] = await Promise.all([
      api.get('/api/comision/vigente').catch((e) => (e.estado === 404 ? null : Promise.reject(e))),
      api.get('/api/comision/historial'),
    ]);
    document.getElementById('comision-vigente').textContent = vigente ? porcentaje(vigente.porcentaje) : '—';
    document.getElementById('comision-desde').textContent = vigente
      ? `Vigente desde el ${fecha(vigente.vigenteDesde)}` : 'Aún no hay una comisión configurada.';
    pintarTabla(document.getElementById('tabla-comision'), [
      { titulo: 'Porcentaje', celda: (c) => porcentaje(c.porcentaje) },
      { titulo: 'Desde', celda: (c) => fecha(c.vigenteDesde) },
      { titulo: 'Hasta', celda: (c) => (c.vigenteHasta ? fecha(c.vigenteHasta) : badge('Vigente', 'badge--exito')) },
    ], historial, 'Sin historial.');
  } catch (e) { toastError(e.message ?? 'No se pudo cargar la comisión'); }
}

formComision.addEventListener('submit', async (ev) => {
  ev.preventDefault();
  limpiarErrores(formComision);
  const texto = formComision.elements.porcentaje.value.trim();
  const valor = Number(texto);
  if (texto === '' || Number.isNaN(valor) || valor < 0 || valor > 50) {
    marcarError(formComision, 'porcentaje', 'Ingresa un porcentaje entre 0 y 50');
    formComision.elements.porcentaje.focus();
    return;
  }
  try {
    await ocupado(ev.submitter, () => api.put('/api/comision', { porcentaje: valor }));
    formComision.reset();
    exito(`Comisión actualizada a ${porcentaje(valor)}`);
    await cargarComision();
  } catch (e) {
    errorServidor(formComision, e);
  }
});

// ---------------------------------------------------------------- verificación de tutores
const ETIQUETA_TIPO_SOPORTE = {
  CERTIFICADO_MONITORIA: 'Certificado de monitoría',
  HISTORIAL_CALIFICACIONES: 'Historial de calificaciones',
  CARNE_ESTUDIANTIL: 'Carné estudiantil',
  OTRO: 'Otro',
};
const listaCola = document.getElementById('cola-verificacion');

function itemCola(e) {
  const prioritario = e.prioridad === 'ALTA';
  const item = el('li', `cola__item${prioritario ? ' cola__item--prioridad' : ''}`);

  const cabecera = el('div', 'cola__cabecera');
  const quien = el('div');
  quien.append(el('strong', '', e.nombre), el('div', 'texto-suave', e.correo));
  cabecera.appendChild(quien);
  if (prioritario) cabecera.appendChild(badge('Correo institucional · Prioridad alta', 'badge--prioridad'));
  item.appendChild(cabecera);

  item.appendChild(el('div', 'texto-suave',
    `${ETIQUETA_TIPO_SOPORTE[e.soporte.tipo] ?? e.soporte.tipo} · ${e.soporte.nombreArchivo ?? 'archivo'} · enviado el ${fecha(e.soporte.fechaEnvio)}`));

  const acciones = el('div', 'cola__acciones');
  const observacion = el('input');
  observacion.type = 'text';
  observacion.maxLength = 500;
  observacion.placeholder = 'Observación (obligatoria para rechazar)';
  observacion.setAttribute('aria-label', `Observación sobre el soporte de ${e.nombre}`);
  const error = el('div', 'campo__error');
  error.hidden = true;

  const base = `/api/admin/usuarios/${encodeURIComponent(e.usuarioId)}/soportes/${encodeURIComponent(e.soporte.id)}`;
  async function resolver(boton, accion, cuerpo, mensaje) {
    try {
      await ocupado(boton, () => api.post(`${base}/${accion}`, cuerpo));
      exito(mensaje);
      await cargarCola();
    } catch (err) {
      toastError(err.message);
    }
  }

  acciones.append(
    boton('Descargar', 'btn btn--borde btn--pequeno', async (ev) => {
      try {
        await ocupado(ev.currentTarget, () => descargarArchivo(e.soporte.archivoId, e.soporte.nombreArchivo ?? 'soporte'));
      } catch (err) { toastError(err.message ?? 'No se pudo descargar el archivo'); }
    }),
    observacion,
    boton('Aprobar', 'btn btn--primario btn--pequeno', (ev) => {
      error.hidden = true;
      const texto = observacion.value.trim();
      resolver(ev.currentTarget, 'aprobar', texto ? { observacion: texto } : {}, `Soporte de ${e.nombre} aprobado`);
    }),
    boton('Rechazar', 'btn btn--peligro btn--pequeno', (ev) => {
      const texto = observacion.value.trim();
      if (!texto) {
        error.textContent = 'Escribe el motivo del rechazo';
        error.hidden = false;
        observacion.classList.add('invalido');
        observacion.focus();
        return;
      }
      observacion.classList.remove('invalido');
      error.hidden = true;
      resolver(ev.currentTarget, 'rechazar', { observacion: texto }, `Soporte de ${e.nombre} rechazado`);
    }),
  );
  item.append(acciones, error);
  return item;
}

async function cargarCola() {
  try {
    const cola = await api.get('/api/admin/soportes?estado=EN_REVISION');
    listaCola.replaceChildren(...cola.map(itemCola));
    document.getElementById('cola-vacia').hidden = cola.length > 0;
  } catch (e) { toastError(e.message ?? 'No se pudo cargar la cola de verificación'); }
}
document.getElementById('verificacion-recargar').addEventListener('click', cargarCola);

// ---------------------------------------------------------------- usuarios
const TAMANO_PAGINA = 15;
const formFiltrosUsuarios = document.getElementById('filtros-usuarios');
const ESTADO_CUENTA = {
  ACTIVA: ['Activa', 'badge--exito'],
  SUSPENDIDA: ['Suspendida', 'badge--advertencia'],
  BLOQUEADA: ['Bloqueada', 'badge--error'],
};
const ETIQUETA_ROL = { ESTUDIANTE: 'Estudiante', TUTOR: 'Tutor', ADMINISTRADOR: 'Administrador' };
let paginaUsuarios = 0;

async function cambiarEstadoCuenta(usuario, estadoCuenta) {
  let motivo = '';
  if (estadoCuenta !== 'ACTIVA') {
    motivo = window.prompt(`Motivo para ${estadoCuenta === 'BLOQUEADA' ? 'bloquear' : 'suspender'} a ${usuario.nombre}`);
    if (motivo === null) return;
    if (!motivo.trim()) { toastError('El motivo es obligatorio'); return; }
  }
  try {
    await api.patch(`/api/admin/usuarios/${encodeURIComponent(usuario.id)}/estado`,
      { estadoCuenta, motivo: motivo.trim() || null });
    exito('Estado de la cuenta actualizado');
    await cargarUsuarios(paginaUsuarios);
  } catch (e) { toastError(e.message); }
}

async function cargarUsuarios(pagina) {
  const filtros = new URLSearchParams({ pagina, tamano: TAMANO_PAGINA });
  ['q', 'rol', 'estado'].forEach((c) => {
    const valor = formFiltrosUsuarios.elements[c].value.trim();
    if (valor) filtros.set(c, valor);
  });
  try {
    const datos = await api.get(`/api/admin/usuarios?${filtros}`);
    paginaUsuarios = datos.pagina;
    pintarTabla(document.getElementById('tabla-usuarios'), [
      { titulo: 'Nombre', celda: (u) => u.nombre },
      { titulo: 'Correo', celda: (u) => u.correo },
      { titulo: 'Roles', celda: (u) => u.roles.map((r) => ETIQUETA_ROL[r] ?? r).join(', ') },
      {
        titulo: 'Verificación',
        celda: (u) => (u.nivelVerificacion
          ? badge(u.nivelVerificacion === 'VERIFICADO' ? 'Verificado' : u.nivelVerificacion === 'EN_REVISION' ? 'En revisión' : 'Sin verificar',
            u.nivelVerificacion === 'VERIFICADO' ? 'badge--exito' : 'badge--advertencia')
          : '—'),
      },
      { titulo: 'Registro', celda: (u) => fecha(u.fechaRegistro) },
      { titulo: 'Estado', celda: (u) => badge(...(ESTADO_CUENTA[u.estadoCuenta] ?? [u.estadoCuenta, ''])) },
      {
        titulo: 'Acciones',
        clase: 'acciones',
        celda: (u) => {
          if (u.roles.includes('ADMINISTRADOR')) return '—';
          const caja = el('div', 'fila-badges');
          if (u.estadoCuenta === 'ACTIVA') {
            caja.append(
              boton('Suspender', 'btn btn--borde btn--pequeno', () => cambiarEstadoCuenta(u, 'SUSPENDIDA')),
              boton('Bloquear', 'btn btn--borde btn--pequeno', () => cambiarEstadoCuenta(u, 'BLOQUEADA')),
            );
          } else {
            caja.appendChild(boton('Reactivar', 'btn btn--primario btn--pequeno', () => cambiarEstadoCuenta(u, 'ACTIVA')));
          }
          return caja;
        },
      },
    ], datos.contenido, 'Sin resultados.');
    document.getElementById('usuarios-vacio').hidden = datos.contenido.length > 0;
    document.getElementById('usuarios-pagina').textContent =
      `Página ${datos.pagina + 1} de ${Math.max(datos.totalPaginas, 1)} · ${datos.totalElementos} usuarios`;
    document.getElementById('usuarios-anterior').disabled = datos.pagina <= 0;
    document.getElementById('usuarios-siguiente').disabled = datos.pagina + 1 >= datos.totalPaginas;
  } catch (e) { toastError(e.message ?? 'No se pudieron cargar los usuarios'); }
}

formFiltrosUsuarios.addEventListener('submit', (ev) => { ev.preventDefault(); cargarUsuarios(0); });
document.getElementById('usuarios-anterior').addEventListener('click', () => cargarUsuarios(paginaUsuarios - 1));
document.getElementById('usuarios-siguiente').addEventListener('click', () => cargarUsuarios(paginaUsuarios + 1));

// ---------------------------------------------------------------- auditoría
const formFiltrosAuditoria = document.getElementById('filtros-auditoria');
let paginaAuditoria = 0;

function resumenDetalle(detalle) {
  const pares = Object.entries(detalle ?? {}).filter(([, v]) => v !== null && v !== undefined && v !== '');
  return pares.length ? pares.map(([k, v]) => `${k}: ${v}`).join(' · ') : '—';
}

async function cargarAuditoria(pagina) {
  const filtros = new URLSearchParams({ pagina, tamano: TAMANO_PAGINA });
  const { entidad, desde, hasta } = formFiltrosAuditoria.elements;
  if (entidad.value) filtros.set('entidad', entidad.value);
  if (desde.value) filtros.set('desde', `${desde.value}T00:00:00Z`);
  if (hasta.value) filtros.set('hasta', `${hasta.value}T23:59:59Z`);
  try {
    const datos = await api.get(`/api/admin/auditoria?${filtros}`);
    paginaAuditoria = datos.pagina;
    pintarTabla(document.getElementById('tabla-auditoria'), [
      { titulo: 'Fecha', celda: (r) => fecha(r.fecha) },
      { titulo: 'Usuario', celda: (r) => r.usuarioNombre ?? r.usuarioId ?? 'Sistema' },
      { titulo: 'Acción', celda: (r) => r.accion },
      { titulo: 'Entidad', celda: (r) => r.entidadAfectada },
      { titulo: 'Detalle', celda: (r) => resumenDetalle(r.detalle) },
    ], datos.contenido, 'Sin registros.');
    document.getElementById('auditoria-vacia').hidden = datos.contenido.length > 0;
    document.getElementById('auditoria-pagina').textContent =
      `Página ${datos.pagina + 1} de ${Math.max(datos.totalPaginas, 1)} · ${datos.totalElementos} registros`;
    document.getElementById('auditoria-anterior').disabled = datos.pagina <= 0;
    document.getElementById('auditoria-siguiente').disabled = datos.pagina + 1 >= datos.totalPaginas;
  } catch (e) { toastError(e.message ?? 'No se pudo cargar la auditoría'); }
}

formFiltrosAuditoria.addEventListener('submit', (ev) => { ev.preventDefault(); cargarAuditoria(0); });
document.getElementById('auditoria-anterior').addEventListener('click', () => cargarAuditoria(paginaAuditoria - 1));
document.getElementById('auditoria-siguiente').addEventListener('click', () => cargarAuditoria(paginaAuditoria + 1));

// ---------------------------------------------------------------- moderación (mensajes y reseñas reportados)
const MOTIVO_MENSAJE = {
  CONDUCTA_ABUSIVA: 'Conducta abusiva', ACOSO: 'Acoso', CONTACTO_EXTERNO: 'Contacto o pago fuera de la plataforma',
  FRAUDE_ACADEMICO: 'Fraude académico', SPAM: 'Spam', OTRO: 'Otro',
};
const TIPOS_SANCION = [
  ['', 'Sin sanción'], ['ADVERTENCIA', 'Advertencia'], ['SUSPENSION_TEMPORAL', 'Suspensión temporal'],
  ['BLOQUEO_PERMANENTE', 'Bloqueo permanente'],
];
let adminId = null;
const moderacionAlResolver = [];

/**
 * Formulario de decisión: observación, sanción opcional (solo al confirmar la infracción) y los dos botones.
 * `enviar(cuerpo)` hace la llamada; al terminar bien se recargan las dos colas.
 */
function formularioDecision(enviar, textoConfirmar, objetivoSancion) {
  const form = el('form', 'moderacion__form');
  form.noValidate = true;
  const obs = el('textarea');
  obs.rows = 2;
  obs.maxLength = 500;
  obs.placeholder = 'Observación de la decisión (opcional)';
  obs.setAttribute('aria-label', 'Observación de la decisión');
  const sancion = document.createElement('select');
  sancion.setAttribute('aria-label', `Sanción para ${objetivoSancion}`);
  TIPOS_SANCION.forEach(([v, t]) => sancion.appendChild(new Option(v ? `Sancionar a ${objetivoSancion}: ${t.toLowerCase()}` : 'Sin sanción', v)));
  const fin = el('input');
  fin.type = 'date';
  fin.hidden = true;
  fin.setAttribute('aria-label', 'Último día de la suspensión');
  sancion.addEventListener('change', () => { fin.hidden = sancion.value !== 'SUSPENSION_TEMPORAL'; });
  const desestimar = el('button', 'btn btn--borde btn--pequeno', 'Desestimar reporte');
  desestimar.type = 'button';
  const confirmar = el('button', 'btn btn--peligro btn--pequeno', textoConfirmar);
  confirmar.type = 'button';
  const acciones = el('div', 'moderacion__acciones');
  acciones.append(desestimar, confirmar);
  form.append(obs, sancion, fin, acciones);

  const decidir = async (decision) => {
    const cuerpo = { decision, observacion: obs.value.trim() || undefined };
    if (decision === 'CONFIRMAR_INFRACCION' && sancion.value) {
      cuerpo.sancion = { tipo: sancion.value };
      if (sancion.value === 'SUSPENSION_TEMPORAL') {
        const finIso = fin.value ? new Date(`${fin.value}T23:59:59Z`) : null;
        if (!finIso || finIso <= new Date()) {
          toastError('Indica un último día de suspensión posterior a hoy');
          fin.focus();
          return;
        }
        cuerpo.sancion.fin = finIso.toISOString();
      }
    }
    desestimar.disabled = true;
    confirmar.disabled = true;
    try {
      await enviar(cuerpo);
      exito(decision === 'DESESTIMAR' ? 'Reporte desestimado' : 'Infracción confirmada');
      await Promise.all([cargarMensajesReportados(), cargarResenasReportadas()]);
    } catch (e) {
      toastError(e.message ?? 'No se pudo registrar la decisión');
      desestimar.disabled = false;
      confirmar.disabled = false;
    }
  };
  desestimar.addEventListener('click', () => decidir('DESESTIMAR'));
  confirmar.addEventListener('click', () => decidir('CONFIRMAR_INFRACCION'));
  return form;
}

function itemMensaje(r) {
  const m = r.mensaje;
  const item = el('article', 'moderacion__item');
  const cab = el('div', 'cola__cabecera');
  const fila = el('div', 'fila-badges');
  fila.append(badge(MOTIVO_MENSAJE[r.motivoReporte] ?? r.motivoReporte, 'badge--advertencia'),
    el('span', 'texto-suave', `Reportado por ${r.reportante?.nombre ?? '—'} · ${fecha(r.fechaReporte)}`));
  cab.append(el('strong', '', `Mensaje de ${m.emisorNombre ?? '—'}`), fila);
  item.append(cab,
    el('p', 'texto-suave', `Conversación del hilo con ${r.tutorNombre ?? 'el tutor'} · enviado ${fecha(m.marcaTemporal)}`),
    el('p', 'moderacion__cita', m.contenido ?? '[Sin texto: solo adjunto]'));
  if (m.archivo) {
    item.appendChild(boton(`📎 ${m.archivo.nombre}`, 'btn btn--borde btn--pequeno', async () => {
      try { await descargarArchivo(m.archivo.archivoId, m.archivo.nombre); } catch (e) { toastError(e.message); }
    }));
  }
  if (r.detalleReporte) item.appendChild(el('p', '', `Detalle del reporte: ${r.detalleReporte}`));

  const zonaChat = el('div');
  zonaChat.hidden = true;
  let chat = null;
  const ver = boton('Ver la conversación', 'btn btn--borde btn--pequeno', () => {
    zonaChat.hidden = !zonaChat.hidden;
    chat?.destruir();
    chat = null;
    if (!zonaChat.hidden) {
      chat = montarChat(zonaChat, { solicitudId: r.solicitudId, tutorId: r.tutorId, usuarioId: adminId, rol: 'ADMINISTRADOR' });
    }
  });
  item.append(ver, zonaChat);
  if (r.moderacion) {
    item.appendChild(badge(`Resuelto: ${r.moderacion.decision === 'DESESTIMAR' ? 'desestimado' : 'infracción confirmada'}`, 'badge--exito'));
  } else {
    item.appendChild(formularioDecision(
      (cuerpo) => api.post(`/api/admin/mensajes-reportados/${encodeURIComponent(m.id)}/moderar`, cuerpo),
      'Confirmar infracción', m.emisorNombre ?? 'el autor'));
  }
  return item;
}

function itemResena(r) {
  const c = r.calificacion;
  const item = el('article', 'moderacion__item');
  const cab = el('div', 'cola__cabecera');
  cab.append(el('strong', '', `Reseña de ${r.autor?.nombre ?? '—'} a ${r.destinatario?.nombre ?? '—'}`),
    badge(`Promedio ${Number(c.promedio).toLocaleString('es-CO', { minimumFractionDigits: 2 })}`, 'badge--info'));
  item.append(cab,
    el('p', 'texto-suave', `Reportada por ${r.reportante?.nombre ?? '—'} · ${fecha(r.fechaReporte)}`),
    el('p', 'moderacion__cita', c.comentario ?? '[Sin comentario]'),
    el('p', '', `Motivo del reporte: ${r.motivoReporte}`));
  item.appendChild(formularioDecision(
    (cuerpo) => api.post(`/api/admin/resenas-reportadas/${encodeURIComponent(c.id)}/moderar`, cuerpo),
    'Ocultar reseña', r.autor?.nombre ?? 'el autor'));
  return item;
}

async function cargarMensajesReportados() {
  const lista = document.getElementById('mensajes-reportados');
  try {
    const datos = await api.get('/api/admin/mensajes-reportados');
    lista.replaceChildren(...datos.map(itemMensaje));
    document.getElementById('mensajes-vacio').hidden = datos.length > 0;
  } catch (e) {
    toastError(e.message ?? 'No se pudieron cargar los mensajes reportados');
  }
}

async function cargarResenasReportadas() {
  const lista = document.getElementById('resenas-reportadas');
  try {
    const datos = await api.get('/api/admin/resenas-reportadas');
    lista.replaceChildren(...datos.map(itemResena));
    document.getElementById('resenas-vacio').hidden = datos.length > 0;
  } catch (e) {
    toastError(e.message ?? 'No se pudieron cargar las reseñas reportadas');
  }
}

document.getElementById('moderacion-recargar').addEventListener('click',
  () => Promise.all([cargarMensajesReportados(), cargarResenasReportadas()]));

// ---------------------------------------------------------------- cierre automático (demostración)
const formCierre = document.getElementById('form-cierre');
formCierre.addEventListener('submit', async (e) => {
  e.preventDefault();
  const botonCierre = document.getElementById('cierre-ejecutar');
  const ignorar = document.getElementById('cierre-ignorar-plazo').checked;
  const resultado = document.getElementById('cierre-resultado');
  await ocupado(botonCierre, async () => {
    try {
      const r = await api.post(`/api/pagos/simular-cierre-automatico?ignorarPlazo=${ignorar}`);
      const resumen = el('ul');
      [`Servicios en desarrollo revisados: ${r.revisados}`, `Cerrados y liquidados: ${r.cerrados}`,
        `Omitidos por tener un reporte abierto: ${r.omitidosPorReporte}`,
        `Omitidos porque aún no cumplen 24 h: ${r.omitidosPorPlazo}`, `Con error (se reintentan solos): ${r.fallidos}`,
      ].forEach((t) => resumen.appendChild(el('li', '', t)));
      const nodos = [el('h3', '', 'Resultado'), resumen];
      if (r.serviciosCerrados?.length) {
        const enlaces = el('div', 'fila-badges');
        r.serviciosCerrados.forEach((id) => {
          const a = el('a', 'btn btn--borde btn--pequeno', `Servicio ${id.slice(-6)}`);
          a.href = `negociacion.html?id=${encodeURIComponent(id)}`;
          enlaces.appendChild(a);
        });
        nodos.push(enlaces);
      }
      resultado.replaceChildren(...nodos);
      resultado.hidden = false;
      exito(r.cerrados ? `${r.cerrados} servicio(s) cerrado(s)` : 'No había servicios para cerrar');
    } catch (err) {
      toastError(err.message ?? 'No se pudo ejecutar el cierre automático');
    }
  });
});

// ---------------------------------------------------------------- carga inicial
(async function iniciar() {
  try {
    const usuario = await exigirSesion(['ADMINISTRADOR']);
    if (!usuario) return;
    adminId = usuario.id;
    document.getElementById('cargando').hidden = true;
    document.getElementById('contenido').hidden = false;
    // Los enlaces de las notificaciones abren directamente la moderación o los reportes y apelaciones.
    const hash = window.location.hash;
    activarPestana(['#mensajes', '#resenas'].includes(hash) ? 'moderacion' : hash === '#reportes' ? 'reportes' : 'catalogos');
  } catch (err) {
    document.getElementById('cargando').hidden = true;
    mostrarAviso(err.message ?? 'No se pudo cargar el panel. Inténtalo de nuevo.', 'error');
  }
})();
