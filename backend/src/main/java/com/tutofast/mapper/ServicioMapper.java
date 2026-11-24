package com.tutofast.mapper;

import com.tutofast.dto.ServicioDTO;
import com.tutofast.modelo.ServicioTutoria;

/** Conversiones de ServicioTutoria hacia DTO. */
public final class ServicioMapper {

    private ServicioMapper() {
    }

    public static ServicioDTO aDTO(ServicioTutoria s) {
        return new ServicioDTO(s.getId(), s.getSolicitudId(), s.getPropuestaId(), s.getEstudianteId(), s.getTutorId(),
                s.getPrecioFinalCOP(), s.getDuracion(), s.getModalidad(), s.getLugarOEnlace(), s.getFechaHora(),
                s.getComisionPct(), s.getFechaConfirmacion(), s.getFechaInicio(), s.getFechaFinalizacion(),
                s.getEstado());
    }
}
