package com.tutofast.dto;

import java.math.BigDecimal;

import com.tutofast.modelo.Duracion;
import com.tutofast.modelo.Modalidad;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PATCH /api/servicios/{id}/enlace}. Precio, duración y modalidad existen solo para poder rechazar
 * con 409 cualquier intento de cambiarlos: son inmutables tras la confirmación.
 */
public record EnlaceServicioDTO(
        @NotBlank(message = "El lugar o enlace es obligatorio")
        @Size(max = 300, message = "El lugar o enlace admite hasta 300 caracteres") String lugarOEnlace,
        BigDecimal precioFinalCOP, Duracion duracion, Modalidad modalidad) {
}
