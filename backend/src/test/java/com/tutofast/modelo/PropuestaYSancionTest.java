package com.tutofast.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.tutofast.excepcion.ExcepcionNegocio;

class PropuestaYSancionTest {

    private static final Instant T0 = Instant.parse("2026-12-01T10:00:00Z");

    private static Propuesta propuesta() {
        return Propuesta.nueva("sol1", "tut1", TipoPropuesta.CONTRAPROPUESTA_PRECIO, new BigDecimal("30000"),
                Duracion.MIN_90, Modalidad.VIRTUAL, "nota", T0);
    }

    // ---- Propuesta ----

    @Test
    void laPropuestaNuevaEstaEnviadaYActiva() {
        Propuesta p = propuesta();
        assertThat(p.getEstado()).isEqualTo(EstadoPropuesta.ENVIADA);
        assertThat(p.isActiva()).isTrue();
    }

    @Test
    void contraofertaYModificacionVuelvenAEnviada() {
        Propuesta p = propuesta();
        p.contraofertar(new BigDecimal("25000"), T0);
        assertThat(p.getEstado()).isEqualTo(EstadoPropuesta.CONTRAOFERTA_ESTUDIANTE);
        assertThat(p.getContraofertaCOP()).isEqualByComparingTo("25000");
        assertThat(p.isActiva()).isTrue();

        p.modificar(TipoPropuesta.ACEPTA_PRECIO, new BigDecimal("25000"), Duracion.MIN_60, Modalidad.VIRTUAL, null, T0);
        assertThat(p.getEstado()).isEqualTo(EstadoPropuesta.ENVIADA);
        assertThat(p.getContraofertaCOP()).isNull();
    }

    @Test
    void descartarORetirarDejanLaPropuestaInactivaYTerminal() {
        Propuesta descartada = propuesta();
        descartada.descartar(T0);
        assertThat(descartada.isActiva()).isFalse();
        assertThatThrownBy(() -> descartada.aceptar(T0)).isInstanceOf(ExcepcionNegocio.class);

        Propuesta retirada = propuesta();
        retirada.retirar(T0);
        assertThat(retirada.isActiva()).isFalse();
        assertThatThrownBy(() -> retirada.retirar(T0)).isInstanceOf(ExcepcionNegocio.class);
    }

    @Test
    void laPropuestaAceptadaSigueActivaPeroNoSeDescarta() {
        Propuesta p = propuesta();
        p.aceptar(T0);
        assertThat(p.isActiva()).isTrue();
        assertThatThrownBy(() -> p.descartar(T0)).isInstanceOf(ExcepcionNegocio.class);
    }

    @Test
    void soloSePuedeContraofertarUnaPropuestaEnviada() {
        Propuesta p = propuesta();
        p.contraofertar(new BigDecimal("20000"), T0);
        assertThatThrownBy(() -> p.contraofertar(new BigDecimal("21000"), T0)).isInstanceOf(ExcepcionNegocio.class);
    }

    // ---- Sancion ----

    private static Sancion suspension() {
        return Sancion.aplicar("rep1", "usr1", TipoSancion.SUSPENSION_TEMPORAL,
                new Periodo(Instant.parse("2026-12-07T00:00:00Z"), Instant.parse("2026-12-21T00:00:00Z")));
    }

    @Test
    void laSuspensionSoloSuspendeDentroDeLaVigencia() {
        Sancion s = suspension();
        assertThat(s.suspendeCuenta(Instant.parse("2026-12-10T00:00:00Z"))).isTrue();
        assertThat(s.suspendeCuenta(Instant.parse("2026-12-21T00:00:00Z"))).isFalse();
        assertThat(Sancion.aplicar("r", "u", TipoSancion.ADVERTENCIA, null).suspendeCuenta(T0)).isFalse();
    }

    @Test
    void laSuspensionTemporalExigeFechaDeFin() {
        assertThatThrownBy(() -> Sancion.aplicar("r", "u", TipoSancion.SUSPENSION_TEMPORAL,
                new Periodo(T0, null))).isInstanceOf(ExcepcionNegocio.class);
    }

    @Test
    void apelarYRevocarReactivaLaCuenta() {
        Sancion s = suspension();
        Apelacion a = s.apelar("Tuve una emergencia médica", List.of("ev1"), T0);
        assertThat(s.getEstado()).isEqualTo(EstadoSancion.EN_APELACION);
        assertThat(s.tieneApelacionPendiente()).isTrue();

        s.resolverApelacion(a.getId(), ResultadoApelacion.REVOCADA, T0.plusSeconds(60));

        assertThat(s.getEstado()).isEqualTo(EstadoSancion.REVOCADA);
        assertThat(s.suspendeCuenta(Instant.parse("2026-12-10T00:00:00Z"))).isFalse();
        assertThat(a.getResultado()).isEqualTo(ResultadoApelacion.REVOCADA);
    }

    @Test
    void ratificarMantieneLaSuspension() {
        Sancion s = suspension();
        Apelacion a = s.apelar("descargo", null, T0);
        s.resolverApelacion(a.getId(), ResultadoApelacion.RATIFICADA, T0);
        assertThat(s.getEstado()).isEqualTo(EstadoSancion.RATIFICADA);
        assertThat(s.suspendeCuenta(Instant.parse("2026-12-10T00:00:00Z"))).isTrue();
    }

    @Test
    void segundaApelacionPendienteDevuelve409() {
        Sancion s = suspension();
        s.apelar("primer descargo", null, T0);

        assertThatThrownBy(() -> s.apelar("segundo descargo", null, T0))
                .isInstanceOfSatisfying(ExcepcionNegocio.class, e -> assertThat(e.getEstado()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void noSeApelaUnaSancionYaResuelta() {
        Sancion s = suspension();
        Apelacion a = s.apelar("descargo", null, T0);
        s.resolverApelacion(a.getId(), ResultadoApelacion.RATIFICADA, T0);

        assertThatThrownBy(() -> s.apelar("otra vez", null, T0)).isInstanceOf(ExcepcionNegocio.class);
        assertThatThrownBy(() -> s.resolverApelacion(a.getId(), ResultadoApelacion.REVOCADA, T0))
                .isInstanceOf(ExcepcionNegocio.class);
    }

    // ---- Reporte y mensaje ----

    @Test
    void elReporteSigueRadicadoEnRevisionYResuelto() {
        Reporte r = Reporte.radicar("est1", "tut1", "srv1", TipoReporte.INASISTENCIA, "No se presentó", List.of("ev1"), T0);
        assertThat(r.getEstado()).isEqualTo(EstadoReporte.RADICADO);
        assertThat(r.categoria()).isEqualTo(CategoriaReporte.INCUMPLIMIENTO_SERVICIO);
        assertThat(r.estaAbierto()).isTrue();

        r.iniciarRevision();
        r.resolver(DecisionReporte.A_FAVOR_REPORTANTE, AccionPago.REEMBOLSAR, "Procede", "adm1", T0);

        assertThat(r.getEstado()).isEqualTo(EstadoReporte.RESUELTO);
        assertThat(r.getResolucion().getAccionPago()).isEqualTo(AccionPago.REEMBOLSAR);
        assertThat(r.estaAbierto()).isFalse();
        assertThatThrownBy(() -> r.resolver(DecisionReporte.DESESTIMADO, AccionPago.NINGUNA, "x", "adm1", T0))
                .isInstanceOf(ExcepcionNegocio.class);
    }

    @Test
    void nadaPuedeReportarseASiMismo() {
        assertThatThrownBy(() -> Reporte.radicar("u1", "u1", null, TipoReporte.ACOSO, "motivo", null, T0))
                .isInstanceOfSatisfying(ExcepcionNegocio.class, e -> assertThat(e.getEstado()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void unMensajeSoloSeReportaUnaVez() {
        Mensaje m = Mensaje.nuevo("sol1", "tut1", "est1", "hola", null, T0);
        m.reportar("lenguaje inapropiado");
        assertThat(m.isReportado()).isTrue();
        assertThatThrownBy(() -> m.reportar("otra vez")).isInstanceOf(ExcepcionNegocio.class);
        assertThatThrownBy(() -> Mensaje.nuevo("sol1", "tut1", "est1", " ", null, T0)).isInstanceOf(ExcepcionNegocio.class);
    }

    @Test
    void resolverUnaApelacionDejaConstanciaDelAdministradorYLaObservacion() {
        Sancion s = suspension();
        Apelacion a = s.apelar("Tuve una emergencia médica", List.of("ev1"), T0);

        s.resolverApelacion(a.getId(), ResultadoApelacion.REVOCADA, T0.plusSeconds(60), "Incapacidad válida", "admin1");

        assertThat(a.getObservacionResolucion()).isEqualTo("Incapacidad válida");
        assertThat(a.getResueltaPor()).isEqualTo("admin1");
        assertThat(a.getFechaResolucion()).isEqualTo(T0.plusSeconds(60));
        assertThat(s.getEstado()).isEqualTo(EstadoSancion.REVOCADA);
    }

    @Test
    void laResolucionSinDatosDelAdministradorSigueFuncionando() {
        Sancion s = suspension();
        Apelacion a = s.apelar("descargo", null, T0);

        s.resolverApelacion(a.getId(), ResultadoApelacion.RATIFICADA, T0);

        assertThat(a.getObservacionResolucion()).isNull();
        assertThat(a.getResueltaPor()).isNull();
    }

    @Test
    void unaApelacionNoPuedeResolverseComoPendienteNiDosVeces() {
        Sancion s = suspension();
        Apelacion a = s.apelar("descargo", null, T0);

        assertThatThrownBy(() -> s.resolverApelacion(a.getId(), ResultadoApelacion.PENDIENTE, T0, "x", "admin1"))
                .isInstanceOf(ExcepcionNegocio.class);
        assertThat(s.getEstado()).isEqualTo(EstadoSancion.EN_APELACION);
        s.resolverApelacion(a.getId(), ResultadoApelacion.REVOCADA, T0, "x", "admin1");
        assertThatThrownBy(() -> a.resolver(ResultadoApelacion.RATIFICADA, T0, "y", "admin2"))
                .isInstanceOfSatisfying(ExcepcionNegocio.class, e -> assertThat(e.getEstado()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(a.getResueltaPor()).isEqualTo("admin1");
    }
}
