package com.tutofast.dto;

import com.tutofast.modelo.ResultadoApelacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/admin/sanciones/{id}/apelaciones/{apelacionId}/resolver}. */
public record ResolucionApelacionEntradaDTO(@NotNull(message = "El resultado es obligatorio") ResultadoApelacion resultado,
                                            @NotBlank(message = "La observación es obligatoria")
                                            @Size(max = 1000, message = "La observación admite hasta 1000 caracteres") String observacion) {
}
