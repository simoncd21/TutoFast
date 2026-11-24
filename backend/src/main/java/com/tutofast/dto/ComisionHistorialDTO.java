package com.tutofast.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Fila del historial de comisión (solo administrador: incluye quién la configuró). */
public record ComisionHistorialDTO(String id, BigDecimal porcentaje, Instant vigenteDesde, Instant vigenteHasta,
                                   String modificadoPor) {
}
