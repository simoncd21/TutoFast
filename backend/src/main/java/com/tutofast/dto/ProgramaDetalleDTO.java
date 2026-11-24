package com.tutofast.dto;

/** Programa académico con su jerarquía (facultad → universidad); sirve para precargar selects encadenados. */
public record ProgramaDetalleDTO(ProgramaDTO programa, FacultadDTO facultad, UniversidadDTO universidad) {
}
