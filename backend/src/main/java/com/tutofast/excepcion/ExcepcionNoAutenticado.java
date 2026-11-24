package com.tutofast.excepcion;

/** Token ausente, inválido o expirado, o credenciales incorrectas (401). */
public class ExcepcionNoAutenticado extends RuntimeException {

    public ExcepcionNoAutenticado(String mensaje) {
        super(mensaje);
    }
}
