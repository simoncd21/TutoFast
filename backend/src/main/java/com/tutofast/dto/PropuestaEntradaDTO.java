package com.tutofast.dto;

import java.math.BigDecimal;

import com.tutofast.modelo.Duracion;
import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.TipoPropuesta;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/solicitudes/{id}/propuestas} y de {@code PUT /api/propuestas/{id}}. */
public record PropuestaEntradaDTO(@NotNull(message = "El tipo de propuesta es obligatorio") TipoPropuesta tipo,
                                  BigDecimal precioCOP, Duracion duracion, Modalidad modalidad,
                                  @Size(max = 500, message = "La nota admite hasta 500 caracteres") String nota) {
}
