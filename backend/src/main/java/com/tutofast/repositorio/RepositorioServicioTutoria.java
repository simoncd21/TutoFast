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
}
