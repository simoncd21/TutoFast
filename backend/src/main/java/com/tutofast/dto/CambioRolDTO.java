package com.tutofast.dto;

import com.tutofast.modelo.Rol;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code POST /api/usuarios/yo/roles}. */
public record CambioRolDTO(@NotNull(message = "El rol es obligatorio") Rol rol) {
}
