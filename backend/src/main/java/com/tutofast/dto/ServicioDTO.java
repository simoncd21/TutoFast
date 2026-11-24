package com.tutofast.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.tutofast.modelo.Duracion;
import com.tutofast.modelo.EstadoServicio;
import com.tutofast.modelo.Modalidad;

public record ServicioDTO(String id, String solicitudId, String propuestaId, String estudianteId, String tutorId,
                          BigDecimal precioFinalCOP, Duracion duracion, Modalidad modalidad, String lugarOEnlace,
                          Instant fechaHora, BigDecimal comisionPct, Instant fechaConfirmacion, Instant fechaInicio,
                          Instant fechaFinalizacion, EstadoServicio estado) {
}
