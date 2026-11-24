package com.tutofast.dto;

import com.tutofast.modelo.TipoReporte;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo ({@code datos}) de {@code POST /api/reportes}. Con {@code servicioId} el denunciado es la otra parte del
 * servicio (puede omitirse); sin servicio, {@code denunciadoId} es obligatorio.
 */
public record ReporteEntradaDTO(@NotNull(message = "El tipo de reporte es obligatorio") TipoReporte tipo,
                                @NotBlank(message = "El motivo del reporte es obligatorio")
                                @Size(min = 10, max = 1000, message = "El motivo debe tener entre 10 y 1000 caracteres") String motivo,
                                String denunciadoId, String servicioId) {
}
