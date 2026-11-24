package com.tutofast.dto;

import java.time.Instant;

/** Elemento de la cola de moderación: el mensaje reportado con su contexto. {@code moderacion} es nula si está pendiente. */
public record MensajeReportadoDTO(MensajeDTO mensaje, String solicitudId, String tutorId, String tutorNombre,
                                  String motivoReporte, String detalleReporte, ParticipanteDTO reportante,
                                  Instant fechaReporte, ModeracionVistaDTO moderacion) {
}
