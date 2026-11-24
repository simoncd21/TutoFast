package com.tutofast.dto;

import com.tutofast.modelo.EstadoCuenta;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code PATCH /api/admin/usuarios/{id}/estado}. */
public record CambioEstadoCuentaDTO(
        @NotNull(message = "El estado de la cuenta es obligatorio")
        EstadoCuenta estadoCuenta,

        @Size(max = 300, message = "El motivo no puede superar 300 caracteres")
        String motivo) {
}
