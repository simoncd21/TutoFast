package com.tutofast.repositorio;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.tutofast.modelo.SolicitudTutoria;

/** Consultas dinámicas de solicitudes que no se pueden expresar con métodos derivados. */
public interface RepositorioSolicitudTutoriaPersonalizado {

    /** Búsqueda con filtros opcionales (R07), orden y paginación. */
    Page<SolicitudTutoria> buscar(FiltroSolicitudes filtro, Pageable paginacion);
}
