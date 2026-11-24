package com.tutofast.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.tutofast.excepcion.ExcepcionNegocio;

/** Reglas del dominio para reportar y moderar mensajes y reseñas. */
class ModeracionModeloTest {

    private static final Instant AHORA = Instant.parse("2026-12-01T10:00:00Z");

    private static Mensaje mensaje() {
        return Mensaje.nuevo("sol1", "tutor1", "tutor1", "Hola", null, AHORA);
    }

    private static Moderacion decision(DecisionModeracion d) {
        return new Moderacion(d, "obs", "admin1", AHORA);
    }

    // ---------------------------------------------------------------- Mensaje

    @Test
    void reportarGuardaQuienCuandoMotivoYDetalleSinTocarElContenido() {
        Mensaje m = mensaje();

        m.reportar("ACOSO", "  me insultó  ", "est1", AHORA);

        assertTrue(m.isReportado());
        assertEquals("ACOSO", m.getMotivoReporte());
        assertEquals("me insultó", m.getDetalleReporte());
        assertEquals("est1", m.getReportadoPorId());
        assertEquals(AHORA, m.getFechaReporte());
        assertEquals("Hola", m.getContenido());
        assertEquals("tutor1", m.getEmisorId());
    }

    @Test
    void unDetalleEnBlancoSeGuardaComoNulo() {
        Mensaje m = mensaje();
        m.reportar("OTRO", "   ", "est1", AHORA);
        assertNull(m.getDetalleReporte());
    }

    @Test
    void unMensajeNoSeReportaDosVecesNiSinMotivo() {
        Mensaje m = mensaje();
        ExcepcionNegocio sinMotivo = assertThrows(ExcepcionNegocio.class, () -> m.reportar(" ", null, "est1", AHORA));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, sinMotivo.getEstado());
        assertFalse(m.isReportado());

        m.reportar("SPAM", null, "est1", AHORA);
        ExcepcionNegocio doble = assertThrows(ExcepcionNegocio.class, () -> m.reportar("SPAM", null, "est1", AHORA));
        assertEquals("MENSAJE_YA_REPORTADO", doble.getCodigo());
        assertEquals(HttpStatus.CONFLICT, doble.getEstado());
    }

    @Test
    void soloSeModeraUnMensajeReportadoYUnaSolaVez() {
        Mensaje m = mensaje();
        assertEquals("MENSAJE_NO_REPORTADO", assertThrows(ExcepcionNegocio.class, m::exigirModerable).getCodigo());
        assertThrows(ExcepcionNegocio.class, () -> m.moderar(decision(DecisionModeracion.DESESTIMAR)));
        assertNull(m.getModeracion());

        m.reportar("OTRO", null, "est1", AHORA);
        m.exigirModerable(); // ahora sí
        m.moderar(decision(DecisionModeracion.DESESTIMAR));

        assertNotNull(m.getModeracion());
        assertEquals("MENSAJE_YA_MODERADO", assertThrows(ExcepcionNegocio.class, m::exigirModerable).getCodigo());
        assertEquals("MENSAJE_YA_MODERADO",
                assertThrows(ExcepcionNegocio.class, () -> m.moderar(decision(DecisionModeracion.CONFIRMAR_INFRACCION))).getCodigo());
    }

    @Test
    void soloConfirmarLaInfraccionRetiraElMensaje() {
        Mensaje desestimado = mensaje();
        desestimado.reportar("OTRO", null, "est1", AHORA);
        desestimado.moderar(decision(DecisionModeracion.DESESTIMAR));
        assertFalse(desestimado.estaRetirado());

        Mensaje confirmado = mensaje();
        confirmado.reportar("OTRO", null, "est1", AHORA);
        confirmado.moderar(decision(DecisionModeracion.CONFIRMAR_INFRACCION));
        assertTrue(confirmado.estaRetirado());
        assertEquals("Hola", confirmado.getContenido(), "el original se conserva");
        assertFalse(mensaje().estaRetirado());
    }

    @Test
    void elAvisoDelSistemaNaceLeidoYSinEmisor() {
        Mensaje aviso = Mensaje.deSistema("sol1", "tutor1", "Hilo cerrado", AHORA);

        assertTrue(aviso.esDeSistema());
        assertTrue(aviso.isLeido());
        assertNull(aviso.getEmisorId());
        assertFalse(mensaje().esDeSistema());
        assertFalse(mensaje().isLeido());
    }

    // ---------------------------------------------------------------- Calificacion

    private static CalificacionAlEstudiante resena(boolean visible) {
        CalificacionAlEstudiante c = new CalificacionAlEstudiante();
        c.setPuntualidad(5);
        c.setComunicacion(5);
        c.setCumplimiento(5);
        c.setVisible(visible);
        return c;
    }

    @Test
    void soloSeReportaUnaReseñaPublicadaUnaSolaVezYConMotivo() {
        CalificacionAlEstudiante oculta = resena(false);
        assertEquals("RESENA_NO_PUBLICADA",
                assertThrows(ExcepcionNegocio.class, () -> oculta.reportar("Injusta", "tutor1", AHORA)).getCodigo());

        CalificacionAlEstudiante publicada = resena(true);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,
                assertThrows(ExcepcionNegocio.class, () -> publicada.reportar("  ", "tutor1", AHORA)).getEstado());
        publicada.reportar("  Injusta  ", "tutor1", AHORA);

        assertTrue(publicada.isReportada());
        assertEquals("Injusta", publicada.getMotivoReporte());
        assertEquals("tutor1", publicada.getReportadaPorId());
        assertEquals(AHORA, publicada.getFechaReporte());
        assertEquals("RESENA_YA_REPORTADA",
                assertThrows(ExcepcionNegocio.class, () -> publicada.reportar("otra", "tutor1", AHORA)).getCodigo());
    }

    @Test
    void confirmarLaInfraccionOcultaLaReseñaParaSiempre() {
        CalificacionAlEstudiante c = resena(true);
        c.reportar("Injusta", "tutor1", AHORA);

        c.moderar(decision(DecisionModeracion.CONFIRMAR_INFRACCION));

        assertFalse(c.isVisible());
        assertTrue(c.isOcultaPorModeracion());
        c.hacerVisible();
        assertFalse(c.isVisible(), "hacerVisible no revive una reseña moderada");
    }

    @Test
    void desestimarDejaLaReseñaVisible() {
        CalificacionAlEstudiante c = resena(true);
        c.reportar("Injusta", "tutor1", AHORA);

        c.moderar(decision(DecisionModeracion.DESESTIMAR));

        assertTrue(c.isVisible());
        assertFalse(c.isOcultaPorModeracion());
        assertEquals(DecisionModeracion.DESESTIMAR, c.getModeracion().getDecision());
    }

    @Test
    void unaReseñaSeModeraUnaSolaVezYSoloSiFueReportada() {
        CalificacionAlEstudiante c = resena(true);
        assertEquals("RESENA_NO_REPORTADA", assertThrows(ExcepcionNegocio.class, c::exigirModerable).getCodigo());

        c.reportar("Injusta", "tutor1", AHORA);
        c.moderar(decision(DecisionModeracion.DESESTIMAR));

        assertEquals("RESENA_YA_MODERADA", assertThrows(ExcepcionNegocio.class, c::exigirModerable).getCodigo());
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ExcepcionNegocio.class, () -> c.moderar(decision(DecisionModeracion.CONFIRMAR_INFRACCION))).getEstado());
    }

    @Test
    void hacerVisibleFuncionaConNormalidadSiNoFueModerada() {
        CalificacionAlEstudiante c = resena(false);
        c.hacerVisible();
        assertTrue(c.isVisible());
    }

    @Test
    void moderacionIndicaSiConfirmaLaInfraccion() {
        assertTrue(decision(DecisionModeracion.CONFIRMAR_INFRACCION).confirmaInfraccion());
        assertFalse(decision(DecisionModeracion.DESESTIMAR).confirmaInfraccion());
    }

    @Test
    void cadaMotivoDeMensajeSeMapeaAUnTipoDeReporte() {
        for (MotivoReporteMensaje motivo : MotivoReporteMensaje.values()) {
            assertNotNull(motivo.tipoReporte(), motivo.name());
        }
        assertEquals(TipoReporte.COBRO_EXTERNO, MotivoReporteMensaje.CONTACTO_EXTERNO.tipoReporte());
        assertEquals(TipoReporte.ACOSO, MotivoReporteMensaje.ACOSO.tipoReporte());
        assertEquals(TipoReporte.FRAUDE_ACADEMICO, MotivoReporteMensaje.FRAUDE_ACADEMICO.tipoReporte());
    }
}
