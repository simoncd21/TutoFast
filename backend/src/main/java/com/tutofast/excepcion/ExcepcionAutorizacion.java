package com.tutofast.excepcion;

/** Rol insuficiente, recurso ajeno o cuenta suspendida (403). */
public class ExcepcionAutorizacion extends RuntimeException {

    public ExcepcionAutorizacion(String mensaje) {
        super(mensaje);
    }
}
