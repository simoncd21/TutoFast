package com.tutofast.repositorio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import com.tutofast.modelo.EstadoSolicitud;
import com.tutofast.modelo.Modalidad;

/**
 * Filtros de exploración de solicitudes (R07). Todos son opcionales; sin {@code estados} solo se devuelven las
 * solicitudes visibles para explorar (PUBLICADA, PROPUESTAS_RECIBIDAS y NEGOCIACION_EN_CURSO).
 */
public record FiltroSolicitudes(
        String universidadId,
        String facultadId,
        String programaId,
        String asignaturaId,
        String tema,
        Modalidad modalidad,
        Instant fechaDesde,
        Instant fechaHasta,
        BigDecimal precioMin,
        BigDecimal precioMax,
        Set<EstadoSolicitud> estados,
        Orden orden) {

    public enum Orden {
        RECIENTES, PRECIO_ASC, PRECIO_DESC, FECHA
    }

    /** Filtro vacío: todo lo explorable, lo más reciente primero. */
    public static FiltroSolicitudes vacio() {
        return new FiltroSolicitudes(null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
