package com.tutofast.mapper;

import com.tutofast.dto.ParticipanteDTO;
import com.tutofast.dto.ServicioDTO;
import com.tutofast.modelo.Pago;
import com.tutofast.modelo.ServicioTutoria;
import com.tutofast.modelo.SolicitudTutoria;
import com.tutofast.modelo.Usuario;

/** Conversiones de ServicioTutoria hacia DTO. */
public final class ServicioMapper {

    private ServicioMapper() {
    }

    /** Cualquier argumento distinto de {@code s} puede ser nulo (dato no disponible). */
    public static ServicioDTO aDTO(ServicioTutoria s, SolicitudTutoria solicitud, Usuario estudiante, Usuario tutor,
                                   Pago pago) {
        return new ServicioDTO(s.getId(), s.getSolicitudId(), s.getPropuestaId(),
                solicitud == null ? null : solicitud.getTema(),
                new ParticipanteDTO(s.getEstudianteId(), estudiante == null ? null : estudiante.getNombre()),
                new ParticipanteDTO(s.getTutorId(), tutor == null ? null : tutor.getNombre()),
                s.getPrecioFinalCOP(), s.getDuracion(), s.getModalidad(), s.getLugarOEnlace(), s.getFechaHora(),
                s.getComisionPct(), s.getFechaConfirmacion(), s.getFechaInicio(), s.getFechaFinalizacion(),
                s.getEstado(), PagoMapper.aResumenDTO(pago));
    }
}
