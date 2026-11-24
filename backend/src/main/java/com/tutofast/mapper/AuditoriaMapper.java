package com.tutofast.mapper;

import java.util.Map;

import com.tutofast.dto.RegistroAuditoriaDTO;
import com.tutofast.modelo.RegistroAuditoria;

/** Conversión de registros de auditoría hacia DTO. */
public final class AuditoriaMapper {

    private AuditoriaMapper() {
    }

    public static RegistroAuditoriaDTO aDTO(RegistroAuditoria r, Map<String, String> nombresPorUsuario) {
        String nombre = r.getUsuarioId() == null ? "Sistema" : nombresPorUsuario.get(r.getUsuarioId());
        return new RegistroAuditoriaDTO(r.getId(), r.getUsuarioId(), nombre, r.getAccion(),
                r.getEntidadAfectada(), r.getFecha(), r.getDetalle());
    }
}
