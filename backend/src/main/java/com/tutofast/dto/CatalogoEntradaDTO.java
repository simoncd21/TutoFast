package com.tutofast.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de alta y edición del catálogo. Al crear se usa el identificador del padre que corresponde al tipo
 * ({@code universidadId} para facultades, {@code facultadId} para programas, {@code programaId} para asignaturas);
 * al editar solo cambian {@code nombre} y, en asignaturas, {@code codigo}.
 */
public record CatalogoEntradaDTO(
        @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "universidadId debe ser un identificador de 24 caracteres hexadecimales")
        String universidadId,

        @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "facultadId debe ser un identificador de 24 caracteres hexadecimales")
        String facultadId,

        @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "programaId debe ser un identificador de 24 caracteres hexadecimales")
        String programaId,

        @Size(max = 20, message = "El código no puede superar 20 caracteres")
        String codigo,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String nombre) {
}
