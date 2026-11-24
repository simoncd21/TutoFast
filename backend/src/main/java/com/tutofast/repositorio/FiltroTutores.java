package com.tutofast.repositorio;

import java.math.BigDecimal;
import java.util.Collection;

import com.tutofast.modelo.Modalidad;

/**
 * Filtros de la búsqueda de tutores. {@code asignaturaIds} reúne las asignaturas que debe dominar el tutor (el
 * servicio expande {@code programaId} y {@code asignaturaId} a esta lista); {@code null} significa sin filtro.
 */
public record FiltroTutores(Collection<String> asignaturaIds, Modalidad modalidad, String disponibilidad,
                            BigDecimal calificacionMin, boolean soloVerificados, Orden orden) {

    public enum Orden {
        CALIFICACION, SERVICIOS;

        public static Orden deTexto(String texto) {
            if (texto == null || texto.isBlank()) {
                return CALIFICACION;
            }
            return switch (texto.strip().toLowerCase()) {
                case "calificacion" -> CALIFICACION;
                case "servicios" -> SERVICIOS;
                default -> throw new com.tutofast.excepcion.ExcepcionNegocio("VALIDACION",
                        "El orden debe ser 'calificacion' o 'servicios'", org.springframework.http.HttpStatus.BAD_REQUEST);
            };
        }
    }
}
