package com.tutofast.dto;

import com.tutofast.modelo.TipoSoporte;

import jakarta.validation.constraints.NotNull;

/** Parte JSON {@code datos} de {@code POST /api/usuarios/yo/soportes}. */
public record SoporteSolicitudDTO(@NotNull(message = "El tipo de soporte es obligatorio") TipoSoporte tipo) {
}
