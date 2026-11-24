package com.tutofast.integracion.puertos;

import java.math.BigDecimal;

/** Puerto de la pasarela de pagos. La implementación actual es un simulador interno. */
public interface IPasarelaPago {

    /**
     * Procesa un cobro. {@code tokenPago} es un token opaco entregado por el cliente: la plataforma nunca recibe
     * ni almacena datos de tarjeta.
     */
    ResultadoTransaccion procesarPago(String tokenPago, BigDecimal monto);
}
