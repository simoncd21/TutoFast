package com.tutofast.mapper;

import com.tutofast.dto.PropuestaVistaDTO;
import com.tutofast.modelo.Propuesta;

/** Conversiones de Propuesta hacia DTO. */
public final class PropuestaMapper {

    private PropuestaMapper() {
    }

    public static PropuestaVistaDTO aVistaDTO(Propuesta p, String tutorNombre) {
        return new PropuestaVistaDTO(p.getId(), p.getSolicitudId(), p.getTutorId(), tutorNombre, p.getTipo(),
                p.getPrecioCOP(), p.getDuracion(), p.getModalidad(), p.getNota(), p.getContraofertaCOP(),
                p.getEstado(), p.getFechaCreacion(), p.getFechaActualizacion());
    }
}
