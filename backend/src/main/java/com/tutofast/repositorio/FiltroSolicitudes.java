package com.tutofast.repositorio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.tutofast.excepcion.ExcepcionNegocio;
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
        RECIENTES, PRECIO_ASC, PRECIO_DESC, FECHA;

        /** Acepta los valores de la API: recientes, precioAsc, precioDesc y fecha. */
        public static Orden deTexto(String texto) {
            if (texto == null || texto.isBlank()) {
                return RECIENTES;
            }
            return switch (texto.strip().toLowerCase(Locale.ROOT)) {
                case "recientes" -> RECIENTES;
                case "precioasc" -> PRECIO_ASC;
                case "preciodesc" -> PRECIO_DESC;
                case "fecha" -> FECHA;
                default -> throw new ExcepcionNegocio("VALIDACION",
                        "Orden no válido: use recientes, precioAsc, precioDesc o fecha", HttpStatus.BAD_REQUEST);
            };
        }
    }

    /** Filtro vacío: todo lo explorable, lo más reciente primero. */
    public static FiltroSolicitudes vacio() {
        return new FiltroSolicitudes(null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
