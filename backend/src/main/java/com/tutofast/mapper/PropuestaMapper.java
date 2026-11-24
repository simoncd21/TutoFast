package com.tutofast.mapper;

import com.tutofast.dto.PropuestaVistaDTO;
import com.tutofast.modelo.NivelVerificacion;
import com.tutofast.modelo.PerfilTutor;
import com.tutofast.modelo.Propuesta;
import com.tutofast.modelo.SolicitudTutoria;
import com.tutofast.modelo.Usuario;

/** Conversiones de Propuesta hacia DTO. */
public final class PropuestaMapper {

    private PropuestaMapper() {
    }

    /** {@code solicitud} puede ser {@code null}; {@code tutor} puede ser {@code null} si el usuario ya no existe. */
    public static PropuestaVistaDTO aVistaDTO(Propuesta p, Usuario tutor, SolicitudTutoria solicitud) {
        PerfilTutor perfil = tutor == null ? null : tutor.getPerfilTutor();
        return new PropuestaVistaDTO(p.getId(), p.getSolicitudId(),
                solicitud == null ? null : solicitud.getTema(), solicitud == null ? null : solicitud.getEstado(),
                p.getTutorId(), tutor == null ? "Tutor" : tutor.getNombre(),
                perfil != null && perfil.getNivelVerificacion() == NivelVerificacion.VERIFICADO,
                perfil == null ? null : perfil.getPromedioCalificacion(),
                perfil == null ? 0 : perfil.getServiciosCompletados(), p.getTipo(), p.getPrecioCOP(),
                p.getDuracion(), p.getModalidad(), p.getNota(), p.getContraofertaCOP(), p.getEstado(),
                p.getFechaCreacion(), p.getFechaActualizacion());
    }
}
