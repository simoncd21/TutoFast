package com.tutofast.repositorio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.modelo.Facultad;

public interface RepositorioFacultad extends MongoRepository<Facultad, String> {

    boolean existsByUniversidadIdAndNombre(String universidadId, String nombre);

    Optional<Facultad> findByUniversidadIdAndNombre(String universidadId, String nombre);

    List<Facultad> findByUniversidadIdAndActivaTrueOrderByNombreAsc(String universidadId);

    List<Facultad> findByUniversidadIdOrderByNombreAsc(String universidadId);
}
