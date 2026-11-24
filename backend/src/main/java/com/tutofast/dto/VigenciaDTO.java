package com.tutofast.dto;

import java.time.Instant;

/** Periodo de una sanción; {@code fin} nulo = indefinida (bloqueo permanente). */
public record VigenciaDTO(Instant inicio, Instant fin) {
}
