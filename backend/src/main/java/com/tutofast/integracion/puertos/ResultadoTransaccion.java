package com.tutofast.integracion.puertos;

/**
 * Resultado de procesar un pago en la pasarela. {@code referencia} solo existe cuando fue aprobado.
 * Nunca contiene datos de tarjeta.
 */
public record ResultadoTransaccion(boolean aprobada, String referencia, String mensaje) {

    public static ResultadoTransaccion aprobada(String referencia) {
        return new ResultadoTransaccion(true, referencia, "Pago aprobado");
    }

    public static ResultadoTransaccion rechazada(String mensaje) {
        return new ResultadoTransaccion(false, null, mensaje);
    }
}
