package com.tutofast.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.tutofast.EscenarioNegociacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.modelo.EstadoPago;
import com.tutofast.modelo.EstadoServicio;
import com.tutofast.modelo.Pago;
import com.tutofast.modelo.RegistroAuditoria;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.ServicioTutoria;
import com.tutofast.modelo.Usuario;

/** Liberación, congelación y reembolso del pago simulado (R11–R12). */
class ServicioPagosLiquidacionTest {

    private EscenarioNegociacion e;
    private ServicioPagos pagos;
    private Usuario carlos;
    private Usuario laura;

    @BeforeEach
    void preparar() {
        e = new EscenarioNegociacion();
        pagos = e.servicioPagos;
        carlos = e.crearUsuario("Carlos Gómez", Rol.ESTUDIANTE);
        laura = e.crearUsuario("Laura Martínez", Rol.TUTOR);
    }

    private ServicioTutoria servicio(String precio, String comision) {
        ServicioTutoria s = e.crearServicio(carlos, laura, EstadoServicio.EN_DESARROLLO, Instant.now().minusSeconds(3600), precio);
        s.setComisionPct(new BigDecimal(comision));
        return s;
    }

    private Pago pagoDe(ServicioTutoria s) {
        return e.pagos.values().stream().filter(p -> p.getServicioId().equals(s.getId())).findFirst().orElseThrow();
    }

    private static void assertMonto(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperado " + esperado + " pero fue " + real);
    }

    private static void assertNegocio(String codigo, HttpStatus estado, Runnable accion) {
        ExcepcionNegocio ex = assertThrows(ExcepcionNegocio.class, accion::run);
        assertEquals(codigo, ex.getCodigo());
        assertEquals(estado, ex.getEstado());
    }

    @Test
    void liberaCuarentaMilConDiezPorCientoEnCuatroMilYTreintaYSeisMil() {
        ServicioTutoria s = servicio("40000", "10");
        Instant ahora = Instant.now();

        Pago pago = pagos.liberarPago(s, carlos.getId(), ahora);

        assertEquals(EstadoPago.LIBERADO, pago.getEstado());
        assertMonto("4000", pago.getComisionCOP());
        assertMonto("36000", pago.getMontoNetoCOP());
        assertMonto("40000", pago.getMontoRetenidoCOP());
        assertEquals(ahora, pago.getFechaLiquidacion());
        assertEquals(List.of(EstadoPago.RETENIDO, EstadoPago.LIBERADO),
                pago.getHistorial().stream().map(Pago.Movimiento::getEstado).toList());
    }

    @Test
    void redondeaLaComisionHalfUpAPesosEnteros() {
        ServicioTutoria s = servicio("33333", "12.5");

        Pago pago = pagos.liberarPago(s, null, Instant.now());

        assertMonto("4167", pago.getComisionCOP());
        assertMonto("29166", pago.getMontoNetoCOP());
        assertMonto("33333", pago.getComisionCOP().add(pago.getMontoNetoCOP()));
    }

    @Test
    void auditaLaLiberacionConElActorOElSistema() {
        ServicioTutoria a = servicio("40000", "10");
        ServicioTutoria b = servicio("40000", "10");

        pagos.liberarPago(a, carlos.getId(), Instant.now());
        pagos.liberarPago(b, null, Instant.now());

        List<RegistroAuditoria> registros = e.bitacora.stream().filter(r -> r.getAccion().equals("PAGO_LIBERADO")).toList();
        assertEquals(2, registros.size());
        assertEquals(carlos.getId(), registros.get(0).getUsuarioId());
        assertEquals(null, registros.get(1).getUsuarioId());
        assertEquals("4000", registros.get(0).getDetalle().get("comisionCOP"));
        assertEquals("36000", registros.get(0).getDetalle().get("montoNetoCOP"));
        assertTrue(registros.get(0).getEntidadAfectada().startsWith("pago:"));
    }

    @Test
    void unPagoLiberadoNoSeLiberaDeNuevo() {
        ServicioTutoria s = servicio("40000", "10");
        pagos.liberarPago(s, null, Instant.now());

        assertNegocio("TRANSICION_INVALIDA", HttpStatus.CONFLICT, () -> pagos.liberarPago(s, null, Instant.now()));
        assertEquals(2, pagoDe(s).getHistorial().size());
    }

    @Test
    void unPagoCongeladoSePuedeLiberarCuandoSeResuelveAFavorDelTutor() {
        ServicioTutoria s = servicio("40000", "10");
        pagos.congelarPago(s.getId(), null, Instant.now());

        Pago pago = pagos.liberarPago(s, null, Instant.now());

        assertEquals(EstadoPago.LIBERADO, pago.getEstado());
        assertEquals(3, pago.getHistorial().size());
    }

    @Test
    void congelaUnPagoRetenidoUnaSolaVez() {
        ServicioTutoria s = servicio("40000", "10");

        Pago pago = pagos.congelarPago(s.getId(), carlos.getId(), Instant.now());

        assertEquals(EstadoPago.CONGELADO, pago.getEstado());
        assertTrue(e.acciones().contains("PAGO_CONGELADO"));
        assertNegocio("TRANSICION_INVALIDA", HttpStatus.CONFLICT, () -> pagos.congelarPago(s.getId(), null, Instant.now()));
    }

    @Test
    void reembolsaElMontoCompletoSinComisionSoloDesdeCongelado() {
        ServicioTutoria s = servicio("40000", "10");
        assertNegocio("TRANSICION_INVALIDA", HttpStatus.CONFLICT, () -> pagos.reembolsarPago(s.getId(), null, Instant.now()));
        pagos.congelarPago(s.getId(), null, Instant.now());

        Pago pago = pagos.reembolsarPago(s.getId(), "admin-1", Instant.now());

        assertEquals(EstadoPago.REEMBOLSADO, pago.getEstado());
        assertMonto("0", pago.getComisionCOP());
        assertMonto("0", pago.getMontoNetoCOP());
        assertMonto("40000", pago.getMontoRetenidoCOP());
        assertNotNull(pago.getFechaLiquidacion());
        RegistroAuditoria registro = e.bitacora.stream().filter(r -> r.getAccion().equals("PAGO_REEMBOLSADO")).findFirst().orElseThrow();
        assertEquals("admin-1", registro.getUsuarioId());
        assertNegocio("TRANSICION_INVALIDA", HttpStatus.CONFLICT, () -> pagos.liberarPago(s, null, Instant.now()));
    }

    @Test
    void unServicioSinPagoDa409() {
        ServicioTutoria s = servicio("40000", "10");
        e.pagos.clear();

        assertNegocio("PAGO_NO_ENCONTRADO", HttpStatus.CONFLICT, () -> pagos.liberarPago(s, null, Instant.now()));
        assertNegocio("PAGO_NO_ENCONTRADO", HttpStatus.CONFLICT, () -> pagos.congelarPago(s.getId(), null, Instant.now()));
        assertNegocio("PAGO_NO_ENCONTRADO", HttpStatus.CONFLICT, () -> pagos.reembolsarPago(s.getId(), null, Instant.now()));
    }
}
