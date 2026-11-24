package com.tutofast.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.tutofast.excepcion.ExcepcionNegocio;

class PagoTest {

    private static final Instant AHORA = Instant.parse("2026-12-05T21:00:00Z");

    private static Pago pagoRetenido(String monto) {
        return Pago.retener("srv1", new BigDecimal(monto), "SIM-123", AHORA);
    }

    @Test
    void liquidaCuarentaMilAlDiezPorCiento() {
        Pago pago = pagoRetenido("40000");

        Pago.Liquidacion liquidacion = pago.calcularLiquidacion(new BigDecimal("10.00"));

        assertThat(liquidacion.comisionCOP()).isEqualByComparingTo("4000");
        assertThat(liquidacion.montoNetoCOP()).isEqualByComparingTo("36000");
        assertThat(pago.getComisionCOP()).isEqualByComparingTo("4000");
        assertThat(pago.getMontoNetoCOP()).isEqualByComparingTo("36000");
    }

    @Test
    void redondeaHalfUpConDoceComaCincoPorCiento() {
        // 4.004 x 12,5 % = 500,5 -> HALF_UP da 501 (HALF_EVEN daría 500)
        Pago pago = pagoRetenido("4004");

        Pago.Liquidacion liquidacion = pago.calcularLiquidacion(new BigDecimal("12.5"));

        assertThat(liquidacion.comisionCOP()).isEqualByComparingTo("501");
        assertThat(liquidacion.montoNetoCOP()).isEqualByComparingTo("3503");
        assertThat(liquidacion.comisionCOP().add(liquidacion.montoNetoCOP())).isEqualByComparingTo("4004");
    }

    @Test
    void doceComaCincoSobreCuarentaMilEsExacto() {
        Pago.Liquidacion liquidacion = pagoRetenido("40000").calcularLiquidacion(new BigDecimal("12.5"));

        assertThat(liquidacion.comisionCOP()).isEqualByComparingTo("5000");
        assertThat(liquidacion.montoNetoCOP()).isEqualByComparingTo("35000");
    }

    @Test
    void comisionCeroDejaTodoAlTutor() {
        Pago.Liquidacion liquidacion = pagoRetenido("25000").calcularLiquidacion(BigDecimal.ZERO);

        assertThat(liquidacion.comisionCOP()).isEqualByComparingTo("0");
        assertThat(liquidacion.montoNetoCOP()).isEqualByComparingTo("25000");
    }

    @Test
    void rechazaPorcentajesFueraDeRango() {
        Pago pago = pagoRetenido("40000");

        assertThatThrownBy(() -> pago.calcularLiquidacion(new BigDecimal("50.01"))).isInstanceOf(ExcepcionNegocio.class);
        assertThatThrownBy(() -> pago.calcularLiquidacion(new BigDecimal("-1"))).isInstanceOf(ExcepcionNegocio.class);
        assertThatThrownBy(() -> pago.calcularLiquidacion(null)).isInstanceOf(ExcepcionNegocio.class);
    }

    @Test
    void liberarLiquidaYQuedaLiberado() {
        Pago pago = pagoRetenido("40000");

        pago.liberar(new BigDecimal("10.00"), AHORA);

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.LIBERADO);
        assertThat(pago.getMontoNetoCOP()).isEqualByComparingTo("36000");
        assertThat(pago.getFechaLiquidacion()).isEqualTo(AHORA);
        assertThat(pago.getHistorial()).extracting(Pago.Movimiento::getEstado)
                .containsExactly(EstadoPago.RETENIDO, EstadoPago.LIBERADO);
    }

    @Test
    void congeladoPuedeLiberarseOReembolsarse() {
        Pago aLiberar = pagoRetenido("40000");
        aLiberar.congelar(AHORA);
        aLiberar.liberar(new BigDecimal("10"), AHORA);
        assertThat(aLiberar.getEstado()).isEqualTo(EstadoPago.LIBERADO);

        Pago aReembolsar = pagoRetenido("40000");
        aReembolsar.congelar(AHORA);
        aReembolsar.reembolsar(AHORA);
        assertThat(aReembolsar.getEstado()).isEqualTo(EstadoPago.REEMBOLSADO);
        assertThat(aReembolsar.getComisionCOP()).isEqualByComparingTo("0");
    }

    @Test
    void transicionesInvalidasDevuelven409() {
        Pago liberado = pagoRetenido("40000");
        liberado.liberar(new BigDecimal("10"), AHORA);

        assertThatThrownBy(() -> liberado.liberar(new BigDecimal("10"), AHORA))
                .isInstanceOfSatisfying(ExcepcionNegocio.class, e -> assertThat(e.getEstado()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> liberado.congelar(AHORA)).isInstanceOf(ExcepcionNegocio.class);
        assertThatThrownBy(() -> pagoRetenido("40000").reembolsar(AHORA)).isInstanceOf(ExcepcionNegocio.class);
        assertThat(liberado.getHistorial()).hasSize(2);
    }

    @Test
    void noRetieneMontosNoPositivos() {
        assertThatThrownBy(() -> Pago.retener("srv1", BigDecimal.ZERO, "SIM-1", AHORA)).isInstanceOf(ExcepcionNegocio.class);
    }
}
