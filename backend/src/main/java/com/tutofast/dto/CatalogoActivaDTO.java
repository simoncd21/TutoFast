package com.tutofast.dto;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code PATCH /api/catalogos/{tipo}/{id}/activa} (borrado lógico). */
public record CatalogoActivaDTO(@NotNull(message = "El campo activa es obligatorio") Boolean activa) {
}
