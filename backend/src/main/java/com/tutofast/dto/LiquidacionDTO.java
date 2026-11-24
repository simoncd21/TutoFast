package com.tutofast.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.tutofast.modelo.EstadoPago;

public record LiquidacionDTO(String servicioId, BigDecimal precioFinalCOP, BigDecimal comisionPct,
                             BigDecimal comisionCOP, BigDecimal montoNetoCOP, EstadoPago estado,
                             String referenciaPasarela, Instant fechaRetencion, Instant fechaLiquidacion) {
}
