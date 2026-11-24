package com.tutofast.config;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tutofast.dto.CierreAutomaticoDTO;
import com.tutofast.servicio.AplicadorSanciones;
import com.tutofast.servicio.ServicioNegociacion;
import com.tutofast.servicio.ServicioReputacion;

/**
 * Tareas programadas del ciclo del servicio (la limpieza de archivos huérfanos vive en {@code ServicioArchivos}).
 * {@code @EnableScheduling} está en {@code TutoFastApplication}. Cada tarea delega en un servicio, registra el
 * resultado y captura cualquier fallo para no detener el planificador. Se pueden apagar todas con
 * {@code tutofast.tareas.habilitadas=false} (por ejemplo, al levantar varias instancias).
 *
 * <ul>
 *   <li>cada minuto: CONFIRMADO → EN_DESARROLLO al llegar la hora programada;</li>
 *   <li>cada 15 minutos: cierre automático de servicios sin cierre 24 h después de su fin programado;</li>
 *   <li>cada hora: recordatorios de sesión, publicación de reseñas vencidas, recordatorios de calificación y
 *       fin de las suspensiones temporales vencidas.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "tutofast.tareas.habilitadas", havingValue = "true", matchIfMissing = true)
public class ProgramadorTareasConfig {

    private static final Logger log = LoggerFactory.getLogger(ProgramadorTareasConfig.class);

    private final ServicioNegociacion negociacion;
    private final ServicioReputacion reputacion;
    private final AplicadorSanciones sanciones;

    public ProgramadorTareasConfig(ServicioNegociacion negociacion, ServicioReputacion reputacion,
                                   AplicadorSanciones sanciones) {
        this.negociacion = negociacion;
        this.reputacion = reputacion;
        this.sanciones = sanciones;
    }

    @Scheduled(fixedDelayString = "${tutofast.tareas.inicio-automatico-ms:60000}", initialDelay = 30000)
    public void iniciarServiciosPorHora() {
        ejecutar("inicio automático", () -> {
            int iniciados = negociacion.iniciarAutomaticamente(Instant.now());
            if (iniciados > 0) {
                log.info("Inicio automático: {} servicios pasaron a EN_DESARROLLO", iniciados);
            }
        });
    }

    @Scheduled(cron = "${tutofast.tareas.cierre-automatico-cron:0 */15 * * * *}")
    public void cerrarServiciosVencidos() {
        ejecutar("cierre automático", () -> {
            CierreAutomaticoDTO r = negociacion.cerrarAutomaticamente(false);
            if (r.cerrados() > 0 || r.fallidos() > 0) {
                log.info("Cierre automático: {} cerrados, {} omitidos por reporte, {} fallidos de {} en desarrollo",
                        r.cerrados(), r.omitidosPorReporte(), r.fallidos(), r.revisados());
            }
        });
    }

    @Scheduled(cron = "${tutofast.tareas.horaria-cron:0 5 * * * *}")
    public void tareasHorarias() {
        ejecutar("recordatorios de sesión", () -> negociacion.recordarSesiones(Instant.now()));
        ejecutar("publicación de reseñas", () -> {
            int publicadas = reputacion.cerrarResenas(Instant.now());
            if (publicadas > 0) {
                log.info("Reseñas publicadas por vencimiento del plazo: {}", publicadas);
            }
        });
        ejecutar("recordatorios de calificación", () -> reputacion.recordarCalificaciones(Instant.now()));
        ejecutar("fin de sanciones", () -> {
            int cerradas = sanciones.levantarVencidas(Instant.now());
            if (cerradas > 0) {
                log.info("Suspensiones levantadas por vencimiento: {}", cerradas);
            }
        });
    }

    private static void ejecutar(String nombre, Runnable tarea) {
        try {
            tarea.run();
        } catch (RuntimeException e) {
            log.error("Falló la tarea programada «{}»", nombre, e);
        }
    }
}
