package com.tutofast.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.tutofast.modelo.Duracion;
import com.tutofast.modelo.EstadoPropuesta;
import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.TipoPropuesta;

public record PropuestaVistaDTO(String id, String solicitudId, String tutorId, String tutorNombre,
                                TipoPropuesta tipo, BigDecimal precioCOP, Duracion duracion, Modalidad modalidad,
                                String nota, BigDecimal contraofertaCOP, EstadoPropuesta estado,
                                Instant fechaCreacion, Instant fechaActualizacion) {
}
