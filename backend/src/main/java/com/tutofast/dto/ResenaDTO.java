package com.tutofast.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import com.tutofast.modelo.TipoCalificacion;

/** Reseña pública (solo calificaciones visibles). */
public record ResenaDTO(TipoCalificacion tipo, String autorNombre, String comentario, BigDecimal promedio,
                        Map<String, Integer> criterios, Instant fechaEmision) {
}
