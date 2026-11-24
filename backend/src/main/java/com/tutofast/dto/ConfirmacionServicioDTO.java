package com.tutofast.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/solicitudes/{id}/confirmar}: token opaco del simulador (nunca datos de tarjeta). */
public record ConfirmacionServicioDTO(
        @NotBlank(message = "El token de pago simulado es obligatorio")
        @Size(max = 64, message = "El token de pago admite hasta 64 caracteres") String tokenPagoSimulado) {
}
