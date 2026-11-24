package com.tutofast.repositorio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.modelo.EstadoSolicitud;
import com.tutofast.modelo.SolicitudTutoria;

public interface RepositorioSolicitudTutoria
        extends MongoRepository<SolicitudTutoria, String>, RepositorioSolicitudTutoriaPersonalizado {

    List<SolicitudTutoria> findByEstudianteIdOrderByFechaCreacionDesc(String estudianteId);

    Optional<SolicitudTutoria> findByArchivosGridFsId(String gridFsId);

    long countByEstado(EstadoSolicitud estado);
}
