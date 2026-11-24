package com.tutofast.dto;

public record ProgramaDTO(String id, String facultadId, String nombre, boolean activa)
        implements ElementoCatalogoDTO {
}
