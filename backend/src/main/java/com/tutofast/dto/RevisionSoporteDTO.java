package com.tutofast.dto;

import jakarta.validation.constraints.Size;

/** Cuerpo opcional de {@code .../aprobar}. */
public record RevisionSoporteDTO(@Size(max = 500, message = "La observación no puede superar 500 caracteres")
                                 String observacion) {
}
