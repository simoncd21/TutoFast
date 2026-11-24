package com.tutofast.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.tutofast.excepcion.ExcepcionNegocio;

class ServicioTutoriaTest {

    private static final Instant CONFIRMACION = Instant.parse("2026-11-23T17:00:00Z");
    private static final Instant SESION = Instant.parse("2026-12-05T19:00:00Z");

    private SolicitudTutoria solicitud;
    private Propuesta propuesta;
    private ServicioTutoria servicio;

    @BeforeEach
    void preparar() {
        solicitud = new SolicitudTutoria();
        solicitud.setId("sol1");
        solicitud.setEstudianteId("est1");
        solicitud.setFechaHora(SESION);
        solicitud.setModalidad(Modalidad.PRESENCIAL);
        solicitud.setZonaEncuentro("Biblioteca central");
        solicitud.setPrecioInicialCOP(new BigDecimal("25000"));
        solicitud.registrarPropuesta();
        solicitud.iniciarNegociacion();
        solicitud.seleccionarTutor();

        propuesta = Propuesta.nueva("sol1", "tut1", TipoPropuesta.ALTERNATIVA_CONDICIONES, new BigDecimal("30000"),
                Duracion.MIN_90, Modalidad.PRESENCIAL, "nota", CONFIRMACION);
        propuesta.setId("pro1");
        propuesta.aceptar(CONFIRMACION);

        servicio = ServicioTutoria.confirmar(solicitud, propuesta, new BigDecimal("10.00"), CONFIRMACION);
    }

    private static void assertConflicto(Runnable accion, String codigo) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ExcepcionNegocio.class, e -> {
            assertThat(e.getEstado()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(e.getCodigo()).isEqualTo(codigo);
        });
    }

    // ---- confirmar ----

    @Test
    void confirmarCongelaLasCondicionesDeLaPropuestaYFormalizaLaSolicitud() {
        assertThat(servicio.getEstado()).isEqualTo(EstadoServicio.CONFIRMADO);
        assertThat(servicio.getPrecioFinalCOP()).isEqualByComparingTo("30000");
        assertThat(servicio.getDuracion()).isEqualTo(Duracion.MIN_90);
        assertThat(servicio.getModalidad()).isEqualTo(Modalidad.PRESENCIAL);
        assertThat(servicio.getComisionPct()).isEqualByComparingTo("10");
        assertThat(servicio.getLugarOEnlace()).isEqualTo("Biblioteca central");
        assertThat(servicio.getEstudianteId()).isEqualTo("est1");
        assertThat(servicio.getTutorId()).isEqualTo("tut1");
        assertThat(servicio.getFechaHora()).isEqualTo(SESION);
        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.FORMALIZADA);
    }

    @Test
    void confirmarExigeSolicitudConTutorSeleccionado() {
        SolicitudTutoria otra = new SolicitudTutoria();
        otra.setId("sol1");
        Propuesta aceptada = Propuesta.nueva("sol1", "tut1", TipoPropuesta.ACEPTA_PRECIO, BigDecimal.TEN,
                Duracion.MIN_60, Modalidad.VIRTUAL, null, CONFIRMACION);
        aceptada.aceptar(CONFIRMACION);

        assertThatThrownBy(() -> ServicioTutoria.confirmar(otra, aceptada, BigDecimal.TEN, CONFIRMACION))
                .isInstanceOf(ExcepcionNegocio.class);
        assertThat(otra.getEstado()).isEqualTo(EstadoSolicitud.PUBLICADA);
    }

    @Test
    void confirmarExigePropuestaAceptadaDeLaMismaSolicitud() {
        SolicitudTutoria otra = new SolicitudTutoria();
        otra.setId("sol1");
        otra.registrarPropuesta();
        otra.seleccionarTutor();
        Propuesta sinAceptar = Propuesta.nueva("sol1", "tut1", TipoPropuesta.ACEPTA_PRECIO, BigDecimal.TEN,
                Duracion.MIN_60, Modalidad.VIRTUAL, null, CONFIRMACION);
        Propuesta ajena = Propuesta.nueva("otra", "tut1", TipoPropuesta.ACEPTA_PRECIO, BigDecimal.TEN,
                Duracion.MIN_60, Modalidad.VIRTUAL, null, CONFIRMACION);
        ajena.aceptar(CONFIRMACION);

        assertThatThrownBy(() -> ServicioTutoria.confirmar(otra, sinAceptar, BigDecimal.TEN, CONFIRMACION))
                .isInstanceOf(ExcepcionNegocio.class);
        assertThatThrownBy(() -> ServicioTutoria.confirmar(otra, ajena, BigDecimal.TEN, CONFIRMACION))
                .isInstanceOf(ExcepcionNegocio.class);
        assertThat(otra.getEstado()).isEqualTo(EstadoSolicitud.TUTOR_SELECCIONADO);
    }

    // ---- transiciones válidas ----

    @Test
    void recorreConfirmadoEnDesarrolloFinalizado() {
        servicio.iniciarSesion(SESION);
        assertThat(servicio.getEstado()).isEqualTo(EstadoServicio.EN_DESARROLLO);
        assertThat(servicio.getFechaInicio()).isEqualTo(SESION);

        Instant fin = SESION.plusSeconds(5400);
        servicio.finalizar(fin);
        assertThat(servicio.getEstado()).isEqualTo(EstadoServicio.FINALIZADO);
        assertThat(servicio.getFechaFinalizacion()).isEqualTo(fin);
    }

    @Test
    void reporteLlevaAEnRevisionYElAdministradorResuelve() {
        servicio.iniciarSesion(SESION);
        servicio.ponerEnRevision();
        assertThat(servicio.getEstado()).isEqualTo(EstadoServicio.EN_REVISION);
        servicio.resolverFinalizando(SESION.plusSeconds(7200));
        assertThat(servicio.getEstado()).isEqualTo(EstadoServicio.FINALIZADO);
        assertThat(servicio.getFechaFinalizacion()).isNotNull();
    }

    @Test
    void reporteDesdeConfirmadoPuedeTerminarCancelado() {
        servicio.ponerEnRevision();
        servicio.resolverCancelando();
        assertThat(servicio.getEstado()).isEqualTo(EstadoServicio.CANCELADO);
    }

    // ---- transiciones inválidas ----

    @Test
    void noSeFinalizaSinHaberIniciado() {
        assertConflicto(() -> servicio.finalizar(SESION), "TRANSICION_INVALIDA");
        assertThat(servicio.getEstado()).isEqualTo(EstadoServicio.CONFIRMADO);
    }

    @Test
    void dobleFinalizacionDevuelveElMensajeDeNoRequiereAcciones() {
        servicio.iniciarSesion(SESION);
        servicio.finalizar(SESION.plusSeconds(3600));

        assertThatThrownBy(() -> servicio.finalizar(SESION.plusSeconds(7200)))
                .isInstanceOfSatisfying(ExcepcionNegocio.class, e -> {
                    assertThat(e.getEstado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("El servicio ya se encuentra finalizado y no requiere nuevas acciones");
                });
    }

    @Test
    void noSeIniciaDosVecesNiDespuesDeFinalizar() {
        servicio.iniciarSesion(SESION);
        assertConflicto(() -> servicio.iniciarSesion(SESION), "TRANSICION_INVALIDA");
        servicio.finalizar(SESION.plusSeconds(60));
        assertConflicto(() -> servicio.iniciarSesion(SESION), "TRANSICION_INVALIDA");
    }

    @Test
    void servicioEnRevisionNoSePuedeFinalizarNiIniciar() {
        servicio.ponerEnRevision();
        assertConflicto(() -> servicio.finalizar(SESION), "SERVICIO_EN_REVISION");
        assertConflicto(() -> servicio.iniciarSesion(SESION), "TRANSICION_INVALIDA");
    }

    @Test
    void noSePuedeRevisarUnServicioFinalizadoNiResolverSinRevision() {
        assertConflicto(servicio::resolverCancelando, "TRANSICION_INVALIDA");
        assertConflicto(() -> servicio.resolverFinalizando(SESION), "TRANSICION_INVALIDA");
        servicio.iniciarSesion(SESION);
        servicio.finalizar(SESION.plusSeconds(60));
        assertConflicto(servicio::ponerEnRevision, "TRANSICION_INVALIDA");
    }

    // ---- inmutabilidad y reglas de apoyo ----

    @Test
    void elEnlaceSoloSeEditaMientrasEstaConfirmado() {
        servicio.actualizarLugarOEnlace("https://meet.example/abc");
        assertThat(servicio.getLugarOEnlace()).isEqualTo("https://meet.example/abc");

        servicio.iniciarSesion(SESION);
        assertConflicto(() -> servicio.actualizarLugarOEnlace("otro"), "SERVICIO_NO_EDITABLE");
        assertThat(servicio.getPrecioFinalCOP()).isEqualByComparingTo("30000");
    }

    @Test
    void cierreAutomaticoEspera24HorasDespuesDelFinProgramado() {
        servicio.iniciarSesion(SESION);
        Instant fin = servicio.finProgramado();
        assertThat(fin).isEqualTo(SESION.plusSeconds(90 * 60));

        assertThat(servicio.puedeCerrarseAutomaticamente(fin.plusSeconds(23 * 3600), false)).isFalse();
        assertThat(servicio.puedeCerrarseAutomaticamente(fin.plusSeconds(24 * 3600), false)).isTrue();
        assertThat(servicio.puedeCerrarseAutomaticamente(SESION, true)).isTrue();
    }

    @Test
    void cierreAutomaticoIgnoraServiciosQueNoEstanEnDesarrollo() {
        assertThat(servicio.puedeCerrarseAutomaticamente(SESION.plusSeconds(999_999), true)).isFalse();
        servicio.iniciarSesion(SESION);
        servicio.ponerEnRevision();
        assertThat(servicio.puedeCerrarseAutomaticamente(SESION.plusSeconds(999_999), true)).isFalse();
    }

    @Test
    void iniciaAutomaticamenteCuandoLlegaLaHora() {
        assertThat(servicio.debeIniciar(SESION.minusSeconds(1))).isFalse();
        assertThat(servicio.debeIniciar(SESION)).isTrue();
        servicio.iniciarSesion(SESION);
        assertThat(servicio.debeIniciar(SESION.plusSeconds(60))).isFalse();
    }

    @Test
    void soloLosParticipantesSonParticipantes() {
        assertThat(servicio.esParticipante("est1")).isTrue();
        assertThat(servicio.esParticipante("tut1")).isTrue();
        assertThat(servicio.esParticipante("otro")).isFalse();
        assertThat(servicio.esParticipante(null)).isFalse();
    }
}
