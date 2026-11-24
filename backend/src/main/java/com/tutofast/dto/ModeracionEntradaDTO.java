package com.tutofast.dto;

import com.tutofast.modelo.DecisionModeracion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de las acciones de moderación (mensajes y reseñas reportados). La sanción es opcional. */
public record ModeracionEntradaDTO(@NotNull(message = "La decisión es obligatoria") DecisionModeracion decision,
                                   @Size(max = 500, message = "La observación admite hasta 500 caracteres") String observacion,
                                   @Valid SancionEntradaDTO sancion) {
}
