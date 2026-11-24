package com.tutofast.modelo;

import com.tutofast.excepcion.ExcepcionNoEncontrado;

/** Niveles del catálogo académico; {@code segmento} es el valor usado en la ruta {@code /api/catalogos/{tipo}}. */
public enum TipoCatalogo {
    UNIVERSIDADES("universidades"),
    FACULTADES("facultades"),
    PROGRAMAS("programas"),
    ASIGNATURAS("asignaturas");

    private final String segmento;

    TipoCatalogo(String segmento) {
        this.segmento = segmento;
    }

    public String segmento() {
        return segmento;
    }

    public static TipoCatalogo deSegmento(String valor) {
        for (TipoCatalogo tipo : values()) {
            if (tipo.segmento.equalsIgnoreCase(valor)) {
                return tipo;
            }
        }
        throw new ExcepcionNoEncontrado("Tipo de catálogo no encontrado: " + valor);
    }
}
