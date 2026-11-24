package com.tutofast.mapper;

import com.tutofast.dto.NotificacionDTO;
import com.tutofast.integracion.adaptadores.DocumentoNotificacion;

public final class NotificacionMapper {

    private NotificacionMapper() {
    }

    public static NotificacionDTO aDTO(DocumentoNotificacion n) {
        return new NotificacionDTO(n.getId(), n.getTipo(), n.getTitulo(), n.getMensaje(), n.getEnlace(),
                n.isLeida(), n.getFecha());
    }
}
