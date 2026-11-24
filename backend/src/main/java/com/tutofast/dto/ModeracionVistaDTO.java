package com.tutofast.dto;

import java.time.Instant;

import com.tutofast.modelo.DecisionModeracion;

/** Decisión del administrador sobre un mensaje o una reseña reportados. */
public record ModeracionVistaDTO(DecisionModeracion decision, String observacion, String moderadoPor, Instant fecha,
                                 String sancionId) {
}
