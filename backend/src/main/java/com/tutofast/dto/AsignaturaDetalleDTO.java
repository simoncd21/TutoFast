package com.tutofast.dto;

/** Asignatura con su jerarquía completa (programa → facultad → universidad). */
public record AsignaturaDetalleDTO(AsignaturaDTO asignatura, ProgramaDTO programa, FacultadDTO facultad,
                                   UniversidadDTO universidad) {
}
