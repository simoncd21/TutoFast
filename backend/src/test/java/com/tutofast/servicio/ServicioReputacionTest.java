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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import com.tutofast.EscenarioNegociacion;
import com.tutofast.dto.CalificacionDTO;
import com.tutofast.dto.CalificacionEntradaDTO;
import com.tutofast.dto.CalificacionesServicioDTO;
import com.tutofast.dto.ModeracionEntradaDTO;
import com.tutofast.dto.ReporteResenaEntradaDTO;
import com.tutofast.dto.ResenaReportadaDTO;
import com.tutofast.dto.SancionEntradaDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.modelo.Calificacion;
import com.tutofast.modelo.CalificacionAlEstudiante;
import com.tutofast.modelo.CalificacionAlTutor;
import com.tutofast.modelo.DecisionModeracion;
import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.EstadoServicio;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.ServicioTutoria;
import com.tutofast.modelo.TipoCalificacion;
import com.tutofast.modelo.TipoSancion;
import com.tutofast.modelo.Usuario;

class ServicioReputacionTest {

    private EscenarioNegociacion e;
    private ServicioReputacion reputacion;
    private Usuario carlos;
    private Usuario laura;
    private Usuario admin;
    private ServicioTutoria servicio;

    @BeforeEach
    void preparar() {
        e = new EscenarioNegociacion();
        reputacion = e.servicioReputacion;
        carlos = e.crearUsuario("Carlos Gómez", Rol.ESTUDIANTE);
        laura = e.crearUsuario("Laura Martínez", Rol.TUTOR);
        admin = e.crearUsuario("Administrador", Rol.ADMINISTRADOR);
        servicio = finalizado(carlos, laura, 1);
    }

    /** Servicio FINALIZADO hace {@code dias} días (con su pago ya liberado no hace falta aquí). */
    private ServicioTutoria finalizado(Usuario estudiante, Usuario tutor, long dias) {
        Instant fin = Instant.now().minus(dias, ChronoUnit.DAYS);
        ServicioTutoria s = e.crearServicio(estudiante, tutor, EstadoServicio.FINALIZADO, fin.minus(2, ChronoUnit.HOURS), "40000");
        s.setFechaFinalizacion(fin);
        return s;
    }

    private static CalificacionEntradaDTO alTutor(int claridad, int dominio, int puntualidad, int cumplimiento, int trato,
                                                   int precio, String comentario) {
        return new CalificacionEntradaDTO(claridad, dominio, puntualidad, cumplimiento, trato, precio, null, comentario);
    }

    private static CalificacionEntradaDTO alEstudiante(int puntualidad, int comunicacion, int cumplimiento, String comentario) {
        return new CalificacionEntradaDTO(null, null, puntualidad, cumplimiento, null, null, comunicacion, comentario);
    }

    private CalificacionDTO calificarComoCarlos(ServicioTutoria s) {
        return reputacion.calificar(autenticado(carlos), s.getId(),
                alTutor(5, 5, 5, 5, 5, 4, "Excelente explicación, muy paciente"));
    }

    private CalificacionDTO calificarComoLaura(ServicioTutoria s) {
        return reputacion.calificar(autenticado(laura), s.getId(), alEstudiante(5, 5, 4, "Puntual y preparado"));
    }

    private static void assertNegocio(String codigo, HttpStatus estado, Runnable accion) {
        ExcepcionNegocio ex = assertThrows(ExcepcionNegocio.class, accion::run);
        assertEquals(codigo, ex.getCodigo());
        assertEquals(estado, ex.getEstado());
    }

    private static void assertPromedio(String esperado, BigDecimal real) {
        assertNotNull(real);
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperado " + esperado + " pero fue " + real);
    }

    /** Inserta una calificación visible ya emitida (para probar el recálculo sin pasar por el flujo completo). */
    private Calificacion visible(TipoCalificacion tipo, String destinatarioId, int... criterios) {
        Calificacion c;
        if (tipo == TipoCalificacion.AL_TUTOR) {
            CalificacionAlTutor t = new CalificacionAlTutor();
            t.setClaridadPedagogica(criterios[0]);
            t.setDominioTematico(criterios[1]);
            t.setPuntualidad(criterios[2]);
            t.setCumplimiento(criterios[3]);
            t.setTrato(criterios[4]);
            t.setRelacionCalidadPrecio(criterios[5]);
            c = t;
        } else {
            CalificacionAlEstudiante s = new CalificacionAlEstudiante();
            s.setPuntualidad(criterios[0]);
            s.setComunicacion(criterios[1]);
            s.setCumplimiento(criterios[2]);
            c = s;
        }
        c.setId(EscenarioNegociacion.nuevoId());
        c.setServicioId(EscenarioNegociacion.nuevoId());
        c.setAutorId(EscenarioNegociacion.nuevoId());
        c.setDestinatarioId(destinatarioId);
        c.setFechaEmision(Instant.now());
        c.setFechaLimite(Instant.now().plus(5, ChronoUnit.DAYS));
        c.setVisible(true);
        e.calificaciones.put(c.getId(), c);
        return c;
    }

    // ---------------------------------------------------------------- calificar

    @Test
    void elEstudianteCalificaAlTutorYQuedaOcultaHastaQueLaOtraParteCalifique() {
        CalificacionDTO dto = calificarComoCarlos(servicio);

        assertEquals(TipoCalificacion.AL_TUTOR, dto.tipo());
        assertEquals(carlos.getId(), dto.autorId());
        assertEquals(laura.getId(), dto.destinatarioId());
        assertPromedio("4.83", dto.promedio());
        assertEquals(6, dto.criterios().size());
        assertFalse(dto.visible());
        assertEquals(Calificacion.calcularFechaLimite(servicio.getFechaFinalizacion()), dto.fechaLimite());
        assertNull(laura.getPerfilTutor().getPromedioCalificacion(), "una reseña oculta no cuenta todavía");
        verify(e.notificaciones).notificar(eq(laura.getId()), eq(TipoNotificacion.CALIFICACION_PENDIENTE), anyString(),
                contains("Carlos Gómez"), eq("negociacion.html?id=" + servicio.getId()));
        assertTrue(e.acciones().contains("CALIFICACION_EMITIDA"));
    }

    @Test
    void elTutorCalificaAlEstudianteConTresCriteriosEIgnoraLosDemas() {
        CalificacionEntradaDTO conSobrantes = new CalificacionEntradaDTO(1, 1, 5, 4, 1, 1, 5, "Muy buen estudiante");

        CalificacionDTO dto = reputacion.calificar(autenticado(laura), servicio.getId(), conSobrantes);

        assertEquals(TipoCalificacion.AL_ESTUDIANTE, dto.tipo());
        assertEquals(carlos.getId(), dto.destinatarioId());
        assertEquals(3, dto.criterios().size());
        assertPromedio("4.67", dto.promedio());
    }

    @Test
    void cuandoLasDosPartesCalificanSeRevelanJuntasYSeRecalculaLaReputacion() {
        calificarComoCarlos(servicio);
        CalificacionDTO segunda = calificarComoLaura(servicio);

        assertTrue(segunda.visible());
        assertTrue(e.calificaciones.values().stream().allMatch(Calificacion::isVisible));
        assertPromedio("4.83", laura.getPerfilTutor().getPromedioCalificacion());
        assertEquals(1, laura.getPerfilTutor().getServiciosCompletados());
        assertPromedio("4.67", carlos.getPerfilEstudiante().getIndicadorConfiabilidad());
        assertEquals(1, carlos.getPerfilEstudiante().getCalificacionesRecibidas());
        verify(e.notificaciones, times(2)).notificar(anyString(), eq(TipoNotificacion.RESENA_DISPONIBLE), anyString(),
                anyString(), anyString());
    }

    @Test
    void seCalificaEnCualquierOrden() {
        calificarComoLaura(servicio);
        calificarComoCarlos(servicio);

        assertTrue(e.calificaciones.values().stream().allMatch(Calificacion::isVisible));
        assertPromedio("4.83", laura.getPerfilTutor().getPromedioCalificacion());
    }

    @Test
    void soloSeCalificanServiciosFinalizados() {
        for (EstadoServicio estado : List.of(EstadoServicio.CONFIRMADO, EstadoServicio.EN_DESARROLLO,
                EstadoServicio.EN_REVISION, EstadoServicio.CANCELADO)) {
            ServicioTutoria s = e.crearServicio(carlos, laura, estado, Instant.now().minusSeconds(3600), "40000");
            ExcepcionNegocio ex = assertThrows(ExcepcionNegocio.class, () -> calificarComoCarlos(s));
            assertEquals("REGLA_NEGOCIO", ex.getCodigo());
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getEstado());
            assertTrue(ex.getMessage().contains("(R13)"), estado.name());
        }
        assertTrue(e.calificaciones.isEmpty());
    }

    @Test
    void unaParteSoloCalificaUnaVez() {
        calificarComoCarlos(servicio);

        assertNegocio("DUPLICADO", HttpStatus.CONFLICT, () -> calificarComoCarlos(servicio));
        assertEquals(1, e.calificaciones.size());
    }

    @Test
    void unaCarreraContraElIndiceUnicoTambienDa409() {
        calificarComoCarlos(servicio);
        // La comprobación previa no ve la calificación (carrera), pero el índice único (servicioId, tipo) sí.
        when(e.repoCalificaciones.existsByServicioIdAndTipo(anyString(), any(TipoCalificacion.class))).thenReturn(false);

        assertNegocio("DUPLICADO", HttpStatus.CONFLICT, () -> calificarComoCarlos(servicio));
        assertEquals(1, e.calificaciones.size());
    }

    @Test
    void pasadosCatorceDiasDeLaFinalizacionYaNoSePuedeCalificar() {
        ServicioTutoria viejo = finalizado(carlos, laura, 15);

        assertNegocio("PLAZO_VENCIDO", HttpStatus.UNPROCESSABLE_ENTITY, () -> calificarComoCarlos(viejo));
        assertNegocio("PLAZO_VENCIDO", HttpStatus.UNPROCESSABLE_ENTITY, () -> calificarComoLaura(viejo));
    }

    @Test
    void enElUltimoDiaDelPlazoTodavíaSePuedeCalificar() {
        ServicioTutoria casi = finalizado(carlos, laura, 13);

        assertNotNull(calificarComoCarlos(casi).id());
    }

    @Test
    void soloLosParticipantesConCuentaActivaCalifican() {
        Usuario ajeno = e.crearUsuario("Ajeno", Rol.ESTUDIANTE, Rol.TUTOR);

        assertThrows(ExcepcionAutorizacion.class, () -> reputacion.calificar(autenticado(ajeno), servicio.getId(),
                alTutor(5, 5, 5, 5, 5, 5, null)));
        assertThrows(ExcepcionAutorizacion.class, () -> reputacion.calificar(autenticado(admin), servicio.getId(),
                alTutor(5, 5, 5, 5, 5, 5, null)));
        carlos.setEstadoCuenta(EstadoCuenta.SUSPENDIDA);
        assertThrows(ExcepcionAutorizacion.class, () -> calificarComoCarlos(servicio));
        assertThrows(ExcepcionNoEncontrado.class, () -> reputacion.calificar(autenticado(laura), "000000000000000000000000",
                alEstudiante(5, 5, 5, null)));
        assertTrue(e.calificaciones.isEmpty());
    }

    @Test
    void exigeTodosLosCriteriosDelTipoYLosValidaEnRango() {
        // Faltan criterios: 400 con la lista.
        ExcepcionNegocio faltan = assertThrows(ExcepcionNegocio.class, () -> reputacion.calificar(autenticado(carlos),
                servicio.getId(), new CalificacionEntradaDTO(5, null, 5, 5, null, 5, null, null)));
        assertEquals("VALIDACION", faltan.getCodigo());
        assertEquals(HttpStatus.BAD_REQUEST, faltan.getEstado());
        assertTrue(faltan.getMessage().contains("dominioTematico") && faltan.getMessage().contains("trato"));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> reputacion.calificar(autenticado(laura), servicio.getId(),
                new CalificacionEntradaDTO(null, null, 5, 5, null, null, null, null))); // sin comunicación
        // Un 0 o un 6 que se salte la validación del DTO igualmente lo rechaza el dominio.
        assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY, () -> reputacion.calificar(autenticado(carlos),
                servicio.getId(), alTutor(5, 5, 6, 5, 5, 5, null)));
        assertTrue(e.calificaciones.isEmpty());
    }

    @Test
    void elComentarioNoPuedeIncluirContactosExternos() {
        assertNegocio("CONTACTO_EXTERNO", HttpStatus.UNPROCESSABLE_ENTITY, () -> reputacion.calificar(autenticado(carlos),
                servicio.getId(), alTutor(5, 5, 5, 5, 5, 5, "Excelente, escríbele al 3001234567")));
        assertTrue(e.calificaciones.isEmpty());
    }

    @Test
    void elComentarioEsOpcionalYSeLimpia() {
        CalificacionDTO dto = reputacion.calificar(autenticado(carlos), servicio.getId(), alTutor(4, 4, 4, 4, 4, 4, "   "));
        assertNull(dto.comentario());
        assertPromedio("4.00", dto.promedio());
    }

    // ---------------------------------------------------------------- consultar

    @Test
    void consultarMuestraLaPropiaLaDeLaContraparteSoloSiEsVisibleYElPlazo() {
        CalificacionesServicioDTO antes = reputacion.consultar(autenticado(carlos), servicio.getId());
        assertNull(antes.mia());
        assertNull(antes.contraparte());
        assertTrue(antes.puedeCalificar());
        assertEquals(Calificacion.calcularFechaLimite(servicio.getFechaFinalizacion()), antes.fechaLimite());

        calificarComoLaura(servicio); // Laura califica primero: Carlos todavía no ve nada
        CalificacionesServicioDTO tras = reputacion.consultar(autenticado(carlos), servicio.getId());
        assertNull(tras.contraparte(), "la reseña de la contraparte sigue oculta");
        assertEquals(carlos.getId(), reputacion.consultar(autenticado(laura), servicio.getId()).mia().destinatarioId());

        calificarComoCarlos(servicio);
        CalificacionesServicioDTO despues = reputacion.consultar(autenticado(carlos), servicio.getId());
        assertNotNull(despues.mia());
        assertNotNull(despues.contraparte());
        assertEquals(TipoCalificacion.AL_ESTUDIANTE, despues.contraparte().tipo());
        assertFalse(despues.puedeCalificar(), "ya calificó");
    }

    @Test
    void consultarNoPermiteCalificarFueraDePlazoNiSiNoEstaFinalizado() {
        ServicioTutoria viejo = finalizado(carlos, laura, 20);
        ServicioTutoria enCurso = e.crearServicio(carlos, laura, EstadoServicio.EN_DESARROLLO, Instant.now(), "40000");

        assertFalse(reputacion.consultar(autenticado(carlos), viejo.getId()).puedeCalificar());
        CalificacionesServicioDTO sinFinalizar = reputacion.consultar(autenticado(carlos), enCurso.getId());
        assertFalse(sinFinalizar.puedeCalificar());
        assertNull(sinFinalizar.fechaLimite());
    }

    @Test
    void consultarSoloLoHacenLosParticipantes() {
        Usuario ajeno = e.crearUsuario("Ajeno", Rol.ESTUDIANTE);

        assertThrows(ExcepcionAutorizacion.class, () -> reputacion.consultar(autenticado(ajeno), servicio.getId()));
        assertThrows(ExcepcionAutorizacion.class, () -> reputacion.consultar(autenticado(admin), servicio.getId()));
        assertThrows(ExcepcionNoEncontrado.class, () -> reputacion.consultar(autenticado(carlos), "000000000000000000000000"));
    }

    // ---------------------------------------------------------------- recálculo

    @Test
    void elPromedioDelTutorEsLaMediaDeSusReseñasVisiblesConDosDecimalesHalfUp() {
        visible(TipoCalificacion.AL_TUTOR, laura.getId(), 5, 5, 5, 5, 5, 4); // 4,83
        visible(TipoCalificacion.AL_TUTOR, laura.getId(), 4, 4, 4, 4, 4, 4); // 4,00 → media 4,415 → 4,42

        reputacion.recalcularTutor(laura.getId());

        assertPromedio("4.42", laura.getPerfilTutor().getPromedioCalificacion());
    }

    @Test
    void elRecalculoIgnoraOcultasModeradasYLasDeOtroTipo() {
        visible(TipoCalificacion.AL_TUTOR, laura.getId(), 5, 5, 5, 5, 5, 5);
        Calificacion oculta = visible(TipoCalificacion.AL_TUTOR, laura.getId(), 1, 1, 1, 1, 1, 1);
        oculta.setVisible(false);
        Calificacion moderada = visible(TipoCalificacion.AL_TUTOR, laura.getId(), 1, 1, 1, 1, 1, 1);
        moderada.setVisible(false);
        moderada.setOcultaPorModeracion(true);
        visible(TipoCalificacion.AL_ESTUDIANTE, laura.getId(), 1, 1, 1); // como estudiante, no como tutor

        reputacion.recalcularTutor(laura.getId());

        assertPromedio("5.00", laura.getPerfilTutor().getPromedioCalificacion());
    }

    @Test
    void sinReseñasVisiblesElPromedioQuedaNuloYLosServiciosCompletadosCuentanSoloFinalizados() {
        laura.getPerfilTutor().setPromedioCalificacion(new BigDecimal("4.50")); // valor viejo
        finalizado(carlos, laura, 3);
        e.crearServicio(carlos, laura, EstadoServicio.EN_DESARROLLO, Instant.now(), "30000");
        e.crearServicio(carlos, laura, EstadoServicio.CANCELADO, Instant.now(), "30000");

        reputacion.recalcularTutor(laura.getId());

        assertNull(laura.getPerfilTutor().getPromedioCalificacion());
        assertEquals(2, laura.getPerfilTutor().getServiciosCompletados(), "el de @BeforeEach y el de esta prueba");
    }

    @Test
    void elRecalculoEsIdempotenteYCorrigeContadoresAtrasados() {
        laura.getPerfilTutor().setServiciosCompletados(99);
        reputacion.recalcularTutor(laura.getId());
        reputacion.recalcularTutor(laura.getId());

        assertEquals(1, laura.getPerfilTutor().getServiciosCompletados());
    }

    @Test
    void elIndicadorDeConfiabilidadDelEstudianteSeRecalculaConSusVisibles() {
        visible(TipoCalificacion.AL_ESTUDIANTE, carlos.getId(), 5, 5, 5);
        visible(TipoCalificacion.AL_ESTUDIANTE, carlos.getId(), 3, 3, 3);

        reputacion.recalcularEstudiante(carlos.getId());

        assertPromedio("4.00", carlos.getPerfilEstudiante().getIndicadorConfiabilidad());
        assertEquals(2, carlos.getPerfilEstudiante().getCalificacionesRecibidas());
    }

    @Test
    void recalcularUsuariosSinPerfilOInexistentesNoHaceNada() {
        Usuario soloAdmin = admin;
        reputacion.recalcularTutor(soloAdmin.getId());
        reputacion.recalcularEstudiante(soloAdmin.getId());
        reputacion.recalcularTutor("000000000000000000000000");
        reputacion.recalcularEstudiante("000000000000000000000000");
    }

    // ---------------------------------------------------------------- publicación por plazo

    @Test
    void alVencerElPlazoSePublicaLaReseñaQueNuncaTuvoContraparte() {
        calificarComoCarlos(servicio);
        Calificacion c = e.calificaciones.values().iterator().next();
        c.setFechaLimite(Instant.now().minusSeconds(60));

        int publicadas = reputacion.cerrarResenas(Instant.now());

        assertEquals(1, publicadas);
        assertTrue(c.isVisible());
        assertPromedio("4.83", laura.getPerfilTutor().getPromedioCalificacion());
        verify(e.notificaciones).notificar(eq(laura.getId()), eq(TipoNotificacion.RESENA_DISPONIBLE), anyString(),
                contains("plazo"), anyString());
        assertTrue(e.acciones().contains("RESENAS_PUBLICADAS_POR_PLAZO"));
        assertEquals(0, reputacion.cerrarResenas(Instant.now()), "no vuelve a publicarla");
    }

    @Test
    void antesDelPlazoLaReseñaSigueOcultaYLaModeradaNuncaSePublica() {
        calificarComoCarlos(servicio);
        Calificacion c = e.calificaciones.values().iterator().next();

        assertEquals(0, reputacion.cerrarResenas(Instant.now()));
        assertFalse(c.isVisible());

        c.setFechaLimite(Instant.now().minusSeconds(60));
        c.setOcultaPorModeracion(true);
        assertEquals(0, reputacion.cerrarResenas(Instant.now()));
        assertFalse(c.isVisible());
    }

    @Test
    void publicarPorPlazoRecalculaTambienAlEstudiante() {
        calificarComoLaura(servicio);
        Calificacion c = e.calificaciones.values().iterator().next();
        c.setFechaLimite(Instant.now().minusSeconds(60));

        reputacion.cerrarResenas(Instant.now());

        assertPromedio("4.67", carlos.getPerfilEstudiante().getIndicadorConfiabilidad());
    }

    // ---------------------------------------------------------------- recordatorios de calificación

    @Test
    void recuerdaUnaSolaVezAQuienNoHaCalificadoCuandoFaltanSieteDias() {
        ServicioTutoria s = finalizado(carlos, laura, 8);

        assertEquals(1, reputacion.recordarCalificaciones(Instant.now()), "solo el de 8 días; el de 1 día es muy reciente");

        verify(e.notificaciones).notificar(eq(carlos.getId()), eq(TipoNotificacion.CALIFICACION_PENDIENTE), anyString(),
                contains("7 días"), eq("negociacion.html?id=" + s.getId()));
        verify(e.notificaciones).notificar(eq(laura.getId()), eq(TipoNotificacion.CALIFICACION_PENDIENTE), anyString(),
                contains("7 días"), eq("negociacion.html?id=" + s.getId()));
        assertTrue(s.isRecordatorioCalificacionEnviado());
        assertEquals(0, reputacion.recordarCalificaciones(Instant.now()), "no se repite");
    }

    @Test
    void noRecuerdaAQuienYaCalificoNiFueraDeLaVentana() {
        ServicioTutoria s = finalizado(carlos, laura, 9);
        reputacion.calificar(autenticado(carlos), s.getId(), alTutor(5, 5, 5, 5, 5, 5, null));
        ServicioTutoria vencido = finalizado(carlos, laura, 16);

        reputacion.recordarCalificaciones(Instant.now());

        verify(e.notificaciones, never()).notificar(eq(carlos.getId()), eq(TipoNotificacion.CALIFICACION_PENDIENTE),
                anyString(), contains("7 días"), anyString());
        verify(e.notificaciones).notificar(eq(laura.getId()), eq(TipoNotificacion.CALIFICACION_PENDIENTE), anyString(),
                contains("7 días"), anyString());
        assertFalse(vencido.isRecordatorioCalificacionEnviado());
    }

    // ---------------------------------------------------------------- reportar y moderar reseñas

    /** Publica la reseña de Carlos a Laura (las dos partes califican) y la devuelve. */
    private Calificacion reseñaPublicadaAlTutor() {
        calificarComoCarlos(servicio);
        calificarComoLaura(servicio);
        return e.calificaciones.values().stream().filter(c -> c.getTipo() == TipoCalificacion.AL_TUTOR).findFirst().orElseThrow();
    }

    @Test
    void elDestinatarioReportaUnaReseñaPublicada() {
        Calificacion c = reseñaPublicadaAlTutor();

        CalificacionDTO dto = reputacion.reportarResena(autenticado(laura), c.getId(),
                new ReporteResenaEntradaDTO("Contiene acusaciones falsas"));

        assertTrue(c.isReportada());
        assertEquals("Contiene acusaciones falsas", c.getMotivoReporte());
        assertEquals(laura.getId(), c.getReportadaPorId());
        assertNotNull(c.getFechaReporte());
        assertEquals(c.getId(), dto.id());
        assertTrue(e.acciones().contains("REPORTAR_RESENA"));
        verify(e.notificaciones).notificar(eq(admin.getId()), eq(TipoNotificacion.REPORTE_RECIBIDO), anyString(),
                contains("reseña"), anyString());
    }

    @Test
    void soloElDestinatarioReportaYSoloUnaVezYSoloSiEstaPublicada() {
        calificarComoCarlos(servicio); // oculta todavía
        Calificacion c = e.calificaciones.values().iterator().next();
        var reporte = new ReporteResenaEntradaDTO("Injusta");

        assertNegocio("RESENA_NO_PUBLICADA", HttpStatus.CONFLICT, () -> reputacion.reportarResena(autenticado(laura), c.getId(), reporte));
        calificarComoLaura(servicio);
        assertThrows(ExcepcionAutorizacion.class, () -> reputacion.reportarResena(autenticado(carlos), c.getId(), reporte), "el autor no");
        assertThrows(ExcepcionAutorizacion.class, () -> reputacion.reportarResena(autenticado(admin), c.getId(), reporte));
        reputacion.reportarResena(autenticado(laura), c.getId(), reporte);
        assertNegocio("RESENA_YA_REPORTADA", HttpStatus.CONFLICT, () -> reputacion.reportarResena(autenticado(laura), c.getId(), reporte));
        assertThrows(ExcepcionNoEncontrado.class, () -> reputacion.reportarResena(autenticado(laura), "000000000000000000000000", reporte));
    }

    @Test
    void laColaMuestraLasReseñasReportadasPendientesConNombres() {
        Calificacion c = reseñaPublicadaAlTutor();
        reputacion.reportarResena(autenticado(laura), c.getId(), new ReporteResenaEntradaDTO("Injusta"));

        List<ResenaReportadaDTO> cola = reputacion.listarResenasReportadas();

        assertEquals(1, cola.size());
        assertEquals("Carlos Gómez", cola.get(0).autor().nombre());
        assertEquals("Laura Martínez", cola.get(0).destinatario().nombre());
        assertEquals("Laura Martínez", cola.get(0).reportante().nombre());
        assertEquals("Injusta", cola.get(0).motivoReporte());
        assertNull(cola.get(0).moderacion());
    }

    @Test
    void desestimarElReporteMantienePublicaLaReseñaYElPromedio() {
        Calificacion c = reseñaPublicadaAlTutor();
        reputacion.reportarResena(autenticado(laura), c.getId(), new ReporteResenaEntradaDTO("Injusta"));

        ResenaReportadaDTO r = reputacion.moderarResena(admin.getId(), c.getId(),
                new ModeracionEntradaDTO(DecisionModeracion.DESESTIMAR, "Es una opinión legítima", null));

        assertEquals(DecisionModeracion.DESESTIMAR, r.moderacion().decision());
        assertTrue(c.isVisible());
        assertFalse(c.isOcultaPorModeracion());
        assertPromedio("4.83", laura.getPerfilTutor().getPromedioCalificacion());
        assertTrue(reputacion.listarResenasReportadas().isEmpty());
        assertTrue(e.acciones().contains("MODERAR_RESENA"));
        verify(e.notificaciones).notificar(eq(laura.getId()), eq(TipoNotificacion.REPORTE_RESUELTO), anyString(),
                contains("se mantiene"), anyString());
    }

    @Test
    void confirmarLaInfraccionOcultaLaReseñaDeFormaDefinitivaYRecalculaElPromedio() {
        Calificacion c = reseñaPublicadaAlTutor();
        reputacion.reportarResena(autenticado(laura), c.getId(), new ReporteResenaEntradaDTO("Insultos"));
        assertPromedio("4.83", laura.getPerfilTutor().getPromedioCalificacion());

        reputacion.moderarResena(admin.getId(), c.getId(),
                new ModeracionEntradaDTO(DecisionModeracion.CONFIRMAR_INFRACCION, "Lenguaje ofensivo", null));

        assertFalse(c.isVisible());
        assertTrue(c.isOcultaPorModeracion());
        assertNull(laura.getPerfilTutor().getPromedioCalificacion(), "ya no hay reseñas visibles");
        c.hacerVisible();
        assertFalse(c.isVisible(), "una reseña ocultada por moderación no vuelve a publicarse");
        assertEquals(0, reputacion.cerrarResenas(Instant.now().plus(Duration.ofDays(30))));
    }

    @Test
    void confirmarLaInfraccionPuedeSancionarAlAutorDeLaReseña() {
        Calificacion c = reseñaPublicadaAlTutor();
        reputacion.reportarResena(autenticado(laura), c.getId(), new ReporteResenaEntradaDTO("Insultos"));
        Instant fin = Instant.now().plus(Duration.ofDays(15));

        ResenaReportadaDTO r = reputacion.moderarResena(admin.getId(), c.getId(), new ModeracionEntradaDTO(
                DecisionModeracion.CONFIRMAR_INFRACCION, "Reseña ofensiva",
                new SancionEntradaDTO(TipoSancion.SUSPENSION_TEMPORAL, null, fin)));

        assertEquals(EstadoCuenta.SUSPENDIDA, carlos.getEstadoCuenta(), "se sanciona al autor, no al reseñado");
        assertEquals(EstadoCuenta.ACTIVA, laura.getEstadoCuenta());
        assertEquals(1, e.sanciones.size());
        assertEquals(e.sanciones.keySet().iterator().next(), r.moderacion().sancionId());
        assertEquals(carlos.getId(), e.sanciones.values().iterator().next().getUsuarioId());
        assertEquals(1, e.reportes.size());
    }

    @Test
    void unaReseñaSeModeraUnaVezYSoloSiEstaReportada() {
        Calificacion c = reseñaPublicadaAlTutor();
        var desestimar = new ModeracionEntradaDTO(DecisionModeracion.DESESTIMAR, null, null);

        assertNegocio("RESENA_NO_REPORTADA", HttpStatus.CONFLICT, () -> reputacion.moderarResena(admin.getId(), c.getId(), desestimar));
        reputacion.reportarResena(autenticado(laura), c.getId(), new ReporteResenaEntradaDTO("Injusta"));
        reputacion.moderarResena(admin.getId(), c.getId(), desestimar);
        assertNegocio("RESENA_YA_MODERADA", HttpStatus.CONFLICT, () -> reputacion.moderarResena(admin.getId(), c.getId(), desestimar));
        assertThrows(ExcepcionNoEncontrado.class, () -> reputacion.moderarResena(admin.getId(), "000000000000000000000000", desestimar));
    }

    @Test
    void noSeSancionaSiNoSeConfirmaLaInfraccionNiConUnaSancionInvalida() {
        Calificacion c = reseñaPublicadaAlTutor();
        reputacion.reportarResena(autenticado(laura), c.getId(), new ReporteResenaEntradaDTO("Injusta"));

        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> reputacion.moderarResena(admin.getId(), c.getId(),
                new ModeracionEntradaDTO(DecisionModeracion.DESESTIMAR, null,
                        new SancionEntradaDTO(TipoSancion.ADVERTENCIA, null, null))));
        assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY, () -> reputacion.moderarResena(admin.getId(), c.getId(),
                new ModeracionEntradaDTO(DecisionModeracion.CONFIRMAR_INFRACCION, null,
                        new SancionEntradaDTO(TipoSancion.SUSPENSION_TEMPORAL, null, null))));

        assertNull(c.getModeracion(), "sigue pendiente");
        assertTrue(c.isVisible());
        assertTrue(e.sanciones.isEmpty());
        assertEquals(1, reputacion.listarResenasReportadas().size());
    }
}
