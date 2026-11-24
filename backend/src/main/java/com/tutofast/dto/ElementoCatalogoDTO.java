package com.tutofast.dto;

/** Elemento del catálogo académico (universidad, facultad, programa o asignatura). */
public sealed interface ElementoCatalogoDTO
        permits UniversidadDTO, FacultadDTO, ProgramaDTO, AsignaturaDTO {
}
