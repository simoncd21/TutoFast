package com.tutofast.dto;

import java.time.Instant;
import java.util.List;

/** Formato único de error de la API. */
public record ErrorDTO(String codigo, String mensaje, List<Detalle> detalles, Instant fecha) {

    public record Detalle(String campo, String mensaje) {
    }

    public static ErrorDTO de(String codigo, String mensaje) {
        return new ErrorDTO(codigo, mensaje, List.of(), Instant.now());
    }
}
