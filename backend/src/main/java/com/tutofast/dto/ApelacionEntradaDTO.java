package com.tutofast.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Parte {@code datos} de {@code POST /api/sanciones/{id}/apelaciones}. */
public record ApelacionEntradaDTO(@NotBlank(message = "El descargo es obligatorio")
                                  @Size(min = 10, max = 2000, message = "El descargo debe tener entre 10 y 2000 caracteres") String descargo) {
}
