package com.tutofast.excepcion;

import org.springframework.http.HttpStatus;

/** Regla de negocio incumplida. Lleva un código estable y el estado HTTP asociado. */
public class ExcepcionNegocio extends RuntimeException {

    private final String codigo;
    private final HttpStatus estado;

    public ExcepcionNegocio(String codigo, String mensaje, HttpStatus estado) {
        super(mensaje);
        this.codigo = codigo;
        this.estado = estado;
    }

    /** Regla de dominio genérica: 422 REGLA_NEGOCIO. */
    public ExcepcionNegocio(String mensaje) {
        this("REGLA_NEGOCIO", mensaje, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /** Transición de estado no permitida: 409 TRANSICION_INVALIDA. */
    public static ExcepcionNegocio transicionInvalida(String entidad, Object actual, Object destino) {
        return new ExcepcionNegocio("TRANSICION_INVALIDA",
                "Transición no permitida en " + entidad + ": " + actual + " → " + destino, HttpStatus.CONFLICT);
    }

    public String getCodigo() {
        return codigo;
    }

    public HttpStatus getEstado() {
        return estado;
    }
}
