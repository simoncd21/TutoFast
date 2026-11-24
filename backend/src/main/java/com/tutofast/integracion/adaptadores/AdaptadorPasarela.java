package com.tutofast.integracion.adaptadores;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.tutofast.integracion.puertos.IPasarelaPago;
import com.tutofast.integracion.puertos.ResultadoTransaccion;

/**
 * Simulador de pasarela: aprueba con una referencia {@code SIM-<uuid>} salvo el token de prueba
 * {@value #TOKEN_RECHAZO} (o un token vacío), que se rechaza. No guarda ni registra datos de tarjeta.
 */
@Component
public class AdaptadorPasarela implements IPasarelaPago {

    public static final String TOKEN_RECHAZO = "tok_rechazar";
    public static final String PREFIJO_REFERENCIA = "SIM-";

    @Override
    public ResultadoTransaccion procesarPago(String tokenPago, BigDecimal monto) {
        if (monto == null || monto.signum() <= 0) {
            return ResultadoTransaccion.rechazada("El monto a cobrar debe ser mayor que cero");
        }
        if (tokenPago == null || tokenPago.isBlank()) {
            return ResultadoTransaccion.rechazada("Falta el token de pago");
        }
        if (TOKEN_RECHAZO.equals(tokenPago.trim())) {
            return ResultadoTransaccion.rechazada("Pago rechazado por la pasarela simulada");
        }
        return ResultadoTransaccion.aprobada(PREFIJO_REFERENCIA + UUID.randomUUID());
    }
}
