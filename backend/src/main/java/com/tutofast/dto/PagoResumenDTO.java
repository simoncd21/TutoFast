package com.tutofast.dto;

import java.math.BigDecimal;

import com.tutofast.modelo.EstadoPago;

/** Estado del pago de un servicio. Nunca incluye datos de tarjeta, solo la referencia de la pasarela simulada. */
public record PagoResumenDTO(EstadoPago estado, BigDecimal montoRetenidoCOP, BigDecimal comisionCOP,
                             BigDecimal montoNetoCOP, String referencia) {
}
