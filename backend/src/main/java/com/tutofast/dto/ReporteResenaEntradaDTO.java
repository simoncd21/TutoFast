package com.tutofast.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/calificaciones/{id}/reportar}. */
public record ReporteResenaEntradaDTO(@NotBlank(message = "El motivo del reporte es obligatorio")
                                      @Size(max = 500, message = "El motivo admite hasta 500 caracteres") String motivo) {
}
