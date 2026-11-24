package com.tutofast.dto;

import java.time.Instant;

/**
 * Calificaciones de un servicio para uno de sus participantes: la propia, la de la contraparte (solo cuando ya es
 * visible), el plazo para calificar y si todavía puede hacerlo.
 */
public record CalificacionesServicioDTO(CalificacionDTO mia, CalificacionDTO contraparte, Instant fechaLimite,
                                        boolean puedeCalificar) {
}
