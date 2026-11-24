package com.tutofast.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import com.tutofast.modelo.TipoCalificacion;

public record CalificacionDTO(String id, TipoCalificacion tipo, String servicioId, String autorId,
                              String destinatarioId, String comentario, BigDecimal promedio,
                              Map<String, Integer> criterios, Instant fechaEmision, Instant fechaLimite,
                              boolean visible) {
}
