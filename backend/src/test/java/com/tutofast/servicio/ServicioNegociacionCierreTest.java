package com.tutofast.servicio;

import static com.tutofast.EscenarioNegociacion.autenticado;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.tutofast.EscenarioNegociacion;
import com.tutofast.dto.CierreAutomaticoDTO;
import com.tutofast.dto.LiquidacionDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.modelo.EstadoPago;
import com.tutofast.modelo.EstadoServicio;
import com.tutofast.modelo.Pago;
import com.tutofast.modelo.RegistroAuditoria;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.ServicioTutoria;
import com.tutofast.modelo.Usuario;

/** Finalización, liquidación, cierre automático, inicio automático y recordatorios de sesión. */
class ServicioNegociacionCierreTest {

    private EscenarioNegociacion e;
    private ServicioNegociacion negociacion;
    private Usuario carlos;
    private Usuario laura;
    private Usuario admin;

    @BeforeEach
    void preparar() {
        e = new EscenarioNegociacion();
        negociacion = e.servicioNegociacion;
        carlos = e.crearUsuario("Carlos Gómez", Rol.ESTUDIANTE);
        laura = e.crearUsuario("Laura Martínez", Rol.TUTOR);
        admin = e.crearUsuario("Administrador", Rol.ADMINISTRADOR);
    }

    private ServicioTutoria enDesarrollo(String precio, long horasDesdeElInicio) {
        return e.crearServicio(carlos, laura, EstadoServicio.EN_DESARROLLO,
                Instant.now().minus(Duration.ofHours(horasDesdeElInicio)), precio);
    }

    private Pago pagoDe(ServicioTutoria s) {
        return e.pagos.values().stream().filter(p -> p.getServicioId().equals(s.getId())).findFirst().orElseThrow();
    }

    private static void assertNegocio(String codigo, HttpStatus estado, Runnable accion) {
        ExcepcionNegocio ex = assertThrows(ExcepcionNegocio.class, accion::run);
        assertEquals(codigo, ex.getCodigo());
        assertEquals(estado, ex.getEstado());
    }

    private static void assertMonto(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperado " + esperado + " pero fue " + real);
    }

    // ---------------------------------------------------------------- finalizar

    @Test
    void finalizarLiquidaLaComisionYLiberaElPago() {
        ServicioTutoria s = enDesarrollo("40000", 2);

        LiquidacionDTO dto = negociacion.finalizarServicio(carlos.getId(), s.getId());

        assertEquals(EstadoServicio.FINALIZADO, s.getEstado());
        assertNotNull(s.getFechaFinalizacion());
        assertEquals(EstadoPago.LIBERADO, dto.estado());
        assertEquals(s.getId(), dto.servicioId());
        assertMonto("40000", dto.precioFinalCOP());
        assertMonto("10", dto.comisionPct());
        assertMonto("4000", dto.comisionCOP());
        assertMonto("36000", dto.montoNetoCOP());
        assertNotNull(dto.fechaLiquidacion());
        Pago pago = pagoDe(s);
        assertEquals(EstadoPago.LIBERADO, pago.getEstado());
        assertEquals(List.of(EstadoPago.RETENIDO, EstadoPago.LIBERADO),
                pago.getHistorial().stream().map(Pago.Movimiento::getEstado).toList());
    }

    @Test
    void usaLaComisionCongeladaDelServicioYRedondeaHalfUp() {
        ServicioTutoria s = enDesarrollo("33333", 2);
        s.setComisionPct(new BigDecimal("12.5"));
        e.comisionVigente = new BigDecimal("40"); // un cambio posterior de comisión no afecta al servicio

        LiquidacionDTO dto = negociacion.finalizarServicio(carlos.getId(), s.getId());

        assertMonto("4167", dto.comisionCOP()); // 4.166,625 → 4.167
        assertMonto("29166", dto.montoNetoCOP());
        assertMonto("12.5", dto.comisionPct());
    }

    @Test
    void recalculaLosServiciosCompletadosDelTutor() {
        ServicioTutoria a = enDesarrollo("40000", 2);
        ServicioTutoria b = enDesarrollo("30000", 2);

        negociacion.finalizarServicio(carlos.getId(), a.getId());
        assertEquals(1, laura.getPerfilTutor().getServiciosCompletados());
        negociacion.finalizarServicio(carlos.getId(), b.getId());
        assertEquals(2, laura.getPerfilTutor().getServiciosCompletados());
    }

    @Test
    void notificaYAuditaLaFinalizacion() {
        ServicioTutoria s = enDesarrollo("40000", 2);
        String enlace = "negociacion.html?id=" + s.getId();

        negociacion.finalizarServicio(carlos.getId(), s.getId());

        verify(e.notificaciones).notificar(eq(carlos.getId()), eq(TipoNotificacion.SERVICIO_FINALIZADO), anyString(),
                contains("14 días"), eq(enlace));
        verify(e.notificaciones).notificar(eq(carlos.getId()), eq(TipoNotificacion.CALIFICACION_PENDIENTE), anyString(),
                contains("Laura Martínez"), eq(enlace));
        verify(e.notificaciones).notificar(eq(laura.getId()), eq(TipoNotificacion.SERVICIO_FINALIZADO), anyString(),
                contains("Carlos Gómez"), eq(enlace));
        verify(e.notificaciones).notificar(eq(laura.getId()), eq(TipoNotificacion.PAGO_LIBERADO), anyString(),
                contains("36000"), eq(enlace));
        verify(e.notificaciones).notificar(eq(laura.getId()), eq(TipoNotificacion.CALIFICACION_PENDIENTE), anyString(),
                contains("Carlos Gómez"), eq(enlace));
        assertTrue(e.acciones().containsAll(List.of("PAGO_LIBERADO", "SERVICIO_FINALIZADO")));
        RegistroAuditoria registro = e.bitacora.stream().filter(r -> r.getAccion().equals("SERVICIO_FINALIZADO")).findFirst().orElseThrow();
        assertEquals(carlos.getId(), registro.getUsuarioId());
        assertEquals("servicio:" + s.getId(), registro.getEntidadAfectada());
    }

    @Test
    void unFalloAlNotificarNoRevierteLaFinalizacion() {
        ServicioTutoria s = enDesarrollo("40000", 2);
        doThrow(new RuntimeException("sin conexión")).when(e.notificaciones)
                .notificar(anyString(), any(TipoNotificacion.class), anyString(), anyString(), anyString());

        negociacion.finalizarServicio(carlos.getId(), s.getId());

        assertEquals(EstadoServicio.FINALIZADO, s.getEstado());
        assertEquals(EstadoPago.LIBERADO, pagoDe(s).getEstado());
    }

    @Test
    void laDobleFinalizacionDa409YNoLiberaDosVeces() {
        ServicioTutoria s = enDesarrollo("40000", 2);
        negociacion.finalizarServicio(carlos.getId(), s.getId());
        int movimientos = pagoDe(s).getHistorial().size();

        ExcepcionNegocio ex = assertThrows(ExcepcionNegocio.class, () -> negociacion.finalizarServicio(carlos.getId(), s.getId()));

        assertEquals("SERVICIO_YA_FINALIZADO", ex.getCodigo());
        assertEquals(HttpStatus.CONFLICT, ex.getEstado());
        assertEquals("El servicio ya se encuentra finalizado y no requiere nuevas acciones", ex.getMessage());
        assertEquals(movimientos, pagoDe(s).getHistorial().size());
        assertEquals(1, e.acciones().stream().filter("PAGO_LIBERADO"::equals).count());
        assertEquals(1, laura.getPerfilTutor().getServiciosCompletados());
    }

    @Test
    void soloElEstudianteDelServicioLoFinaliza() {
        ServicioTutoria s = enDesarrollo("40000", 2);
        Usuario otro = e.crearUsuario("Otro Estudiante", Rol.ESTUDIANTE);

        assertThrows(ExcepcionAutorizacion.class, () -> negociacion.finalizarServicio(laura.getId(), s.getId()));
        assertThrows(ExcepcionAutorizacion.class, () -> negociacion.finalizarServicio(otro.getId(), s.getId()));
        assertThrows(ExcepcionAutorizacion.class, () -> negociacion.finalizarServicio(admin.getId(), s.getId()));
        assertThrows(ExcepcionNoEncontrado.class, () -> negociacion.finalizarServicio(carlos.getId(), "000000000000000000000000"));
        assertEquals(EstadoServicio.EN_DESARROLLO, s.getEstado());
        assertEquals(EstadoPago.RETENIDO, pagoDe(s).getEstado());
    }

    @Test
    void noSeFinalizaUnServicioSinIniciarNiEnRevision() {
        ServicioTutoria confirmado = e.crearServicio(carlos, laura, EstadoServicio.CONFIRMADO, Instant.now().plusSeconds(3600), "40000");
        ServicioTutoria enRevision = e.crearServicio(carlos, laura, EstadoServicio.EN_REVISION, Instant.now().minusSeconds(3600), "40000");

        assertNegocio("TRANSICION_INVALIDA", HttpStatus.CONFLICT, () -> negociacion.finalizarServicio(carlos.getId(), confirmado.getId()));
        assertNegocio("SERVICIO_EN_REVISION", HttpStatus.CONFLICT, () -> negociacion.finalizarServicio(carlos.getId(), enRevision.getId()));
        assertEquals(EstadoServicio.CONFIRMADO, confirmado.getEstado());
        assertEquals(EstadoServicio.EN_REVISION, enRevision.getEstado());
        assertEquals(EstadoPago.RETENIDO, pagoDe(confirmado).getEstado());
    }

    @Test
    void unReporteAbiertoImpideFinalizarYDejaTodoComoEstaba() {
        ServicioTutoria s = enDesarrollo("40000", 2);
        e.serviciosConReporteAbierto.add(s.getId());

        assertNegocio("REPORTE_ABIERTO", HttpStatus.CONFLICT, () -> negociacion.finalizarServicio(carlos.getId(), s.getId()));

        assertEquals(EstadoServicio.EN_DESARROLLO, s.getEstado());
        assertNull(s.getFechaFinalizacion());
        assertEquals(EstadoPago.RETENIDO, pagoDe(s).getEstado());
    }

    @Test
    void conElPagoCongeladoOSinPagoNoSeFinaliza() {
        ServicioTutoria congelado = enDesarrollo("40000", 2);
        pagoDe(congelado).congelar(Instant.now());
        ServicioTutoria sinPago = enDesarrollo("40000", 2);
        e.pagos.values().removeIf(p -> p.getServicioId().equals(sinPago.getId()));

        assertNegocio("PAGO_NO_LIBERABLE", HttpStatus.CONFLICT, () -> negociacion.finalizarServicio(carlos.getId(), congelado.getId()));
        assertNegocio("PAGO_NO_LIBERABLE", HttpStatus.CONFLICT, () -> negociacion.finalizarServicio(carlos.getId(), sinPago.getId()));

        assertEquals(EstadoServicio.EN_DESARROLLO, congelado.getEstado());
        assertEquals(EstadoServicio.EN_DESARROLLO, sinPago.getEstado());
        assertNull(congelado.getFechaFinalizacion());
    }

    @Test
    void siFallaLaLiberacionDelPagoSeRestauraElServicio() {
        ServicioTutoria s = enDesarrollo("40000", 2);
        when(e.repoPagos.save(any(Pago.class))).thenThrow(new RuntimeException("Mongo caído"));

        assertThrows(RuntimeException.class, () -> negociacion.finalizarServicio(carlos.getId(), s.getId()));

        assertEquals(EstadoServicio.EN_DESARROLLO, s.getEstado());
        assertNull(s.getFechaFinalizacion());
        assertEquals(0, laura.getPerfilTutor().getServiciosCompletados());
    }

    // ---------------------------------------------------------------- cierre automático

    @Test
    void cierraLosServiciosCuyoFinMasVeinticuatroHorasYaPaso() {
        ServicioTutoria vencido = enDesarrollo("40000", 30); // empezó hace 30 h, duró 1 h → fin + 24 h = hace 5 h

        CierreAutomaticoDTO r = negociacion.cerrarAutomaticamente(false);

        assertEquals(new CierreAutomaticoDTO(1, 1, 0, 0, 0, List.of(vencido.getId())), r);
        assertEquals(EstadoServicio.FINALIZADO, vencido.getEstado());
        assertEquals(EstadoPago.LIBERADO, pagoDe(vencido).getEstado());
        assertMonto("36000", pagoDe(vencido).getMontoNetoCOP());
        RegistroAuditoria registro = e.bitacora.stream()
                .filter(a -> a.getAccion().equals("SERVICIO_CERRADO_AUTOMATICAMENTE")).findFirst().orElseThrow();
        assertNull(registro.getUsuarioId(), "lo hace el sistema");
        verify(e.notificaciones).notificar(eq(carlos.getId()), eq(TipoNotificacion.SERVICIO_FINALIZADO), anyString(),
                contains("automáticamente"), anyString());
        assertEquals(1, laura.getPerfilTutor().getServiciosCompletados());
    }

    @Test
    void omiteLosQueTodaviaNoCumplenElPlazoDeVeinticuatroHoras() {
        ServicioTutoria reciente = enDesarrollo("40000", 2);
        ServicioTutoria casi = enDesarrollo("40000", 24); // fin hace 23 h: falta 1 h para las 24 h

        CierreAutomaticoDTO r = negociacion.cerrarAutomaticamente(false);

        assertEquals(2, r.revisados());
        assertEquals(0, r.cerrados());
        assertEquals(2, r.omitidosPorPlazo());
        assertEquals(EstadoServicio.EN_DESARROLLO, reciente.getEstado());
        assertEquals(EstadoServicio.EN_DESARROLLO, casi.getEstado());
        assertEquals(EstadoPago.RETENIDO, pagoDe(reciente).getEstado());
    }

    @Test
    void omiteLosServiciosConUnReporteAbierto() {
        ServicioTutoria conReporte = enDesarrollo("40000", 48);
        ServicioTutoria limpio = enDesarrollo("40000", 48);
        e.serviciosConReporteAbierto.add(conReporte.getId());

        CierreAutomaticoDTO r = negociacion.cerrarAutomaticamente(false);

        assertEquals(1, r.cerrados());
        assertEquals(1, r.omitidosPorReporte());
        assertEquals(List.of(limpio.getId()), r.serviciosCerrados());
        assertEquals(EstadoServicio.EN_DESARROLLO, conReporte.getEstado());
        assertEquals(EstadoPago.RETENIDO, pagoDe(conReporte).getEstado());
    }

    @Test
    void ignorarPlazoCierraLosRecientesPeroNuncaLosQueTienenReporte() {
        ServicioTutoria reciente = enDesarrollo("40000", 2);
        ServicioTutoria conReporte = enDesarrollo("40000", 2);
        e.serviciosConReporteAbierto.add(conReporte.getId());

        CierreAutomaticoDTO r = negociacion.cerrarAutomaticamente(true);

        assertEquals(List.of(reciente.getId()), r.serviciosCerrados());
        assertEquals(1, r.omitidosPorReporte());
        assertEquals(0, r.omitidosPorPlazo());
        assertEquals(EstadoServicio.EN_DESARROLLO, conReporte.getEstado());
    }

    @Test
    void soloRevisaLosServiciosEnDesarrollo() {
        e.crearServicio(carlos, laura, EstadoServicio.CONFIRMADO, Instant.now().minus(Duration.ofDays(5)), "40000");
        e.crearServicio(carlos, laura, EstadoServicio.FINALIZADO, Instant.now().minus(Duration.ofDays(5)), "40000");
        e.crearServicio(carlos, laura, EstadoServicio.EN_REVISION, Instant.now().minus(Duration.ofDays(5)), "40000");
        e.crearServicio(carlos, laura, EstadoServicio.CANCELADO, Instant.now().minus(Duration.ofDays(5)), "40000");

        CierreAutomaticoDTO r = negociacion.cerrarAutomaticamente(true);

        assertEquals(new CierreAutomaticoDTO(0, 0, 0, 0, 0, List.of()), r);
    }

    @Test
    void unServicioQueFallaNoImpideCerrarLosDemas() {
        ServicioTutoria malo = enDesarrollo("40000", 48);
        ServicioTutoria bueno = enDesarrollo("30000", 48);
        when(e.repoPagos.save(any(Pago.class))).thenAnswer(inv -> {
            Pago p = inv.getArgument(0);
            if (p.getServicioId().equals(malo.getId())) {
                throw new RuntimeException("Mongo caído");
            }
            e.pagos.put(p.getId(), p);
            return p;
        });

        CierreAutomaticoDTO r = negociacion.cerrarAutomaticamente(false);

        assertEquals(1, r.cerrados());
        assertEquals(1, r.fallidos());
        assertEquals(List.of(bueno.getId()), r.serviciosCerrados());
        assertEquals(EstadoServicio.EN_DESARROLLO, malo.getEstado(), "el fallido se restaura para reintentarlo");
        assertEquals(EstadoServicio.FINALIZADO, bueno.getEstado());
    }

    @Test
    void cadaServicioSeCierraEnSuPropiaTransaccion() {
        PlataformaDeTransacciones gestor = new PlataformaDeTransacciones();
        ServicioNegociacion conGestor = new ServicioNegociacion(e.repoSolicitudes, e.repoPropuestas, e.repoServicios,
                e.repoUsuarios, e.servicioPropuestas, e.servicioPagos, e.catalogo, e.auditoria, e.notificaciones,
                e.servicioMensajeria, e.servicioReputacion, e.repoReportes, gestor.mock);
        enDesarrollo("40000", 48);
        enDesarrollo("30000", 48);
        ServicioTutoria malo = enDesarrollo("20000", 48);
        when(e.repoPagos.save(any(Pago.class))).thenAnswer(inv -> {
            Pago p = inv.getArgument(0);
            if (p.getServicioId().equals(malo.getId())) {
                throw new RuntimeException("Mongo caído");
            }
            e.pagos.put(p.getId(), p);
            return p;
        });

        conGestor.cerrarAutomaticamente(false);

        verify(gestor.mock, times(3)).getTransaction(any());
        verify(gestor.mock, times(2)).commit(any());
        verify(gestor.mock, times(1)).rollback(any());
    }

    @Test
    void elAdministradorSimulaElCierreYQuedaAuditado() {
        ServicioTutoria reciente = enDesarrollo("40000", 2);

        CierreAutomaticoDTO sinForzar = negociacion.simularCierreAutomatico(admin.getId(), false);
        assertEquals(0, sinForzar.cerrados());
        assertEquals(EstadoServicio.EN_DESARROLLO, reciente.getEstado());

        CierreAutomaticoDTO forzado = negociacion.simularCierreAutomatico(admin.getId(), true);
        assertEquals(List.of(reciente.getId()), forzado.serviciosCerrados());
        assertEquals(EstadoServicio.FINALIZADO, reciente.getEstado());
        List<RegistroAuditoria> manuales = e.bitacora.stream().filter(r -> r.getAccion().equals("CIERRE_AUTOMATICO_MANUAL")).toList();
        assertEquals(2, manuales.size());
        assertEquals(admin.getId(), manuales.get(1).getUsuarioId());
        assertEquals(true, manuales.get(1).getDetalle().get("ignorarPlazo"));
        assertEquals(1, manuales.get(1).getDetalle().get("cerrados"));
    }

    // ---------------------------------------------------------------- inicio automático

    @Test
    void alLlegarLaHoraProgramadaElServicioPasaAEnDesarrollo() {
        ServicioTutoria s = e.crearServicio(carlos, laura, EstadoServicio.CONFIRMADO, Instant.now().minusSeconds(30), "40000");
        s.setFechaInicio(null);
        Instant ahora = Instant.now();

        int iniciados = negociacion.iniciarAutomaticamente(ahora);

        assertEquals(1, iniciados);
        assertEquals(EstadoServicio.EN_DESARROLLO, s.getEstado());
        assertEquals(ahora, s.getFechaInicio());
        RegistroAuditoria r = e.bitacora.stream().filter(a -> a.getAccion().equals("SERVICIO_INICIADO_AUTOMATICO")).findFirst().orElseThrow();
        assertNull(r.getUsuarioId());
        verify(e.notificaciones).notificar(eq(carlos.getId()), eq(TipoNotificacion.RECORDATORIO_SESION), anyString(),
                anyString(), eq("negociacion.html?id=" + s.getId()));
        verify(e.notificaciones).notificar(eq(laura.getId()), eq(TipoNotificacion.RECORDATORIO_SESION), anyString(),
                anyString(), eq("negociacion.html?id=" + s.getId()));
    }

    @Test
    void noIniciaLosQueTodaviaNoLlegaronNiLosYaIniciadosYEsIdempotente() {
        ServicioTutoria futuro = e.crearServicio(carlos, laura, EstadoServicio.CONFIRMADO, Instant.now().plusSeconds(3600), "40000");
        ServicioTutoria yaEnCurso = enDesarrollo("40000", 1);
        ServicioTutoria pasado = e.crearServicio(carlos, laura, EstadoServicio.CONFIRMADO, Instant.now().minusSeconds(5), "40000");

        assertEquals(1, negociacion.iniciarAutomaticamente(Instant.now()));
        assertEquals(0, negociacion.iniciarAutomaticamente(Instant.now()));

        assertEquals(EstadoServicio.CONFIRMADO, futuro.getEstado());
        assertEquals(EstadoServicio.EN_DESARROLLO, yaEnCurso.getEstado());
        assertEquals(EstadoServicio.EN_DESARROLLO, pasado.getEstado());
        assertEquals(1, e.acciones().stream().filter("SERVICIO_INICIADO_AUTOMATICO"::equals).count());
    }

    @Test
    void unFalloAlIniciarUnoNoImpideIniciarLosDemas() {
        ServicioTutoria malo = e.crearServicio(carlos, laura, EstadoServicio.CONFIRMADO, Instant.now().minusSeconds(60), "40000");
        ServicioTutoria bueno = e.crearServicio(carlos, laura, EstadoServicio.CONFIRMADO, Instant.now().minusSeconds(60), "40000");
        when(e.repoServicios.save(any(ServicioTutoria.class))).thenAnswer(inv -> {
            ServicioTutoria s = inv.getArgument(0);
            if (s.getId().equals(malo.getId())) {
                throw new RuntimeException("Mongo caído");
            }
            return s;
        });

        assertEquals(1, negociacion.iniciarAutomaticamente(Instant.now()));
        assertEquals(EstadoServicio.EN_DESARROLLO, bueno.getEstado());
    }

    @Test
    void lasNotificacionesDeInicioNoRevierten() {
        ServicioTutoria s = e.crearServicio(carlos, laura, EstadoServicio.CONFIRMADO, Instant.now().minusSeconds(30), "40000");
        doThrow(new RuntimeException("sin conexión")).when(e.notificaciones)
                .notificar(anyString(), any(TipoNotificacion.class), anyString(), anyString(), anyString());

        assertEquals(1, negociacion.iniciarAutomaticamente(Instant.now()));
        assertEquals(EstadoServicio.EN_DESARROLLO, s.getEstado());
    }

    // ---------------------------------------------------------------- recordatorios

    @Test
    void recuerdaUnaSolaVezLasSesionesDeLasProximasVeinticuatroHoras() {
        ServicioTutoria proxima = e.crearServicio(carlos, laura, EstadoServicio.CONFIRMADO, Instant.now().plus(Duration.ofHours(5)), "40000");
        ServicioTutoria lejana = e.crearServicio(carlos, laura, EstadoServicio.CONFIRMADO, Instant.now().plus(Duration.ofHours(30)), "40000");
        ServicioTutoria pasada = e.crearServicio(carlos, laura, EstadoServicio.CONFIRMADO, Instant.now().minus(Duration.ofHours(1)), "40000");

        assertEquals(1, negociacion.recordarSesiones(Instant.now()));
        assertEquals(0, negociacion.recordarSesiones(Instant.now()), "no se repite");

        assertTrue(proxima.isRecordatorioEnviado());
        assertFalse(lejana.isRecordatorioEnviado());
        assertFalse(pasada.isRecordatorioEnviado());
        verify(e.notificaciones).recordar(eq(carlos.getId()), eq(TipoNotificacion.RECORDATORIO_SESION), anyString(),
                anyString(), eq("negociacion.html?id=" + proxima.getId()));
        verify(e.notificaciones).recordar(eq(laura.getId()), eq(TipoNotificacion.RECORDATORIO_SESION), anyString(),
                anyString(), eq("negociacion.html?id=" + proxima.getId()));
    }

    @Test
    void siNoSePudoEntregarElRecordatorioSeReintentaEnLaSiguientePasada() {
        ServicioTutoria s = e.crearServicio(carlos, laura, EstadoServicio.CONFIRMADO, Instant.now().plus(Duration.ofHours(5)), "40000");
        when(e.notificaciones.recordar(eq(laura.getId()), any(TipoNotificacion.class), anyString(), anyString(), anyString()))
                .thenReturn(false);

        assertEquals(0, negociacion.recordarSesiones(Instant.now()));
        assertFalse(s.isRecordatorioEnviado());

        when(e.notificaciones.recordar(eq(laura.getId()), any(TipoNotificacion.class), anyString(), anyString(), anyString()))
                .thenReturn(true);
        assertEquals(1, negociacion.recordarSesiones(Instant.now()));
        assertTrue(s.isRecordatorioEnviado());
    }

    @Test
    void ningunProcesoProgramadoNotificaSiNoHayNada() {
        negociacion.iniciarAutomaticamente(Instant.now());
        negociacion.recordarSesiones(Instant.now());
        negociacion.cerrarAutomaticamente(false);

        verify(e.notificaciones, never()).notificar(anyString(), any(TipoNotificacion.class), anyString(), anyString(), anyString());
        assertTrue(e.bitacora.isEmpty());
        assertEquals(autenticado(carlos).id(), carlos.getId());
    }

    /** Gestor de transacciones simulado que entrega estados válidos para poder contar commits y rollbacks. */
    private static final class PlataformaDeTransacciones {
        final PlatformTransactionManager mock = mock(PlatformTransactionManager.class);

        PlataformaDeTransacciones() {
            when(mock.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
        }
    }
}
