package com.tutofast.dto;

import java.time.Instant;
import java.util.List;

import com.tutofast.modelo.CategoriaReporte;
import com.tutofast.modelo.EstadoReporte;
import com.tutofast.modelo.TipoReporte;

/**
 * Reporte con las partes por nombre. En «recibidos» (vista del denunciado) {@code reportante} es nulo y
 * {@code evidencias} va vacía.
 */
public record ReporteDTO(String id, TipoReporte tipo, CategoriaReporte categoria, String motivo, EstadoReporte estado,
                         ParticipanteDTO reportante, ParticipanteDTO denunciado, String servicioId,
                         List<String> evidencias, Instant fechaRadicacion, ResolucionVistaDTO resolucion) {
}
