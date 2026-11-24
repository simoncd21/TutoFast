package com.tutofast.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tutofast.integracion.adaptadores.AdaptadorPasarela;
import com.tutofast.integracion.puertos.ResultadoTransaccion;

class AdaptadorPasarelaTest {

    private final AdaptadorPasarela pasarela = new AdaptadorPasarela();
    private static final BigDecimal MONTO = new BigDecimal("40000");

    @Test
    void apruebaConReferenciaSimUuid() {
        ResultadoTransaccion r = pasarela.procesarPago("tok_visa_4242", MONTO);

        assertTrue(r.aprobada());
        assertTrue(r.referencia().startsWith("SIM-"));
        UUID.fromString(r.referencia().substring("SIM-".length())); // lanza si no es un UUID válido
    }

    @Test
    void cadaPagoAprobadoTieneReferenciaDistinta() {
        assertNotEquals(pasarela.procesarPago("tok_a", MONTO).referencia(), pasarela.procesarPago("tok_a", MONTO).referencia());
    }

    @Test
    void rechazaElTokenDePruebaTokRechazar() {
        ResultadoTransaccion r = pasarela.procesarPago("tok_rechazar", MONTO);

        assertFalse(r.aprobada());
        assertNull(r.referencia());
        assertTrue(r.mensaje().toLowerCase().contains("rechazado"));
    }

    @Test
    void rechazaTokenVacioOMontoNoPositivo() {
        assertFalse(pasarela.procesarPago(" ", MONTO).aprobada());
        assertFalse(pasarela.procesarPago(null, MONTO).aprobada());
        assertFalse(pasarela.procesarPago("tok_a", BigDecimal.ZERO).aprobada());
        assertFalse(pasarela.procesarPago("tok_a", null).aprobada());
        assertEquals("Pago aprobado", pasarela.procesarPago("tok_a", MONTO).mensaje());
    }
}
