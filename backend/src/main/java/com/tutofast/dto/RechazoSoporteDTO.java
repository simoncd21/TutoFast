package com.tutofast.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code .../rechazar}: la observación es obligatoria. */
public record RechazoSoporteDTO(
        @NotBlank(message = "La observación es obligatoria al rechazar un soporte")
        @Size(max = 500, message = "La observación no puede superar 500 caracteres")
        String observacion) {
}
