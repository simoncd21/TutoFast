package com.tutofast.dto;

import java.time.Instant;

import com.tutofast.modelo.AccionPago;
import com.tutofast.modelo.DecisionReporte;

/** Decisión del administrador sobre un reporte. */
public record ResolucionVistaDTO(DecisionReporte decision, AccionPago accionPago, String observacion,
                                 ParticipanteDTO resueltoPor, Instant fecha) {
}
