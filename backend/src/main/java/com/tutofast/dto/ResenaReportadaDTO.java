package com.tutofast.dto;

import java.time.Instant;

/** Reseña reportada en la cola de moderación (con el autor y el destinatario por nombre). */
public record ResenaReportadaDTO(CalificacionDTO calificacion, ParticipanteDTO autor, ParticipanteDTO destinatario,
                                 String motivoReporte, ParticipanteDTO reportante, Instant fechaReporte,
                                 ModeracionVistaDTO moderacion) {
}
