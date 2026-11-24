package com.tutofast.repositorio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.modelo.Propuesta;

public interface RepositorioPropuesta extends MongoRepository<Propuesta, String> {

    List<Propuesta> findBySolicitudIdOrderByFechaCreacionDesc(String solicitudId);

    List<Propuesta> findBySolicitudIdAndActivaTrue(String solicitudId);

    Optional<Propuesta> findBySolicitudIdAndTutorIdAndActivaTrue(String solicitudId, String tutorId);

    boolean existsBySolicitudIdAndTutorIdAndActivaTrue(String solicitudId, String tutorId);

    List<Propuesta> findByTutorIdOrderByFechaCreacionDesc(String tutorId);
}
