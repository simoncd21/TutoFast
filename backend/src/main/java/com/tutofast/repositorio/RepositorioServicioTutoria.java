package com.tutofast.repositorio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.tutofast.modelo.EstadoServicio;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.modelo.ServicioTutoria;

public interface RepositorioServicioTutoria extends MongoRepository<ServicioTutoria, String> {

    Optional<ServicioTutoria> findBySolicitudId(String solicitudId);

    List<ServicioTutoria> findByEstudianteIdOrTutorIdOrderByFechaConfirmacionDesc(String estudianteId, String tutorId);

    List<ServicioTutoria> findByEstado(EstadoServicio estado);

    /** Para el programador de tareas: servicios en un estado cuya hora ya llegó. */
    List<ServicioTutoria> findByEstadoAndFechaHoraLessThanEqual(EstadoServicio estado, Instant limite);

    /** Servicios que empiezan dentro de la ventana y aún no han recibido su recordatorio. */
    List<ServicioTutoria> findByEstadoAndRecordatorioEnviadoFalseAndFechaHoraBetween(
            EstadoServicio estado, Instant desde, Instant hasta);

    /** Servicios finalizados en la ventana cuyo recordatorio de calificación no se ha enviado. */
    List<ServicioTutoria> findByEstadoAndRecordatorioCalificacionEnviadoFalseAndFechaFinalizacionBetween(
            EstadoServicio estado, Instant desde, Instant hasta);

    /** Servicios finalizados de un tutor (alimenta {@code serviciosCompletados}). */
    long countByTutorIdAndEstado(String tutorId, EstadoServicio estado);
}
