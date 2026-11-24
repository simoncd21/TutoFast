package com.tutofast.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Entrada y salida de {@code PUT /api/usuarios/yo/perfil-estudiante}. */
public record PerfilEstudianteDTO(
        @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "programaId debe ser un identificador de 24 caracteres hexadecimales")
        String programaId,

        @NotNull(message = "El semestre es obligatorio")
        @Min(value = 1, message = "El semestre mínimo es 1")
        @Max(value = 14, message = "El semestre máximo es 14")
        Integer semestre) {
}
