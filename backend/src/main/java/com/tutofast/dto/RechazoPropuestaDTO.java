package com.tutofast.dto;

import jakarta.validation.constraints.Size;

/** Cuerpo opcional de {@code POST /api/propuestas/{id}/rechazar}. */
public record RechazoPropuestaDTO(@Size(max = 300, message = "El motivo admite hasta 300 caracteres") String motivo) {
}
