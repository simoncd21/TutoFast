package com.tutofast.dto;

import java.util.List;

import com.tutofast.modelo.EstadoSancion;
import com.tutofast.modelo.TipoReporte;
import com.tutofast.modelo.TipoSancion;

/**
 * Sanción con sus apelaciones. {@code motivo} y {@code tipoReporte} vienen del reporte que la respalda (nunca la
 * identidad del reportante); {@code puedeApelar} es verdadero solo si está VIGENTE.
 */
public record SancionDTO(String id, String reporteId, ParticipanteDTO usuario, TipoSancion tipo, VigenciaDTO vigencia,
                         EstadoSancion estado, TipoReporte tipoReporte, String motivo, boolean puedeApelar,
                         List<ApelacionDTO> apelaciones) {
}
