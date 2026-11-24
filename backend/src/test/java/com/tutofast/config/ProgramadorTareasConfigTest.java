package com.tutofast.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.tutofast.TutoFastApplication;
import com.tutofast.dto.CierreAutomaticoDTO;
import com.tutofast.servicio.AplicadorSanciones;
import com.tutofast.servicio.ServicioNegociacion;
import com.tutofast.servicio.ServicioReputacion;

class ProgramadorTareasConfigTest {

    private ServicioNegociacion negociacion;
    private ServicioReputacion reputacion;
    private AplicadorSanciones sanciones;
    private ProgramadorTareasConfig programador;

    @BeforeEach
    void preparar() {
        negociacion = mock(ServicioNegociacion.class);
        reputacion = mock(ServicioReputacion.class);
        sanciones = mock(AplicadorSanciones.class);
        programador = new ProgramadorTareasConfig(negociacion, reputacion, sanciones);
        when(negociacion.cerrarAutomaticamente(anyBoolean())).thenReturn(new CierreAutomaticoDTO(0, 0, 0, 0, 0, List.of()));
    }

    @Test
    void laAplicacionHabilitaLaProgramacion() {
        assertNotNull(TutoFastApplication.class.getAnnotation(EnableScheduling.class));
    }

    @Test
    void cadaTareaTieneSuPlanificacion() throws Exception {
        Scheduled inicio = programarDe("iniciarServiciosPorHora");
        assertTrue(inicio.fixedDelayString().contains("60000"), "cada minuto");
        Scheduled cierre = programarDe("cerrarServiciosVencidos");
        assertTrue(cierre.cron().contains("0 */15 * * * *"), "cada 15 minutos");
        Scheduled horaria = programarDe("tareasHorarias");
        assertTrue(horaria.cron().contains("* * *"), "cada hora");
    }

    private static Scheduled programarDe(String metodo) throws Exception {
        Method m = ProgramadorTareasConfig.class.getMethod(metodo);
        Scheduled s = m.getAnnotation(Scheduled.class);
        assertNotNull(s, metodo);
        return s;
    }

    @Test
    void sePuedenApagarTodasLasTareasPorConfiguracion() {
        ConditionalOnProperty condicion = ProgramadorTareasConfig.class.getAnnotation(ConditionalOnProperty.class);
        assertEquals("tutofast.tareas.habilitadas", condicion.name()[0]);
        assertTrue(condicion.matchIfMissing(), "por omisión están activas");
    }

    @Test
    void elInicioAutomaticoDelegaEnElServicio() {
        programador.iniciarServiciosPorHora();

        verify(negociacion).iniciarAutomaticamente(any(Instant.class));
    }

    @Test
    void elCierreAutomaticoNuncaIgnoraElPlazo() {
        programador.cerrarServiciosVencidos();

        verify(negociacion).cerrarAutomaticamente(false);
    }

    @Test
    void lasTareasHorariasEjecutanTodasLasLabores() {
        programador.tareasHorarias();

        verify(negociacion).recordarSesiones(any(Instant.class));
        verify(reputacion).cerrarResenas(any(Instant.class));
        verify(reputacion).recordarCalificaciones(any(Instant.class));
        verify(sanciones).levantarVencidas(any(Instant.class));
    }

    @Test
    void unaTareaQueFallaNoDetieneElPlanificadorNiLasDemas() {
        when(negociacion.iniciarAutomaticamente(any(Instant.class))).thenThrow(new RuntimeException("Mongo caído"));
        when(negociacion.cerrarAutomaticamente(anyBoolean())).thenThrow(new RuntimeException("Mongo caído"));
        when(negociacion.recordarSesiones(any(Instant.class))).thenThrow(new RuntimeException("Mongo caído"));
        when(reputacion.cerrarResenas(any(Instant.class))).thenThrow(new RuntimeException("Mongo caído"));

        programador.iniciarServiciosPorHora(); // no propaga
        programador.cerrarServiciosVencidos(); // no propaga
        programador.tareasHorarias();

        // Aunque fallaron los recordatorios de sesión y la publicación de reseñas, las otras dos se ejecutaron.
        verify(reputacion).recordarCalificaciones(any(Instant.class));
        verify(sanciones).levantarVencidas(any(Instant.class));
    }
}
