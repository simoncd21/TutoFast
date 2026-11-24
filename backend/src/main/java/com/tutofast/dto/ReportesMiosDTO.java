package com.tutofast.dto;

import java.util.List;

/** Reportes que el usuario radicó y los que recibió como denunciado. */
public record ReportesMiosDTO(List<ReporteDTO> radicados, List<ReporteDTO> recibidos) {
}
