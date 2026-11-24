package com.tutofast.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.tutofast.modelo.Duracion;
import com.tutofast.modelo.EstadoServicio;
import com.tutofast.modelo.Modalidad;

/** Servicio acordado, con los participantes por nombre y el estado de su pago ({@code pago} puede ser nulo). */
public record ServicioDTO(String id, String solicitudId, String propuestaId, String tema, ParticipanteDTO estudiante,
                          ParticipanteDTO tutor, BigDecimal precioFinalCOP, Duracion duracion, Modalidad modalidad,
                          String lugarOEnlace, Instant fechaHora, BigDecimal comisionPct, Instant fechaConfirmacion,
                          Instant fechaInicio, Instant fechaFinalizacion, EstadoServicio estado, PagoResumenDTO pago) {
}
