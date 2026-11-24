package com.tutofast.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.NivelVerificacion;
import com.tutofast.modelo.TipoSoporte;

/** Perfil público del tutor. Nunca expone la cuenta bancaria, el documento ni el correo. */
public record PerfilPublicoTutorDTO(String id, String nombre, Declarado declarado, Verificado verificado,
                                    BigDecimal promedioCalificacion, int serviciosCompletados,
                                    List<ResenaDTO> resenas) {

    public record Declarado(String presentacion, List<String> areasDeInteres, String formacion, String experiencia,
                            List<Modalidad> modalidades, String disponibilidad, List<String> asignaturas) {
    }

    public record Verificado(NivelVerificacion nivelVerificacion, String etiqueta,
                             List<SoporteAprobado> soportesAprobados) {
    }

    public record SoporteAprobado(TipoSoporte tipo, Instant fechaRevision) {
    }
}
