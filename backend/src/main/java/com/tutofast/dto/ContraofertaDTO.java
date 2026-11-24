package com.tutofast.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/propuestas/{id}/contraofertar}. */
public record ContraofertaDTO(@NotNull(message = "El precio de la contraoferta es obligatorio")
                              @Positive(message = "El precio debe ser mayor que cero") BigDecimal precioCOP,
                              @Size(max = 300, message = "La nota admite hasta 300 caracteres") String nota) {
}
