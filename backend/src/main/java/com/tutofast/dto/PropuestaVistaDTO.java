package com.tutofast.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.tutofast.modelo.Duracion;
import com.tutofast.modelo.EstadoPropuesta;
import com.tutofast.modelo.EstadoSolicitud;
import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.TipoPropuesta;

/**
 * Propuesta tal como la ven estudiante y tutor. {@code solicitudTema} y {@code solicitudEstado} se rellenan en
 * {@code GET /api/propuestas/mias}; en el resto de rutas pueden venir {@code null}.
 */
public record PropuestaVistaDTO(String id, String solicitudId, String solicitudTema, EstadoSolicitud solicitudEstado,
                                String tutorId, String tutorNombre, boolean tutorVerificado,
                                BigDecimal tutorPromedioCalificacion, int tutorServiciosCompletados,
                                TipoPropuesta tipo, BigDecimal precioCOP, Duracion duracion, Modalidad modalidad,
                                String nota, BigDecimal contraofertaCOP, EstadoPropuesta estado,
                                Instant fechaCreacion, Instant fechaActualizacion) {
}
