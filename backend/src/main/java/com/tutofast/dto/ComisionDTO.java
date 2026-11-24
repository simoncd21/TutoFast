package com.tutofast.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ComisionDTO(String id, BigDecimal porcentaje, Instant vigenteDesde, Instant vigenteHasta) {
}
