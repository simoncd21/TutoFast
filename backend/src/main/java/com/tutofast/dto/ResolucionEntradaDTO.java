package com.tutofast.dto;

import com.tutofast.modelo.AccionPago;
import com.tutofast.modelo.DecisionReporte;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/admin/reportes/{id}/resolver}. {@code accionPago} por omisión es NINGUNA; la sanción es opcional. */
public record ResolucionEntradaDTO(@NotNull(message = "La decisión es obligatoria") DecisionReporte decision,
                                   AccionPago accionPago,
                                   @NotBlank(message = "La observación es obligatoria")
                                   @Size(max = 1000, message = "La observación admite hasta 1000 caracteres") String observacion,
                                   @Valid SancionEntradaDTO sancion) {
}
