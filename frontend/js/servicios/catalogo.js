// Catálogo académico (universidad → facultad → programa → asignatura) y selects encadenados reutilizables.
import { api } from './api.js';
import { error as toastError } from '../componentes/alertas.js';

const base = '/api/catalogos';
const id = encodeURIComponent;

export const listarUniversidades = (soloActivas = true) => api.get(`${base}/universidades?soloActivas=${soloActivas}`);
export const listarFacultades = (universidadId, soloActivas = true) =>
  api.get(`${base}/universidades/${id(universidadId)}/facultades?soloActivas=${soloActivas}`);
export const listarProgramas = (facultadId, soloActivas = true) =>
  api.get(`${base}/facultades/${id(facultadId)}/programas?soloActivas=${soloActivas}`);
export const listarAsignaturas = (programaId, soloActivas = true) =>
  api.get(`${base}/programas/${id(programaId)}/asignaturas?soloActivas=${soloActivas}`);
export const consultarPrograma = (programaId) => api.get(`${base}/programas/${id(programaId)}`);
export const consultarAsignatura = (asignaturaId) => api.get(`${base}/asignaturas/${id(asignaturaId)}`);

/** Reemplaza las opciones de un <select>; la primera es el marcador («Selecciona…»). */
export function llenarSelect(select, elementos, marcador, etiqueta = (e) => e.nombre) {
  const opciones = [new Option(marcador, '')];
  elementos.forEach((e) => opciones.push(new Option(etiqueta(e), e.id)));
  select.replaceChildren(...opciones);
  select.disabled = elementos.length === 0;
}

/**
 * Enlaza tres <select> (universidad, facultad, programa): al elegir uno se cargan las opciones del siguiente.
 * `alCambiarPrograma(programaId | '')` se invoca cada vez que cambia el programa elegido.
 * Devuelve `{ iniciar(programaId?), programaId() }`.
 */
export function enlazarSelectsAcademicos({ universidad, facultad, programa }, { alCambiarPrograma } = {}) {
  let secuencia = 0; // descarta respuestas atrasadas si la persona cambia de opción rápido

  const reiniciar = (select, marcador) => llenarSelect(select, [], marcador);
  const avisar = () => alCambiarPrograma?.(programa.value);

  async function cargar(select, pedir, marcador) {
    const mia = ++secuencia;
    try {
      const datos = await pedir();
      if (mia === secuencia) llenarSelect(select, datos, marcador);
    } catch (e) {
      if (mia === secuencia) toastError(e.message ?? 'No se pudo cargar el catálogo académico');
    }
  }

  universidad.addEventListener('change', async () => {
    reiniciar(facultad, 'Selecciona una facultad');
    reiniciar(programa, 'Selecciona un programa');
    if (universidad.value) await cargar(facultad, () => listarFacultades(universidad.value), 'Selecciona una facultad');
    avisar();
  });
  facultad.addEventListener('change', async () => {
    reiniciar(programa, 'Selecciona un programa');
    if (facultad.value) await cargar(programa, () => listarProgramas(facultad.value), 'Selecciona un programa');
    avisar();
  });
  programa.addEventListener('change', avisar);

  return {
    /** Carga las universidades y, si se indica un programa, preselecciona toda su cadena. */
    async iniciar(programaId = null) {
      reiniciar(facultad, 'Selecciona una facultad');
      reiniciar(programa, 'Selecciona un programa');
      try {
        const universidades = await listarUniversidades();
        llenarSelect(universidad, universidades, 'Selecciona una universidad');
        if (!programaId) return;
        const detalle = await consultarPrograma(programaId);
        const [facultades, programas] = await Promise.all([
          listarFacultades(detalle.universidad.id),
          listarProgramas(detalle.facultad.id),
        ]);
        llenarSelect(facultad, facultades, 'Selecciona una facultad');
        llenarSelect(programa, programas, 'Selecciona un programa');
        universidad.value = detalle.universidad.id;
        facultad.value = detalle.facultad.id;
        programa.value = detalle.programa.id;
        avisar();
      } catch (e) {
        toastError(e.message ?? 'No se pudo cargar el catálogo académico');
      }
    },
    programaId: () => programa.value,
  };
}
