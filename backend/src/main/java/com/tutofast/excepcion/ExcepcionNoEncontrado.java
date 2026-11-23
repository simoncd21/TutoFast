package com.tutofast.excepcion;

/** El recurso solicitado no existe (404). */
public class ExcepcionNoEncontrado extends RuntimeException {

    public ExcepcionNoEncontrado(String mensaje) {
        super(mensaje);
    }
}
