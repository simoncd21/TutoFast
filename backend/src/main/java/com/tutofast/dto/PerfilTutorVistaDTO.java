package com.tutofast.dto;

import java.util.List;

import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.NivelVerificacion;
import com.tutofast.modelo.TipoTutor;

/** Perfil del tutor tal como lo ve su propietario: declarado + estado de verificación. */
public record PerfilTutorVistaDTO(
        String presentacion,
        List<String> areasDeInteres,
        String formacion,
        TipoTutor tipoTutor,
        String experiencia,
        List<Modalidad> modalidades,
        String disponibilidad,
        List<String> asignaturasDominadasIds,
        String cuentaBancariaCobro,
        NivelVerificacion nivelVerificacion,
        String etiquetaVerificacion,
        int serviciosCompletados) {
}
