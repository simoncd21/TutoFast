package com.tutofast.mapper;

import com.tutofast.dto.ArchivoDTO;
import com.tutofast.dto.MensajeDTO;
import com.tutofast.dto.ModeracionVistaDTO;
import com.tutofast.modelo.Archivo;
import com.tutofast.modelo.Mensaje;
import com.tutofast.modelo.Moderacion;

/** Conversiones de Mensaje y de su moderación hacia DTO. */
public final class MensajeMapper {

    /** Texto que ven los participantes cuando un administrador retiró el mensaje. */
    public static final String TEXTO_RETIRADO = "[Mensaje retirado por la moderación de TutoFast]";
    public static final String NOMBRE_SISTEMA = "TutoFast";

    private MensajeMapper() {
    }

    /**
     * {@code verOriginal} es verdadero solo para el administrador: un mensaje retirado por infracción se muestra a
     * los participantes sin contenido ni adjunto, pero el original se conserva para el expediente.
     */
    public static MensajeDTO aDTO(Mensaje m, String emisorNombre, boolean verOriginal) {
        boolean ocultar = m.estaRetirado() && !verOriginal;
        return new MensajeDTO(m.getId(), m.getEmisorId(), m.esDeSistema() ? NOMBRE_SISTEMA : emisorNombre,
                ocultar ? TEXTO_RETIRADO : m.getContenido(), m.getMarcaTemporal(), m.isReportado(), m.esDeSistema(),
                m.estaRetirado(), ocultar ? null : aArchivoDTO(m.getArchivo()));
    }

    public static ArchivoDTO aArchivoDTO(Archivo a) {
        return a == null ? null : new ArchivoDTO(a.getGridFsId(), a.getNombre(), a.getTipo(), a.getTamanoMB());
    }

    public static ModeracionVistaDTO aModeracionDTO(Moderacion m) {
        return m == null ? null
                : new ModeracionVistaDTO(m.getDecision(), m.getObservacion(), m.getModeradoPor(), m.getFecha(),
                        m.getSancionId());
    }
}
