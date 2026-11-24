package com.tutofast.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.tutofast.EscenarioNegociacion;
import com.tutofast.dto.ApelacionDTO;
import com.tutofast.dto.ApelacionEntradaDTO;
import com.tutofast.dto.ExpedienteReporteDTO;
import com.tutofast.dto.Pagina;
import com.tutofast.dto.ReporteDTO;
import com.tutofast.dto.ReporteEntradaDTO;
import com.tutofast.dto.ReportesMiosDTO;
import com.tutofast.dto.ResolucionApelacionEntradaDTO;
import com.tutofast.dto.ResolucionEntradaDTO;
import com.tutofast.dto.SancionDTO;
import com.tutofast.dto.SancionEntradaDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.modelo.AccionPago;
import com.tutofast.modelo.DecisionReporte;
import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.EstadoPago;
import com.tutofast.modelo.EstadoReporte;
import com.tutofast.modelo.EstadoSancion;
import com.tutofast.modelo.EstadoServicio;
import com.tutofast.modelo.Mensaje;
import com.tutofast.modelo.Pago;
import com.tutofast.modelo.Periodo;
import com.tutofast.modelo.Reporte;
import com.tutofast.modelo.ResultadoApelacion;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.Sancion;
import com.tutofast.modelo.ServicioTutoria;
import com.tutofast.modelo.TipoReporte;
import com.tutofast.modelo.TipoSancion;
import com.tutofast.modelo.Usuario;
import com.tutofast.seguridad.UsuarioAutenticado;

import static com.tutofast.EscenarioNegociacion.autenticado;

class ServicioReportesTest {

    private EscenarioNegociacion e;
    private ServicioReportes reportes;
    private Usuario carlos;
    private Usuario laura;
    private Usuario intruso;
    private Usuario admin;
    private ServicioTutoria servicio;

    @BeforeEach
    void preparar() {
        e = new EscenarioNegociacion();
        reportes = e.servicioReportes;
        carlos = e.crearUsuario("Carlos Gómez", Rol.ESTUDIANTE);
        laura = e.crearUsuario("Laura Martínez", Rol.TUTOR);
        intruso = e.crearUsuario("Pedro Intruso", Rol.TUTOR);
        admin = e.crearUsuario("Administrador", Rol.ADMINISTRADOR);
        servicio = e.crearServicio(carlos, laura, EstadoServicio.EN_DESARROLLO, Instant.now().minus(Duration.ofHours(1)), "40000");
    }

    // ---------------------------------------------------------------- utilidades

    private static void assertNegocio(String codigo, HttpStatus estado, Runnable accion) {
        ExcepcionNegocio ex = assertThrows(ExcepcionNegocio.class, accion::run);
        assertEquals(codigo, ex.getCodigo());
        assertEquals(estado, ex.getEstado());
    }

    private ReporteEntradaDTO inasistencia() {
        return new ReporteEntradaDTO(TipoReporte.INASISTENCIA, "El tutor no se presentó tras 20 minutos de tolerancia",
                null, servicio.getId());
    }

    private ReporteDTO radicar(Usuario quien, ReporteEntradaDTO dto) {
        return reportes.radicarReporte(autenticado(quien), dto, List.of());
    }

    private ReporteDTO radicarInasistencia() {
        return radicar(carlos, inasistencia());
    }

    private Pago pago() {
        return e.pagos.values().stream().filter(p -> p.getServicioId().equals(servicio.getId())).findFirst().orElseThrow();
    }

    private ResolucionEntradaDTO resolucion(DecisionReporte decision, AccionPago accion, SancionEntradaDTO sancion) {
        return new ResolucionEntradaDTO(decision, accion, "Revisado el historial del chat", sancion);
    }

    private ReporteDTO resolver(String reporteId, DecisionReporte decision, AccionPago accion, SancionEntradaDTO sancion) {
        return reportes.resolverReporte(admin.getId(), reporteId, resolucion(decision, accion, sancion));
    }

    private static SancionEntradaDTO suspension(int dias) {
        return new SancionEntradaDTO(TipoSancion.SUSPENSION_TEMPORAL, null, Instant.now().plus(Duration.ofDays(dias)));
    }

    private static SancionEntradaDTO bloqueo() {
        return new SancionEntradaDTO(TipoSancion.BLOQUEO_PERMANENTE, null, null);
    }

    private static SancionEntradaDTO advertencia() {
        return new SancionEntradaDTO(TipoSancion.ADVERTENCIA, null, null);
    }

    /** Radica la inasistencia, la resuelve a favor del estudiante con reembolso y sanciona a Laura. Devuelve la sanción. */
    private Sancion sancionarALaura(SancionEntradaDTO sancion) {
        ReporteDTO r = radicarInasistencia();
        resolver(r.id(), DecisionReporte.A_FAVOR_REPORTANTE, AccionPago.REEMBOLSAR, sancion);
        return e.sanciones.values().stream().filter(s -> s.getReporteId().equals(r.id())).findFirst().orElseThrow();
    }

    private static final byte[] PDF_MINIMO = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};

    private static MockMultipartFile evidencia(String nombre) {
        return new MockMultipartFile("evidencias", nombre, "application/pdf", PDF_MINIMO);
    }

    private ApelacionEntradaDTO descargo() {
        return new ApelacionEntradaDTO("Tuve una emergencia médica y adjunto la incapacidad");
    }

    private ApelacionDTO apelar(Usuario quien, Sancion sancion) {
        return reportes.apelarSancion(autenticado(quien), sancion.getId(), descargo(), List.of());
    }

    private SancionDTO resolverApelacion(Sancion sancion, String apelacionId, ResultadoApelacion resultado) {
        return reportes.resolverApelacion(admin.getId(), sancion.getId(), apelacionId,
                new ResolucionApelacionEntradaDTO(resultado, "Revisadas las evidencias"));
    }

    private void aviso(Usuario a, TipoNotificacion tipo) {
        verify(e.notificaciones, atLeastOnce()).notificar(eq(a.getId()), eq(tipo), anyString(), anyString(), anyString());
    }

    // ---------------------------------------------------------------- radicación

    @Test
    void radicarCongelaElPagoPoneElServicioEnRevisionYAvisa() {
        ReporteDTO r = radicarInasistencia();

        assertEquals(EstadoReporte.EN_REVISION, r.estado());
        assertEquals(TipoReporte.INASISTENCIA, r.tipo());
        assertEquals("INCUMPLIMIENTO_SERVICIO", r.categoria().name());
        assertEquals(carlos.getId(), r.reportante().id());
        assertEquals("Carlos Gómez", r.reportante().nombre());
        assertEquals(laura.getId(), r.denunciado().id());
        assertEquals(servicio.getId(), r.servicioId());
        assertNull(r.resolucion());
        assertEquals(EstadoPago.CONGELADO, pago().getEstado());
        assertEquals(EstadoServicio.EN_REVISION, servicio.getEstado());
        assertEquals(EstadoReporte.EN_REVISION, e.reportes.get(r.id()).getEstado());
        assertTrue(e.acciones().containsAll(List.of("RADICAR_REPORTE", "PAGO_CONGELADO")));
        aviso(laura, TipoNotificacion.REPORTE_RECIBIDO);
        aviso(admin, TipoNotificacion.REPORTE_RECIBIDO);
    }

    @Test
    void radicarBloqueaLaFinalizacionYElCierreAutomatico() {
        radicarInasistencia();

        assertNegocio("SERVICIO_EN_REVISION", HttpStatus.CONFLICT,
                () -> e.servicioNegociacion.finalizarServicio(carlos.getId(), servicio.getId()));
        assertEquals(EstadoPago.CONGELADO, pago().getEstado());
        servicio.setFechaHora(Instant.now().minus(Duration.ofDays(3)));
        assertEquals(0, e.servicioNegociacion.cerrarAutomaticamente(true).cerrados());
    }

    @Test
    void tambienCongelaUnServicioSoloConfirmadoYElTutorPuedeReportarAlEstudiante() {
        ServicioTutoria confirmado = e.crearServicio(carlos, laura, EstadoServicio.CONFIRMADO,
                Instant.now().plus(Duration.ofDays(1)), "30000");
        ReporteDTO r = radicar(laura, new ReporteEntradaDTO(TipoReporte.COBRO_EXTERNO,
                "El estudiante pidió pagarle por fuera de la plataforma", null, confirmado.getId()));

        assertEquals(carlos.getId(), r.denunciado().id());
        assertEquals(EstadoServicio.EN_REVISION, confirmado.getEstado());
        assertEquals(EstadoPago.CONGELADO, e.pagos.values().stream()
                .filter(p -> p.getServicioId().equals(confirmado.getId())).findFirst().orElseThrow().getEstado());
    }

    @Test
    void unServicioFinalizadoConElPagoYaLiberadoSeReportaSinCongelarNada() {
        e.servicioNegociacion.finalizarServicio(carlos.getId(), servicio.getId());
        assertEquals(EstadoPago.LIBERADO, pago().getEstado());

        ReporteDTO r = radicarInasistencia();

        assertEquals(EstadoReporte.EN_REVISION, r.estado());
        assertEquals(EstadoServicio.FINALIZADO, servicio.getEstado());
        assertEquals(EstadoPago.LIBERADO, pago().getEstado());
        assertFalse(e.acciones().contains("PAGO_CONGELADO"));
    }

    @Test
    void unServicioFinalizadoHaceMenosDe24HorasConElPagoRetenidoSoloCongelaElPago() {
        servicio.setEstado(EstadoServicio.FINALIZADO);
        servicio.setFechaFinalizacion(Instant.now().minus(Duration.ofHours(2)));

        radicarInasistencia();

        assertEquals(EstadoServicio.FINALIZADO, servicio.getEstado());
        assertEquals(EstadoPago.CONGELADO, pago().getEstado());
    }

    @Test
    void unServicioFinalizadoHaceMasDe24HorasNoSeCongela() {
        servicio.setEstado(EstadoServicio.FINALIZADO);
        servicio.setFechaFinalizacion(Instant.now().minus(Duration.ofHours(30)));

        radicarInasistencia();

        assertEquals(EstadoPago.RETENIDO, pago().getEstado());
    }

    @Test
    void unSegundoReporteSobreUnServicioYaEnRevisionNoVuelveACongelar() {
        radicarInasistencia();
        ReporteDTO segundo = radicar(laura, new ReporteEntradaDTO(TipoReporte.DESACUERDO_ACADEMICO,
                "El estudiante desconoce lo acordado sobre el tema", null, servicio.getId()));

        assertEquals(EstadoReporte.EN_REVISION, segundo.estado());
        assertEquals(EstadoPago.CONGELADO, pago().getEstado());
        assertEquals(EstadoServicio.EN_REVISION, servicio.getEstado());
    }

    @Test
    void reportaAUnUsuarioSinServicioParaConductasGenerales() {
        ReporteDTO r = radicar(carlos, new ReporteEntradaDTO(TipoReporte.ACOSO,
                "Me escribió mensajes insistentes e incómodos", laura.getId(), null));

        assertEquals(EstadoReporte.EN_REVISION, r.estado());
        assertEquals("CONDUCTA_ABUSIVA", r.categoria().name());
        assertNull(r.servicioId());
        assertEquals(EstadoPago.RETENIDO, pago().getEstado());
        assertEquals(EstadoServicio.EN_DESARROLLO, servicio.getEstado());
    }

    @Test
    void guardaLasEvidenciasEnGridFsYLasLigaAlReporte() {
        ReporteDTO r = reportes.radicarReporte(autenticado(carlos), inasistencia(),
                List.of(evidencia("captura1.pdf"), evidencia("captura2.pdf")));

        assertEquals(2, r.evidencias().size());
        assertEquals(2, e.almacenamiento.cantidad());
        assertTrue(r.evidencias().stream().allMatch(e.almacenamiento::contiene));
        assertEquals(r.evidencias(), e.reportes.get(r.id()).getEvidencias());
    }

    @Test
    void rechazaMasDeCincoEvidenciasSinSubirNada() {
        List<MockMultipartFile> seis = java.util.stream.IntStream.range(0, 6).mapToObj(i -> evidencia("e" + i + ".pdf")).toList();

        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST,
                () -> reportes.radicarReporte(autenticado(carlos), inasistencia(), List.copyOf(seis)));
        assertEquals(0, e.almacenamiento.cantidad());
        assertTrue(e.reportes.isEmpty());
        assertEquals(EstadoPago.RETENIDO, pago().getEstado());
    }

    @Test
    void unaEvidenciaConFormatoNoPermitidoNoDejaNadaGuardado() {
        MockMultipartFile exe = new MockMultipartFile("evidencias", "virus.exe", "application/octet-stream", new byte[] {1, 2, 3});

        ExcepcionNegocio ex = assertThrows(ExcepcionNegocio.class, () -> reportes.radicarReporte(autenticado(carlos),
                inasistencia(), List.of(evidencia("ok.pdf"), exe)));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getEstado());
        assertEquals(0, e.almacenamiento.cantidad()); // la primera se subió y se borró
        assertTrue(e.reportes.isEmpty());
        assertEquals(EstadoPago.RETENIDO, pago().getEstado());
    }

    @Test
    void validaElMotivoYElTipo() {
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> radicar(carlos,
                new ReporteEntradaDTO(TipoReporte.INASISTENCIA, "corto", null, servicio.getId())));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> radicar(carlos,
                new ReporteEntradaDTO(TipoReporte.INASISTENCIA, "  ", null, servicio.getId())));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> radicar(carlos,
                new ReporteEntradaDTO(TipoReporte.INASISTENCIA, "x".repeat(1001), null, servicio.getId())));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> radicar(carlos,
                new ReporteEntradaDTO(null, "Motivo suficientemente largo", null, servicio.getId())));
        assertTrue(e.reportes.isEmpty());
    }

    @Test
    void unTipoQueExigeServicioNoSeRadicaSinEl() {
        assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY, () -> radicar(carlos,
                new ReporteEntradaDTO(TipoReporte.INASISTENCIA, "El tutor no se presentó a la sesión", laura.getId(), null)));
        assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY, () -> radicar(carlos,
                new ReporteEntradaDTO(TipoReporte.COBRO_EXTERNO, "Me pidió pagar por fuera de la app", laura.getId(), null)));
        assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY, () -> radicar(carlos,
                new ReporteEntradaDTO(TipoReporte.DESACUERDO_ACADEMICO, "No cubrió el tema acordado", laura.getId(), null)));
    }

    @Test
    void sinServicioElDenunciadoEsObligatorio() {
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> radicar(carlos,
                new ReporteEntradaDTO(TipoReporte.ACOSO, "Me escribió mensajes insistentes", null, null)));
    }

    @Test
    void nadieSePuedeReportarASiMismo() {
        assertThrows(ExcepcionAutorizacion.class, () -> radicar(carlos,
                new ReporteEntradaDTO(TipoReporte.ACOSO, "Me reporto a mí mismo, error", carlos.getId(), null)));
        assertThrows(ExcepcionAutorizacion.class, () -> radicar(carlos,
                new ReporteEntradaDTO(TipoReporte.INASISTENCIA, "Me reporto a mí mismo, error", carlos.getId(), servicio.getId())));
        assertTrue(e.reportes.isEmpty());
    }

    @Test
    void soloLosParticipantesReportanUnServicio() {
        assertThrows(ExcepcionAutorizacion.class, () -> radicar(intruso, inasistencia()));
        assertEquals(EstadoPago.RETENIDO, pago().getEstado());
    }

    @Test
    void elDenunciadoDebeSerLaOtraParteDelServicio() {
        assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY, () -> radicar(carlos,
                new ReporteEntradaDTO(TipoReporte.INASISTENCIA, "El tutor no se presentó a la sesión", intruso.getId(), servicio.getId())));
        // Indicarlo correctamente es válido.
        assertEquals(laura.getId(), radicar(carlos, new ReporteEntradaDTO(TipoReporte.INASISTENCIA,
                "El tutor no se presentó a la sesión", laura.getId(), servicio.getId())).denunciado().id());
    }

    @Test
    void servicioODenunciadoInexistentesDan404() {
        assertThrows(ExcepcionNoEncontrado.class, () -> radicar(carlos,
                new ReporteEntradaDTO(TipoReporte.INASISTENCIA, "El tutor no se presentó a la sesión", null, "no-existe")));
        assertThrows(ExcepcionNoEncontrado.class, () -> radicar(carlos,
                new ReporteEntradaDTO(TipoReporte.ACOSO, "Me escribió mensajes insistentes", "no-existe", null)));
    }

    @Test
    void noSePuedeReportarAUnAdministrador() {
        assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY, () -> radicar(carlos,
                new ReporteEntradaDTO(TipoReporte.ACOSO, "Me escribió mensajes insistentes", admin.getId(), null)));
    }

    @Test
    void unReporteIgualYaAbiertoDa409() {
        radicarInasistencia();

        assertNegocio("REPORTE_DUPLICADO", HttpStatus.CONFLICT, this::radicarInasistencia);
        assertEquals(1, e.reportes.size());
    }

    @Test
    void unaCuentaSuspendidaNoPuedeRadicar() {
        carlos.setEstadoCuenta(EstadoCuenta.SUSPENDIDA);

        assertThrows(ExcepcionAutorizacion.class, this::radicarInasistencia);
    }

    @Test
    void siGuardarElReporteFallaSeBorranLasEvidenciasYNoSeCongelaNada() {
        when(e.repoReportes.save(any(Reporte.class))).thenThrow(new IllegalStateException("fallo de base de datos"));

        assertThrows(IllegalStateException.class, () -> reportes.radicarReporte(autenticado(carlos), inasistencia(),
                List.of(evidencia("captura.pdf"))));

        assertEquals(0, e.almacenamiento.cantidad());
        assertEquals(EstadoPago.RETENIDO, pago().getEstado());
        assertEquals(EstadoServicio.EN_DESARROLLO, servicio.getEstado());
    }

    @Test
    void siFallaAlCongelarSeCompensaElReporteElPagoYElServicio() {
        when(e.repoServicios.save(any(ServicioTutoria.class))).thenThrow(new IllegalStateException("fallo de base de datos"));

        assertThrows(IllegalStateException.class, () -> reportes.radicarReporte(autenticado(carlos), inasistencia(),
                List.of(evidencia("captura.pdf"))));

        assertTrue(e.reportes.isEmpty());
        assertEquals(EstadoPago.RETENIDO, pago().getEstado());
        assertEquals(EstadoServicio.EN_DESARROLLO, servicio.getEstado());
        assertEquals(0, e.almacenamiento.cantidad());
        assertTrue(e.acciones().contains("PAGO_DESCONGELADO"));
    }

    @Test
    void unaNotificacionQueFallaNoRevierteLaRadicacion() {
        org.mockito.Mockito.doThrow(new IllegalStateException("sin notificador")).when(e.notificaciones)
                .notificar(anyString(), any(), anyString(), anyString(), anyString());

        ReporteDTO r = radicarInasistencia();

        assertEquals(EstadoReporte.EN_REVISION, r.estado());
        assertEquals(EstadoPago.CONGELADO, pago().getEstado());
    }

    // ---------------------------------------------------------------- «mis reportes»

    @Test
    void misReportesSeparaRadicadosYRecibidosYOcultaAlReportanteDelDenunciado() {
        reportes.radicarReporte(autenticado(carlos), inasistencia(), List.of(evidencia("captura.pdf")));

        ReportesMiosDTO deCarlos = reportes.listarMios(carlos.getId());
        ReportesMiosDTO deLaura = reportes.listarMios(laura.getId());

        assertEquals(1, deCarlos.radicados().size());
        assertTrue(deCarlos.recibidos().isEmpty());
        assertEquals("Carlos Gómez", deCarlos.radicados().get(0).reportante().nombre());
        assertEquals(1, deCarlos.radicados().get(0).evidencias().size());
        assertTrue(deLaura.radicados().isEmpty());
        assertEquals(1, deLaura.recibidos().size());
        ReporteDTO recibido = deLaura.recibidos().get(0);
        assertNull(recibido.reportante());
        assertTrue(recibido.evidencias().isEmpty());
        assertEquals("Laura Martínez", recibido.denunciado().nombre());
        assertEquals(TipoReporte.INASISTENCIA, recibido.tipo());
    }

    // ---------------------------------------------------------------- resolución

    @Test
    void reembolsarCancelaElServicioYDevuelveElMontoCompleto() {
        ReporteDTO r = radicarInasistencia();

        ReporteDTO resuelto = resolver(r.id(), DecisionReporte.A_FAVOR_REPORTANTE, AccionPago.REEMBOLSAR, null);

        assertEquals(EstadoReporte.RESUELTO, resuelto.estado());
        assertEquals(DecisionReporte.A_FAVOR_REPORTANTE, resuelto.resolucion().decision());
        assertEquals(AccionPago.REEMBOLSAR, resuelto.resolucion().accionPago());
        assertEquals(admin.getId(), resuelto.resolucion().resueltoPor().id());
        assertEquals(EstadoPago.REEMBOLSADO, pago().getEstado());
        assertEquals(0, pago().getMontoNetoCOP().signum());
        assertEquals(EstadoServicio.CANCELADO, servicio.getEstado());
        assertTrue(e.acciones().containsAll(List.of("RESOLVER_REPORTE", "PAGO_REEMBOLSADO")));
        aviso(carlos, TipoNotificacion.REPORTE_RESUELTO);
        aviso(laura, TipoNotificacion.REPORTE_RESUELTO);
    }

    @Test
    void liberarFinalizaElServicioYLiquidaAlTutor() {
        ReporteDTO r = radicar(laura, new ReporteEntradaDTO(TipoReporte.DESACUERDO_ACADEMICO,
                "El estudiante dice que no vio el tema pero sí se dictó completo", null, servicio.getId()));

        ReporteDTO resuelto = resolver(r.id(), DecisionReporte.A_FAVOR_REPORTANTE, AccionPago.LIBERAR, null);

        assertEquals(EstadoReporte.RESUELTO, resuelto.estado());
        assertEquals(EstadoPago.LIBERADO, pago().getEstado());
        assertEquals(0, new java.math.BigDecimal("4000").compareTo(pago().getComisionCOP()));
        assertEquals(0, new java.math.BigDecimal("36000").compareTo(pago().getMontoNetoCOP()));
        assertEquals(EstadoServicio.FINALIZADO, servicio.getEstado());
        assertNotNull(servicio.getFechaFinalizacion());
        assertEquals(1, laura.getPerfilTutor().getServiciosCompletados());
        assertTrue(e.acciones().contains("PAGO_LIBERADO"));
        aviso(laura, TipoNotificacion.PAGO_LIBERADO);
        verify(e.notificaciones).notificar(eq(carlos.getId()), eq(TipoNotificacion.CALIFICACION_PENDIENTE), anyString(), anyString(), anyString());
    }

    @Test
    void desestimarReanudaElServicioYDescongelaElPago() {
        ReporteDTO r = radicarInasistencia();

        ReporteDTO resuelto = resolver(r.id(), DecisionReporte.DESESTIMADO, AccionPago.NINGUNA, null);

        assertEquals(EstadoReporte.DESESTIMADO, resuelto.estado());
        assertEquals(EstadoServicio.EN_DESARROLLO, servicio.getEstado());
        assertEquals(EstadoPago.RETENIDO, pago().getEstado());
        assertTrue(e.acciones().contains("PAGO_DESCONGELADO"));
        // y ahora sí se puede finalizar
        assertEquals(EstadoPago.LIBERADO, e.servicioNegociacion.finalizarServicio(carlos.getId(), servicio.getId()).estado());
    }

    @Test
    void aFavorDelDenunciadoSinAccionDePagoReanudaUnServicioConfirmado() {
        ServicioTutoria confirmado = e.crearServicio(carlos, laura, EstadoServicio.CONFIRMADO,
                Instant.now().plus(Duration.ofDays(1)), "30000");
        ReporteDTO r = radicar(carlos, new ReporteEntradaDTO(TipoReporte.DESACUERDO_ACADEMICO,
                "No estoy de acuerdo con el tema acordado", null, confirmado.getId()));

        resolver(r.id(), DecisionReporte.A_FAVOR_DENUNCIADO, null, null);

        assertEquals(EstadoServicio.CONFIRMADO, confirmado.getEstado());
        assertEquals(EstadoPago.RETENIDO, e.pagos.values().stream()
                .filter(p -> p.getServicioId().equals(confirmado.getId())).findFirst().orElseThrow().getEstado());
    }

    @Test
    void conOtroReporteAbiertoElServicioSigueCongeladoHastaResolverlo() {
        ReporteDTO primero = radicarInasistencia();
        ReporteDTO segundo = radicar(laura, new ReporteEntradaDTO(TipoReporte.DESACUERDO_ACADEMICO,
                "El estudiante desconoce lo acordado sobre el tema", null, servicio.getId()));

        resolver(primero.id(), DecisionReporte.DESESTIMADO, AccionPago.NINGUNA, null);
        assertEquals(EstadoServicio.EN_REVISION, servicio.getEstado());
        assertEquals(EstadoPago.CONGELADO, pago().getEstado());

        resolver(segundo.id(), DecisionReporte.DESESTIMADO, AccionPago.NINGUNA, null);
        assertEquals(EstadoServicio.EN_DESARROLLO, servicio.getEstado());
        assertEquals(EstadoPago.RETENIDO, pago().getEstado());
    }

    @Test
    void unReporteDesestimadoNoPuedeOrdenarMovimientosDePago() {
        ReporteDTO r = radicarInasistencia();

        assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY,
                () -> resolver(r.id(), DecisionReporte.DESESTIMADO, AccionPago.REEMBOLSAR, null));

        assertEquals(EstadoReporte.EN_REVISION, e.reportes.get(r.id()).getEstado());
        assertEquals(EstadoPago.CONGELADO, pago().getEstado());
    }

    @Test
    void laAccionDePagoExigeUnReporteLigadoAUnServicio() {
        ReporteDTO r = radicar(carlos, new ReporteEntradaDTO(TipoReporte.ACOSO, "Me escribió mensajes insistentes", laura.getId(), null));

        assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY,
                () -> resolver(r.id(), DecisionReporte.A_FAVOR_REPORTANTE, AccionPago.REEMBOLSAR, null));
        assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY,
                () -> resolver(r.id(), DecisionReporte.A_FAVOR_REPORTANTE, AccionPago.LIBERAR, null));
        // sin acción de pago sí se resuelve
        assertEquals(EstadoReporte.RESUELTO,
                resolver(r.id(), DecisionReporte.A_FAVOR_REPORTANTE, AccionPago.NINGUNA, null).estado());
    }

    @Test
    void noSeReembolsaUnPagoQueNoEstaCongelado() {
        e.servicioNegociacion.finalizarServicio(carlos.getId(), servicio.getId()); // pago LIBERADO
        ReporteDTO r = radicarInasistencia();

        assertNegocio("PAGO_NO_REEMBOLSABLE", HttpStatus.CONFLICT,
                () -> resolver(r.id(), DecisionReporte.A_FAVOR_REPORTANTE, AccionPago.REEMBOLSAR, null));
        assertNegocio("PAGO_NO_LIBERABLE", HttpStatus.CONFLICT,
                () -> resolver(r.id(), DecisionReporte.A_FAVOR_DENUNCIADO, AccionPago.LIBERAR, null));

        assertEquals(EstadoReporte.EN_REVISION, e.reportes.get(r.id()).getEstado());
        assertEquals(EstadoPago.LIBERADO, pago().getEstado());
    }

    @Test
    void unReporteSeResuelveUnaSolaVez() {
        ReporteDTO r = radicarInasistencia();
        resolver(r.id(), DecisionReporte.A_FAVOR_REPORTANTE, AccionPago.REEMBOLSAR, null);

        assertNegocio("REPORTE_YA_RESUELTO", HttpStatus.CONFLICT,
                () -> resolver(r.id(), DecisionReporte.DESESTIMADO, AccionPago.NINGUNA, null));
        assertEquals(EstadoPago.REEMBOLSADO, pago().getEstado());
    }

    @Test
    void laResolucionExigeDecisionYObservacion() {
        ReporteDTO r = radicarInasistencia();

        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> reportes.resolverReporte(admin.getId(), r.id(),
                new ResolucionEntradaDTO(DecisionReporte.DESESTIMADO, null, "  ", null)));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> reportes.resolverReporte(admin.getId(), r.id(),
                new ResolucionEntradaDTO(null, null, "Observación", null)));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> reportes.resolverReporte(admin.getId(), r.id(),
                new ResolucionEntradaDTO(DecisionReporte.DESESTIMADO, null, "x".repeat(1001), null)));
        assertThrows(ExcepcionNoEncontrado.class, () -> resolver("no-existe", DecisionReporte.DESESTIMADO, null, null));
        assertEquals(EstadoReporte.EN_REVISION, e.reportes.get(r.id()).getEstado());
    }

    // ---- sanciones desde la resolución ----

    @Test
    void sancionarConSuspensionTemporalSuspendeLaCuentaYDejaUnaSancionApelable() {
        Sancion s = sancionarALaura(suspension(14));

        assertEquals(TipoSancion.SUSPENSION_TEMPORAL, s.getTipo());
        assertEquals(EstadoSancion.VIGENTE, s.getEstado());
        assertEquals(laura.getId(), s.getUsuarioId());
        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta());
        assertEquals(EstadoCuenta.ACTIVA, carlos.getEstadoCuenta());
        assertTrue(e.acciones().contains("SANCION_APLICADA"));
        aviso(laura, TipoNotificacion.SANCION_APLICADA);
        assertEquals(EstadoReporte.RESUELTO, e.reportes.get(s.getReporteId()).getEstado());
    }

    @Test
    void sancionarConBloqueoPermanenteYConAdvertencia() {
        Sancion b = sancionarALaura(bloqueo());
        assertEquals(EstadoCuenta.BLOQUEADA, laura.getEstadoCuenta());
        assertNull(b.getVigencia().getFin());

        Usuario otro = e.crearUsuario("Otro Tutor", Rol.TUTOR);
        ServicioTutoria otroServicio = e.crearServicio(carlos, otro, EstadoServicio.EN_DESARROLLO, Instant.now().minusSeconds(60), "20000");
        ReporteDTO r = radicar(carlos, new ReporteEntradaDTO(TipoReporte.COBRO_EXTERNO,
                "Me pidió pagar por fuera de la plataforma", null, otroServicio.getId()));
        resolver(r.id(), DecisionReporte.A_FAVOR_REPORTANTE, AccionPago.REEMBOLSAR, advertencia());
        assertEquals(EstadoCuenta.ACTIVA, otro.getEstadoCuenta());
        assertEquals(2, e.sanciones.size());
    }

    @Test
    void soloSeSancionaCuandoLaDecisionEsAFavorDelReportante() {
        ReporteDTO r = radicarInasistencia();

        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST,
                () -> resolver(r.id(), DecisionReporte.A_FAVOR_DENUNCIADO, AccionPago.NINGUNA, suspension(7)));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST,
                () -> resolver(r.id(), DecisionReporte.DESESTIMADO, AccionPago.NINGUNA, advertencia()));
        assertTrue(e.sanciones.isEmpty());
        assertEquals(EstadoReporte.EN_REVISION, e.reportes.get(r.id()).getEstado());
    }

    @Test
    void unaSancionInvalidaNoCambiaNada() {
        ReporteDTO r = radicarInasistencia();

        assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY, () -> resolver(r.id(),
                DecisionReporte.A_FAVOR_REPORTANTE, AccionPago.REEMBOLSAR,
                new SancionEntradaDTO(TipoSancion.SUSPENSION_TEMPORAL, null, Instant.now().minus(Duration.ofDays(1)))));
        assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY, () -> resolver(r.id(),
                DecisionReporte.A_FAVOR_REPORTANTE, AccionPago.REEMBOLSAR,
                new SancionEntradaDTO(TipoSancion.SUSPENSION_TEMPORAL, null, null)));

        assertEquals(EstadoReporte.EN_REVISION, e.reportes.get(r.id()).getEstado());
        assertEquals(EstadoPago.CONGELADO, pago().getEstado());
        assertEquals(EstadoServicio.EN_REVISION, servicio.getEstado());
        assertEquals(EstadoCuenta.ACTIVA, laura.getEstadoCuenta());
        assertTrue(e.sanciones.isEmpty());
    }

    @Test
    void siFallaElPagoSeCompensaLaSancionLaCuentaYElReporte() {
        ReporteDTO r = radicarInasistencia();
        when(e.repoPagos.save(any(Pago.class))).thenThrow(new IllegalStateException("fallo de base de datos"));

        assertThrows(IllegalStateException.class,
                () -> resolver(r.id(), DecisionReporte.A_FAVOR_REPORTANTE, AccionPago.REEMBOLSAR, suspension(14)));

        assertTrue(e.sanciones.isEmpty());
        assertEquals(EstadoCuenta.ACTIVA, laura.getEstadoCuenta());
        Reporte almacenado = e.reportes.get(r.id());
        assertEquals(EstadoReporte.EN_REVISION, almacenado.getEstado());
        assertNull(almacenado.getResolucion());
    }

    // ---- expediente y colas ----

    @Test
    void elExpedienteIncluyeServicioPagoChatYSanciones() {
        servicio.setSolicitudId("solicitud-1");
        Instant t0 = Instant.now().minus(Duration.ofMinutes(30));
        Mensaje m1 = Mensaje.nuevo("solicitud-1", laura.getId(), carlos.getId(), "¿Estás conectada?", null, t0);
        Mensaje m2 = Mensaje.nuevo("solicitud-1", laura.getId(), laura.getId(), "Perdón, no podré asistir", null, t0.plusSeconds(60));
        Mensaje otroHilo = Mensaje.nuevo("solicitud-1", intruso.getId(), carlos.getId(), "Otro hilo", null, t0);
        for (Mensaje m : List.of(m2, m1, otroHilo)) {
            m.setId(EscenarioNegociacion.nuevoId());
            e.mensajes.put(m.getId(), m);
        }
        ReporteDTO r = radicarInasistencia();
        resolver(r.id(), DecisionReporte.A_FAVOR_REPORTANTE, AccionPago.REEMBOLSAR, advertencia());

        ExpedienteReporteDTO exp = reportes.consultarExpediente(r.id());

        assertEquals(r.id(), exp.reporte().id());
        assertEquals(EstadoReporte.RESUELTO, exp.reporte().estado());
        assertEquals(servicio.getId(), exp.servicio().id());
        assertEquals("Laura Martínez", exp.servicio().tutor().nombre());
        assertEquals(EstadoPago.REEMBOLSADO, exp.pago().estado());
        assertEquals(2, exp.historialChat().size());
        assertEquals("¿Estás conectada?", exp.historialChat().get(0).contenido());
        assertEquals("Perdón, no podré asistir", exp.historialChat().get(1).contenido());
        assertEquals(1, exp.sanciones().size());
        assertEquals(TipoSancion.ADVERTENCIA, exp.sanciones().get(0).tipo());
        assertEquals(r.id(), exp.sanciones().get(0).reporteId());
    }

    @Test
    void elExpedienteDeUnReporteSinServicioNoTraeServicioNiChat() {
        ReporteDTO r = radicar(carlos, new ReporteEntradaDTO(TipoReporte.ACOSO, "Me escribió mensajes insistentes", laura.getId(), null));

        ExpedienteReporteDTO exp = reportes.consultarExpediente(r.id());

        assertNull(exp.servicio());
        assertNull(exp.pago());
        assertTrue(exp.historialChat().isEmpty());
        assertTrue(exp.sanciones().isEmpty());
        assertThrows(ExcepcionNoEncontrado.class, () -> reportes.consultarExpediente("no-existe"));
    }

    @Test
    void laColaDeReportesSePuedeFiltrarPorEstado() {
        ReporteDTO a = radicarInasistencia();
        ReporteDTO b = radicar(carlos, new ReporteEntradaDTO(TipoReporte.ACOSO, "Me escribió mensajes insistentes", laura.getId(), null));
        resolver(b.id(), DecisionReporte.DESESTIMADO, AccionPago.NINGUNA, null);

        Pagina<ReporteDTO> todos = reportes.listarReportes(null, 0, 20);
        Pagina<ReporteDTO> enRevision = reportes.listarReportes(EstadoReporte.EN_REVISION, 0, 20);
        Pagina<ReporteDTO> desestimados = reportes.listarReportes(EstadoReporte.DESESTIMADO, 0, 20);

        assertEquals(2, todos.totalElementos());
        assertEquals(List.of(a.id()), enRevision.contenido().stream().map(ReporteDTO::id).toList());
        assertEquals(List.of(b.id()), desestimados.contenido().stream().map(ReporteDTO::id).toList());
        assertEquals("Carlos Gómez", enRevision.contenido().get(0).reportante().nombre());
        assertEquals("Administrador", desestimados.contenido().get(0).resolucion().resueltoPor().nombre());
    }

    // ---------------------------------------------------------------- apelación

    @Test
    void elSancionadoApelaConDescargoYEvidencias() {
        Sancion s = sancionarALaura(suspension(14));
        UsuarioAutenticado deLaura = new UsuarioAutenticado(laura.getId(), laura.getCorreo(), laura.getNombre(), laura.getRoles(), true);
        laura.setEstadoCuenta(EstadoCuenta.SUSPENDIDA);

        ApelacionDTO a = reportes.apelarSancion(deLaura, s.getId(), descargo(),
                List.of(evidencia("incapacidad.pdf"), evidencia("historia.pdf")));

        assertEquals(ResultadoApelacion.PENDIENTE, a.resultado());
        assertEquals(2, a.evidencias().size());
        assertTrue(a.evidencias().stream().allMatch(e.almacenamiento::contiene));
        assertNotNull(a.fechaPresentacion());
        assertEquals(EstadoSancion.EN_APELACION, e.sanciones.get(s.getId()).getEstado());
        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta()); // sigue suspendida mientras se resuelve
        assertTrue(e.acciones().contains("APELACION_PRESENTADA"));
        aviso(admin, TipoNotificacion.REPORTE_RECIBIDO);
    }

    @Test
    void unaAdvertenciaTambienSePuedeApelar() {
        Sancion s = sancionarALaura(advertencia());

        apelar(laura, s);

        assertEquals(EstadoSancion.EN_APELACION, e.sanciones.get(s.getId()).getEstado());
    }

    @Test
    void unaSegundaApelacionPendienteDa409YNoSubeArchivos() {
        Sancion s = sancionarALaura(suspension(14));
        apelar(laura, s);
        int archivos = e.almacenamiento.cantidad();

        assertNegocio("APELACION_NO_PERMITIDA", HttpStatus.CONFLICT, () -> reportes.apelarSancion(
                autenticado(laura), s.getId(), descargo(), List.of(evidencia("otra.pdf"))));

        assertEquals(archivos, e.almacenamiento.cantidad());
        assertEquals(1, e.sanciones.get(s.getId()).getApelaciones().size());
    }

    @Test
    void soloSePuedeApelarUnaSancionVigente() {
        Sancion ratificada = sancionarALaura(suspension(14));
        ApelacionDTO a = apelar(laura, ratificada);
        resolverApelacion(ratificada, a.id(), ResultadoApelacion.RATIFICADA);

        assertNegocio("APELACION_NO_PERMITIDA", HttpStatus.CONFLICT, () -> apelar(laura, ratificada));

        Sancion cumplida = new Sancion();
        cumplida.setId(EscenarioNegociacion.nuevoId());
        cumplida.setUsuarioId(laura.getId());
        cumplida.setTipo(TipoSancion.ADVERTENCIA);
        cumplida.setEstado(EstadoSancion.CUMPLIDA);
        e.sanciones.put(cumplida.getId(), cumplida);
        assertNegocio("APELACION_NO_PERMITIDA", HttpStatus.CONFLICT, () -> apelar(laura, cumplida));
    }

    @Test
    void soloElSancionadoApelaYLaSancionDebeExistir() {
        Sancion s = sancionarALaura(suspension(14));

        assertThrows(ExcepcionAutorizacion.class, () -> apelar(carlos, s));
        assertThrows(ExcepcionAutorizacion.class, () -> apelar(intruso, s));
        assertThrows(ExcepcionNoEncontrado.class, () -> reportes.apelarSancion(autenticado(laura), "no-existe", descargo(), List.of()));
        assertEquals(EstadoSancion.VIGENTE, e.sanciones.get(s.getId()).getEstado());
    }

    @Test
    void validaElDescargoYLasEvidenciasDeLaApelacion() {
        Sancion s = sancionarALaura(suspension(14));

        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> reportes.apelarSancion(autenticado(laura), s.getId(),
                new ApelacionEntradaDTO("corto"), List.of()));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> reportes.apelarSancion(autenticado(laura), s.getId(),
                new ApelacionEntradaDTO("   "), List.of()));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> reportes.apelarSancion(autenticado(laura), s.getId(),
                new ApelacionEntradaDTO("x".repeat(2001)), List.of()));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> reportes.apelarSancion(autenticado(laura), s.getId(), null, List.of()));
        List<MockMultipartFile> seis = java.util.stream.IntStream.range(0, 6).mapToObj(i -> evidencia("e" + i + ".pdf")).toList();
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> reportes.apelarSancion(autenticado(laura), s.getId(),
                descargo(), List.copyOf(seis)));
        assertEquals(EstadoSancion.VIGENTE, e.sanciones.get(s.getId()).getEstado());
        assertEquals(0, e.almacenamiento.cantidad());
    }

    @Test
    void siGuardarLaApelacionFallaSeBorranLasEvidencias() {
        Sancion s = sancionarALaura(suspension(14));
        when(e.repoSanciones.save(any(Sancion.class))).thenThrow(new IllegalStateException("fallo de base de datos"));

        assertThrows(IllegalStateException.class, () -> reportes.apelarSancion(autenticado(laura), s.getId(), descargo(),
                List.of(evidencia("incapacidad.pdf"))));

        assertEquals(0, e.almacenamiento.cantidad());
    }

    @Test
    void misSancionesMuestranElMotivoSinRevelarAlReportante() {
        Sancion s = sancionarALaura(suspension(14));
        apelar(laura, s);
        sancionarALauraConAdvertenciaAparte();

        List<SancionDTO> mias = reportes.listarSancionesMias(laura.getId());

        assertEquals(2, mias.size());
        SancionDTO apelada = mias.stream().filter(x -> x.id().equals(s.getId())).findFirst().orElseThrow();
        assertEquals(EstadoSancion.EN_APELACION, apelada.estado());
        assertFalse(apelada.puedeApelar());
        assertEquals(TipoReporte.INASISTENCIA, apelada.tipoReporte());
        assertTrue(apelada.motivo().contains("20 minutos"));
        assertEquals(1, apelada.apelaciones().size());
        assertNotNull(apelada.vigencia().fin());
        SancionDTO nueva = mias.stream().filter(x -> !x.id().equals(s.getId())).findFirst().orElseThrow();
        assertTrue(nueva.puedeApelar());
        assertTrue(reportes.listarSancionesMias(carlos.getId()).isEmpty());
    }

    private void sancionarALauraConAdvertenciaAparte() {
        ReporteDTO r = radicar(carlos, new ReporteEntradaDTO(TipoReporte.ACOSO, "Me escribió mensajes insistentes", laura.getId(), null));
        resolver(r.id(), DecisionReporte.A_FAVOR_REPORTANTE, AccionPago.NINGUNA, advertencia());
    }

    // ---------------------------------------------------------------- resolución de apelaciones

    @Test
    void revocarLaApelacionDejaLaSancionSinEfectoYReactivaLaCuenta() {
        Sancion s = sancionarALaura(suspension(14));
        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta());
        ApelacionDTO a = apelar(laura, s);

        SancionDTO resuelta = resolverApelacion(s, a.id(), ResultadoApelacion.REVOCADA);

        assertEquals(EstadoSancion.REVOCADA, resuelta.estado());
        assertEquals(ResultadoApelacion.REVOCADA, resuelta.apelaciones().get(0).resultado());
        assertEquals("Revisadas las evidencias", resuelta.apelaciones().get(0).observacionResolucion());
        assertNotNull(resuelta.apelaciones().get(0).fechaResolucion());
        assertEquals(admin.getId(), e.sanciones.get(s.getId()).getApelaciones().get(0).getResueltaPor());
        assertEquals(EstadoCuenta.ACTIVA, laura.getEstadoCuenta());
        assertTrue(e.acciones().contains("APELACION_RESUELTA"));
        aviso(laura, TipoNotificacion.APELACION_RESUELTA);
    }

    @Test
    void revocarUnBloqueoPermanenteReactivaLaCuenta() {
        Sancion s = sancionarALaura(bloqueo());
        assertEquals(EstadoCuenta.BLOQUEADA, laura.getEstadoCuenta());

        resolverApelacion(s, apelar(laura, s).id(), ResultadoApelacion.REVOCADA);

        assertEquals(EstadoCuenta.ACTIVA, laura.getEstadoCuenta());
    }

    @Test
    void revocarUnaSancionNoReactivaSiOtraSigueVigente() {
        Sancion primera = sancionarALaura(suspension(14));
        Sancion segunda = sancionarALauraConSegundoServicio(suspension(30));
        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta());

        resolverApelacion(primera, apelar(laura, primera).id(), ResultadoApelacion.REVOCADA);

        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta());
        assertEquals(EstadoSancion.VIGENTE, e.sanciones.get(segunda.getId()).getEstado());
    }

    @Test
    void revocarUnBloqueoConUnaSuspensionVigenteDejaLaCuentaSuspendida() {
        Sancion suspension = sancionarALaura(suspension(14));
        Sancion bloqueo = sancionarALauraConSegundoServicio(bloqueo());
        assertEquals(EstadoCuenta.BLOQUEADA, laura.getEstadoCuenta());

        resolverApelacion(bloqueo, apelar(laura, bloqueo).id(), ResultadoApelacion.REVOCADA);

        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta());
        assertEquals(EstadoSancion.VIGENTE, e.sanciones.get(suspension.getId()).getEstado());
    }

    @Test
    void revocarNoTocaUnaCuentaQueUnAdministradorCambioAMano() {
        Sancion s = sancionarALaura(suspension(14));
        ApelacionDTO a = apelar(laura, s);
        laura.setEstadoCuenta(EstadoCuenta.BLOQUEADA); // bloqueo manual posterior

        resolverApelacion(s, a.id(), ResultadoApelacion.REVOCADA);

        assertEquals(EstadoCuenta.BLOQUEADA, laura.getEstadoCuenta());
    }

    private Sancion sancionarALauraConSegundoServicio(SancionEntradaDTO sancion) {
        ServicioTutoria otro = e.crearServicio(carlos, laura, EstadoServicio.EN_DESARROLLO, Instant.now().minusSeconds(60), "20000");
        ReporteDTO r = radicar(carlos, new ReporteEntradaDTO(TipoReporte.COBRO_EXTERNO,
                "Me pidió pagar por fuera de la plataforma", null, otro.getId()));
        resolver(r.id(), DecisionReporte.A_FAVOR_REPORTANTE, AccionPago.REEMBOLSAR, sancion);
        return e.sanciones.values().stream().filter(s -> s.getReporteId().equals(r.id())).findFirst().orElseThrow();
    }

    @Test
    void ratificarMantieneLaSancionYLaCuentaSuspendida() {
        Sancion s = sancionarALaura(suspension(14));
        ApelacionDTO a = apelar(laura, s);

        SancionDTO resuelta = resolverApelacion(s, a.id(), ResultadoApelacion.RATIFICADA);

        assertEquals(EstadoSancion.RATIFICADA, resuelta.estado());
        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta());
        aviso(laura, TipoNotificacion.APELACION_RESUELTA);
    }

    @Test
    void ratificarUnaSuspensionYaVencidaLaCierraYReactivaLaCuenta() {
        Sancion s = sancionarALaura(suspension(14));
        ApelacionDTO a = apelar(laura, s);
        // la vigencia terminó mientras se resolvía la apelación
        s.setVigencia(new Periodo(Instant.now().minus(Duration.ofDays(20)), Instant.now().minus(Duration.ofDays(1))));

        SancionDTO resuelta = resolverApelacion(s, a.id(), ResultadoApelacion.RATIFICADA);

        assertEquals(EstadoSancion.CUMPLIDA, resuelta.estado());
        assertEquals(EstadoCuenta.ACTIVA, laura.getEstadoCuenta());
        assertTrue(e.acciones().contains("SANCION_CUMPLIDA"));
    }

    @Test
    void unaApelacionSeResuelveUnaSolaVez() {
        Sancion s = sancionarALaura(suspension(14));
        ApelacionDTO a = apelar(laura, s);
        resolverApelacion(s, a.id(), ResultadoApelacion.RATIFICADA);

        assertNegocio("APELACION_YA_RESUELTA", HttpStatus.CONFLICT,
                () -> resolverApelacion(s, a.id(), ResultadoApelacion.REVOCADA));
        assertEquals(EstadoSancion.RATIFICADA, e.sanciones.get(s.getId()).getEstado());
    }

    @Test
    void validaLaResolucionDeLaApelacion() {
        Sancion s = sancionarALaura(suspension(14));
        ApelacionDTO a = apelar(laura, s);

        assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY,
                () -> resolverApelacion(s, a.id(), ResultadoApelacion.PENDIENTE));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> reportes.resolverApelacion(admin.getId(), s.getId(), a.id(),
                new ResolucionApelacionEntradaDTO(ResultadoApelacion.REVOCADA, " ")));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> reportes.resolverApelacion(admin.getId(), s.getId(), a.id(),
                new ResolucionApelacionEntradaDTO(null, "Observación")));
        assertThrows(ExcepcionNoEncontrado.class, () -> resolverApelacion(s, "no-existe", ResultadoApelacion.REVOCADA));
        assertThrows(ExcepcionNoEncontrado.class, () -> reportes.resolverApelacion(admin.getId(), "no-existe", a.id(),
                new ResolucionApelacionEntradaDTO(ResultadoApelacion.REVOCADA, "Observación")));
        assertEquals(EstadoSancion.EN_APELACION, e.sanciones.get(s.getId()).getEstado());
        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta());
    }

    @Test
    void siFallaReactivarLaCuentaSeDeshaceLaResolucion() {
        Sancion s = sancionarALaura(suspension(14));
        ApelacionDTO a = apelar(laura, s);
        when(e.repoUsuarios.save(any(Usuario.class))).thenThrow(new IllegalStateException("fallo de base de datos"));

        assertThrows(IllegalStateException.class, () -> resolverApelacion(s, a.id(), ResultadoApelacion.REVOCADA));

        Sancion almacenada = e.sanciones.get(s.getId());
        assertEquals(EstadoSancion.EN_APELACION, almacenada.getEstado());
        assertEquals(ResultadoApelacion.PENDIENTE, almacenada.getApelaciones().get(0).getResultado());
        assertNull(almacenada.getApelaciones().get(0).getFechaResolucion());
        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta());
        verify(e.notificaciones, never()).notificar(eq(laura.getId()), eq(TipoNotificacion.APELACION_RESUELTA),
                anyString(), anyString(), anyString());
    }

    @Test
    void laColaDeSancionesSePuedeFiltrarPorEstado() {
        Sancion s1 = sancionarALaura(suspension(14));
        sancionarALauraConAdvertenciaAparte();
        apelar(laura, s1);

        Pagina<SancionDTO> todas = reportes.listarSanciones(null, 0, 20);
        Pagina<SancionDTO> enApelacion = reportes.listarSanciones(EstadoSancion.EN_APELACION, 0, 20);
        Pagina<SancionDTO> vigentes = reportes.listarSanciones(EstadoSancion.VIGENTE, 0, 20);

        assertEquals(2, todas.totalElementos());
        assertEquals(1, enApelacion.totalElementos());
        assertEquals(s1.getId(), enApelacion.contenido().get(0).id());
        assertEquals("Laura Martínez", enApelacion.contenido().get(0).usuario().nombre());
        assertEquals(1, vigentes.totalElementos());
    }

    // ---------------------------------------------------------------- recorrido completo (HU-10)

    @Test
    void recorridoCompletoReporteResolucionConSancionApelacionYRevocacion() {
        // 1. El estudiante reporta la inasistencia con evidencia: pago CONGELADO, servicio EN_REVISION.
        ReporteDTO r = reportes.radicarReporte(autenticado(carlos), inasistencia(), List.of(evidencia("captura.pdf")));
        assertEquals(EstadoPago.CONGELADO, pago().getEstado());
        assertEquals(EstadoServicio.EN_REVISION, servicio.getEstado());

        // 2. El administrador reembolsa y suspende al tutor.
        resolver(r.id(), DecisionReporte.A_FAVOR_REPORTANTE, AccionPago.REEMBOLSAR, suspension(14));
        assertEquals(EstadoPago.REEMBOLSADO, pago().getEstado());
        assertEquals(EstadoServicio.CANCELADO, servicio.getEstado());
        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta());

        // 3. El tutor apela desde su sesión restringida.
        Sancion s = e.sanciones.values().iterator().next();
        UsuarioAutenticado restringida = new UsuarioAutenticado(laura.getId(), laura.getCorreo(), laura.getNombre(), laura.getRoles(), true);
        ApelacionDTO a = reportes.apelarSancion(restringida, s.getId(), descargo(), List.of(evidencia("incapacidad.pdf")));
        assertEquals(EstadoSancion.EN_APELACION, e.sanciones.get(s.getId()).getEstado());

        // 4. El administrador revoca: la cuenta vuelve a estar activa y todo queda auditado.
        resolverApelacion(s, a.id(), ResultadoApelacion.REVOCADA);
        assertEquals(EstadoSancion.REVOCADA, e.sanciones.get(s.getId()).getEstado());
        assertEquals(EstadoCuenta.ACTIVA, laura.getEstadoCuenta());
        assertTrue(e.acciones().containsAll(List.of("RADICAR_REPORTE", "PAGO_CONGELADO", "RESOLVER_REPORTE",
                "PAGO_REEMBOLSADO", "SANCION_APLICADA", "APELACION_PRESENTADA", "APELACION_RESUELTA")));
        verify(e.notificaciones).notificar(eq(laura.getId()), eq(TipoNotificacion.APELACION_RESUELTA),
                contains("aceptada"), anyString(), anyString());
    }
}
