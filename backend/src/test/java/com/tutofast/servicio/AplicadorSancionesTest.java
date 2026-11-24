package com.tutofast.servicio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.tutofast.EscenarioNegociacion;
import com.tutofast.dto.SancionEntradaDTO;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.EstadoReporte;
import com.tutofast.modelo.EstadoSancion;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.Sancion;
import com.tutofast.modelo.TipoReporte;
import com.tutofast.modelo.TipoSancion;
import com.tutofast.modelo.Usuario;
import com.tutofast.modelo.Periodo;

class AplicadorSancionesTest {

    private EscenarioNegociacion e;
    private AplicadorSanciones sanciones;
    private Usuario carlos;
    private Usuario laura;
    private Usuario admin;

    @BeforeEach
    void preparar() {
        e = new EscenarioNegociacion();
        sanciones = e.aplicadorSanciones;
        carlos = e.crearUsuario("Carlos Gómez", Rol.ESTUDIANTE);
        laura = e.crearUsuario("Laura Martínez", Rol.TUTOR);
        admin = e.crearUsuario("Administrador", Rol.ADMINISTRADOR);
    }

    private Sancion aplicar(Usuario denunciado, SancionEntradaDTO dto) {
        return sanciones.aplicarPorModeracion(admin.getId(), carlos.getId(), denunciado.getId(),
                TipoReporte.CONDUCTA_ABUSIVA, "Mensaje ofensivo", "Observación", dto, Instant.now());
    }

    private static SancionEntradaDTO suspension(Duration duracion) {
        return new SancionEntradaDTO(TipoSancion.SUSPENSION_TEMPORAL, null, Instant.now().plus(duracion));
    }

    private static void assertNegocio(String codigo, HttpStatus estado, Runnable accion) {
        ExcepcionNegocio ex = assertThrows(ExcepcionNegocio.class, accion::run);
        assertEquals(codigo, ex.getCodigo());
        assertEquals(estado, ex.getEstado());
    }

    // ---------------------------------------------------------------- validar

    @Test
    void validaLasReglasDeCadaTipoDeSancion() {
        Instant ahora = Instant.now();
        sanciones.validar(new SancionEntradaDTO(TipoSancion.ADVERTENCIA, null, null), ahora);
        sanciones.validar(new SancionEntradaDTO(TipoSancion.BLOQUEO_PERMANENTE, ahora.minusSeconds(5), null), ahora);
        sanciones.validar(new SancionEntradaDTO(TipoSancion.SUSPENSION_TEMPORAL, ahora.minusSeconds(5), ahora.plusSeconds(60)), ahora);

        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> sanciones.validar(null, ahora));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> sanciones.validar(new SancionEntradaDTO(null, null, null), ahora));
        assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY, () -> sanciones.validar(
                new SancionEntradaDTO(TipoSancion.SUSPENSION_TEMPORAL, null, null), ahora));
        assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY, () -> sanciones.validar(
                new SancionEntradaDTO(TipoSancion.SUSPENSION_TEMPORAL, null, ahora.minusSeconds(1)), ahora));
        assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY, () -> sanciones.validar(
                new SancionEntradaDTO(TipoSancion.SUSPENSION_TEMPORAL, ahora.plusSeconds(10), ahora.plusSeconds(60)), ahora));
    }

    // ---------------------------------------------------------------- aplicar

    @Test
    void laSuspensionTemporalSuspendeLaCuentaYRadicaUnReporteResuelto() {
        Sancion s = aplicar(laura, suspension(Duration.ofDays(7)));

        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta());
        assertEquals(EstadoSancion.VIGENTE, s.getEstado());
        assertEquals(TipoSancion.SUSPENSION_TEMPORAL, s.getTipo());
        assertNotNull(s.getVigencia().getFin());
        var reporte = e.reportes.get(s.getReporteId());
        assertEquals(EstadoReporte.RESUELTO, reporte.getEstado());
        assertEquals(TipoReporte.CONDUCTA_ABUSIVA, reporte.getTipo());
        assertEquals(carlos.getId(), reporte.getReportanteId());
        assertEquals(laura.getId(), reporte.getDenunciadoId());
        assertNull(reporte.getServicioId());
        assertEquals(admin.getId(), reporte.getResolucion().getResueltoPor());
        assertEquals("Observación", reporte.getResolucion().getObservacion());
        verify(e.notificaciones).notificar(eq(laura.getId()), eq(TipoNotificacion.SANCION_APLICADA), contains("suspensión temporal"),
                contains("apelar"), anyString());
        assertTrue(e.acciones().contains("SANCION_APLICADA"));
    }

    @Test
    void elBloqueoPermanenteBloqueaLaCuentaSinFechaDeFin() {
        Sancion s = aplicar(laura, new SancionEntradaDTO(TipoSancion.BLOQUEO_PERMANENTE, null, Instant.now().plusSeconds(99)));

        assertEquals(EstadoCuenta.BLOQUEADA, laura.getEstadoCuenta());
        assertNull(s.getVigencia().getFin(), "el fin se ignora en un bloqueo permanente");
    }

    @Test
    void unBloqueoSobreUnaCuentaSuspendidaLaBloquea() {
        aplicar(laura, suspension(Duration.ofDays(3)));

        aplicar(laura, new SancionEntradaDTO(TipoSancion.BLOQUEO_PERMANENTE, null, null));

        assertEquals(EstadoCuenta.BLOQUEADA, laura.getEstadoCuenta());
    }

    @Test
    void unaAdvertenciaSoloDejaConstanciaYAvisa() {
        Sancion s = aplicar(laura, new SancionEntradaDTO(TipoSancion.ADVERTENCIA, null, null));

        assertEquals(EstadoCuenta.ACTIVA, laura.getEstadoCuenta());
        assertNull(s.getVigencia());
        verify(e.notificaciones).notificar(eq(laura.getId()), eq(TipoNotificacion.SANCION_APLICADA), contains("advertencia"),
                anyString(), anyString());
    }

    @Test
    void nuncaSeSancionaAUnAdministradorNiAUnUsuarioInexistente() {
        assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY, () -> aplicar(admin, suspension(Duration.ofDays(1))));
        assertThrows(ExcepcionNoEncontrado.class, () -> sanciones.aplicarPorModeracion(admin.getId(), carlos.getId(),
                "000000000000000000000000", TipoReporte.ACOSO, "x", null, suspension(Duration.ofDays(1)), Instant.now()));

        assertTrue(e.reportes.isEmpty());
        assertTrue(e.sanciones.isEmpty());
        assertEquals(EstadoCuenta.ACTIVA, admin.getEstadoCuenta());
    }

    @Test
    void unaSancionInvalidaNoCreaNingunExpediente() {
        assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY,
                () -> aplicar(laura, new SancionEntradaDTO(TipoSancion.SUSPENSION_TEMPORAL, null, null)));

        assertTrue(e.reportes.isEmpty());
        assertTrue(e.sanciones.isEmpty());
    }

    @Test
    void siFallaElGuardadoDeLaCuentaSeCompensanSancionYReporte() {
        when(e.repoUsuarios.save(any(Usuario.class))).thenThrow(new RuntimeException("Mongo caído"));

        assertThrows(RuntimeException.class, () -> aplicar(laura, suspension(Duration.ofDays(2))));

        assertTrue(e.sanciones.isEmpty());
        assertTrue(e.reportes.isEmpty());
    }

    @Test
    void unFalloAlNotificarNoRevierteLaSancion() {
        doThrow(new RuntimeException("sin conexión")).when(e.notificaciones)
                .notificar(anyString(), any(TipoNotificacion.class), anyString(), anyString(), anyString());

        aplicar(laura, suspension(Duration.ofDays(2)));

        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta());
        assertEquals(1, e.sanciones.size());
    }

    // ---------------------------------------------------------------- levantar vencidas

    /** Sanción de suspensión cuya vigencia ya terminó (no se puede crear una así por el flujo normal). */
    private Sancion suspensionVencida(Usuario usuario) {
        Sancion s = Sancion.aplicar(EscenarioNegociacion.nuevoId(), usuario.getId(), TipoSancion.SUSPENSION_TEMPORAL,
                new Periodo(Instant.now().minus(Duration.ofDays(8)), Instant.now().minus(Duration.ofDays(1))));
        s.setId(EscenarioNegociacion.nuevoId());
        e.sanciones.put(s.getId(), s);
        usuario.setEstadoCuenta(EstadoCuenta.SUSPENDIDA);
        return s;
    }

    @Test
    void alVencerLaSuspensionLaSancionSeCumpleYLaCuentaSeReactiva() {
        Sancion s = suspensionVencida(laura);

        int cerradas = sanciones.levantarVencidas(Instant.now());

        assertEquals(1, cerradas);
        assertEquals(EstadoSancion.CUMPLIDA, s.getEstado());
        assertEquals(EstadoCuenta.ACTIVA, laura.getEstadoCuenta());
        assertTrue(e.acciones().contains("SANCION_CUMPLIDA"));
        verify(e.notificaciones).notificar(eq(laura.getId()), eq(TipoNotificacion.SANCION_APLICADA), contains("terminó"),
                anyString(), anyString());
        assertEquals(0, sanciones.levantarVencidas(Instant.now()), "no se procesa dos veces");
    }

    @Test
    void unaSuspensionVigenteTodaviaNoSeLevanta() {
        Sancion s = aplicar(laura, suspension(Duration.ofDays(3)));

        assertEquals(0, sanciones.levantarVencidas(Instant.now()));
        assertEquals(EstadoSancion.VIGENTE, s.getEstado());
        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta());
    }

    @Test
    void unaSuspensionEnApelacionNoSeLevantaSola() {
        Sancion s = suspensionVencida(laura);
        s.apelar("No hice nada", List.of(), Instant.now());

        assertEquals(0, sanciones.levantarVencidas(Instant.now()));
        assertEquals(EstadoSancion.EN_APELACION, s.getEstado());
        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta());
    }

    @Test
    void siHayOtraSancionVigenteLaCuentaSigueSuspendida() {
        suspensionVencida(laura);
        Sancion otra = aplicar(laura, suspension(Duration.ofDays(5)));

        assertEquals(1, sanciones.levantarVencidas(Instant.now()));

        assertEquals(EstadoSancion.VIGENTE, otra.getEstado());
        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta());
    }

    @Test
    void unaCuentaBloqueadaNoSeReactivaAlVencerUnaSuspensionAnterior() {
        suspensionVencida(laura);
        laura.setEstadoCuenta(EstadoCuenta.BLOQUEADA);

        assertEquals(1, sanciones.levantarVencidas(Instant.now()));

        assertEquals(EstadoCuenta.BLOQUEADA, laura.getEstadoCuenta());
    }

    @Test
    void unFalloEnUnaSancionNoImpideLevantarLasDemas() {
        Sancion mala = suspensionVencida(laura);
        Sancion buena = suspensionVencida(carlos);
        when(e.repoSanciones.save(any(Sancion.class))).thenAnswer(inv -> {
            Sancion s = inv.getArgument(0);
            if (s.getId().equals(mala.getId())) {
                throw new RuntimeException("Mongo caído");
            }
            return s;
        });

        assertEquals(1, sanciones.levantarVencidas(Instant.now()));

        assertEquals(EstadoCuenta.ACTIVA, carlos.getEstadoCuenta());
        assertEquals(EstadoSancion.CUMPLIDA, buena.getEstado());
    }

    // ---------------------------------------------------------------- revocación y cierre (módulo 10)

    @Test
    void restablecerCuentaTrasRevocarUnaSuspensionLaReactiva() {
        Sancion s = aplicar(laura, suspension(Duration.ofDays(7)));
        s.setEstado(EstadoSancion.REVOCADA);

        sanciones.restablecerCuenta(s, Instant.now());

        assertEquals(EstadoCuenta.ACTIVA, laura.getEstadoCuenta());
    }

    @Test
    void restablecerCuentaConUnBloqueoVigenteLaDejaBloqueada() {
        Sancion suspension = aplicar(laura, suspension(Duration.ofDays(7)));
        aplicar(laura, new SancionEntradaDTO(TipoSancion.BLOQUEO_PERMANENTE, null, null));
        suspension.setEstado(EstadoSancion.REVOCADA);

        sanciones.restablecerCuenta(suspension, Instant.now());

        assertEquals(EstadoCuenta.BLOQUEADA, laura.getEstadoCuenta());
    }

    @Test
    void restablecerCuentaDeUnBloqueoConUnaSuspensionVigenteLaDejaSuspendida() {
        aplicar(laura, suspension(Duration.ofDays(7)));
        Sancion bloqueo = aplicar(laura, new SancionEntradaDTO(TipoSancion.BLOQUEO_PERMANENTE, null, null));
        assertEquals(EstadoCuenta.BLOQUEADA, laura.getEstadoCuenta());
        bloqueo.setEstado(EstadoSancion.REVOCADA);

        sanciones.restablecerCuenta(bloqueo, Instant.now());

        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta());
    }

    @Test
    void restablecerCuentaNoTocaUnaAdvertenciaNiUnEstadoCambiadoAMano() {
        Sancion advertencia = aplicar(laura, new SancionEntradaDTO(TipoSancion.ADVERTENCIA, null, null));
        advertencia.setEstado(EstadoSancion.REVOCADA);
        laura.setEstadoCuenta(EstadoCuenta.SUSPENDIDA); // suspensión manual, sin sanción que la respalde
        sanciones.restablecerCuenta(advertencia, Instant.now());
        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta());

        Usuario otro = e.crearUsuario("Otro Tutor", Rol.TUTOR);
        Sancion suspension = aplicar(otro, suspension(Duration.ofDays(7)));
        suspension.setEstado(EstadoSancion.REVOCADA);
        otro.setEstadoCuenta(EstadoCuenta.BLOQUEADA); // bloqueo manual posterior
        sanciones.restablecerCuenta(suspension, Instant.now());
        assertEquals(EstadoCuenta.BLOQUEADA, otro.getEstadoCuenta());
    }

    @Test
    void siGuardarLaCuentaFallaAlRestablecerElEstadoEnMemoriaNoCambia() {
        Sancion s = aplicar(laura, suspension(Duration.ofDays(7)));
        s.setEstado(EstadoSancion.REVOCADA);
        when(e.repoUsuarios.save(any(Usuario.class))).thenThrow(new RuntimeException("Mongo caído"));

        assertThrows(RuntimeException.class, () -> sanciones.restablecerCuenta(s, Instant.now()));

        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta());
    }

    @Test
    void descartarEliminaLaSancionYRestableceLaCuenta() {
        Sancion s = aplicar(laura, suspension(Duration.ofDays(7)));

        sanciones.descartar(s, Instant.now());

        assertTrue(e.sanciones.isEmpty());
        assertEquals(EstadoCuenta.ACTIVA, laura.getEstadoCuenta());
    }

    @Test
    void cerrarSiVencidaCumpleUnaSuspensionRatificadaQueYaTermino() {
        Sancion s = suspensionVencida(laura);
        s.setEstado(EstadoSancion.RATIFICADA);

        assertTrue(sanciones.cerrarSiVencida(s, Instant.now()));

        assertEquals(EstadoSancion.CUMPLIDA, s.getEstado());
        assertEquals(EstadoCuenta.ACTIVA, laura.getEstadoCuenta());
        verify(e.notificaciones).notificar(eq(laura.getId()), eq(TipoNotificacion.SANCION_APLICADA),
                contains("terminó"), anyString(), anyString());
    }

    @Test
    void cerrarSiVencidaNoTocaLasQueSiguenVigentesOSinRatificar() {
        Sancion vigente = aplicar(laura, suspension(Duration.ofDays(7)));
        vigente.setEstado(EstadoSancion.RATIFICADA);
        assertFalse(sanciones.cerrarSiVencida(vigente, Instant.now()));
        assertEquals(EstadoSancion.RATIFICADA, vigente.getEstado());
        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta());

        Sancion enApelacion = suspensionVencida(carlos);
        enApelacion.setEstado(EstadoSancion.EN_APELACION);
        assertFalse(sanciones.cerrarSiVencida(enApelacion, Instant.now()));
        assertEquals(EstadoSancion.EN_APELACION, enApelacion.getEstado());
    }
}
