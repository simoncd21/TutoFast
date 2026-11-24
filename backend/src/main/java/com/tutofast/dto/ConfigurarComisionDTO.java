package com.tutofast.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code PUT /api/comision}. */
public record ConfigurarComisionDTO(
        @NotNull(message = "El porcentaje es obligatorio")
        @DecimalMin(value = "0", message = "El porcentaje mínimo es 0")
        @DecimalMax(value = "50", message = "El porcentaje máximo es 50")
        BigDecimal porcentaje) {
}
