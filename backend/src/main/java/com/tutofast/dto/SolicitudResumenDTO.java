package com.tutofast.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.tutofast.modelo.Duracion;
import com.tutofast.modelo.EstadoSolicitud;
import com.tutofast.modelo.Modalidad;

public record SolicitudResumenDTO(String id, String tema, String asignaturaNombre, String programaNombre,
                                  Modalidad modalidad, Duracion duracion, BigDecimal precioInicialCOP,
                                  Instant fechaHora, EstadoSolicitud estado, int totalPropuestas,
                                  String estudianteNombre, BigDecimal estudianteConfiabilidad) {
}
