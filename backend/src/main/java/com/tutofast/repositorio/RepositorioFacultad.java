package com.tutofast.repositorio;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.modelo.Facultad;

public interface RepositorioFacultad extends MongoRepository<Facultad, String> {

    boolean existsByUniversidadIdAndNombre(String universidadId, String nombre);

    List<Facultad> findByUniversidadIdAndActivaTrueOrderByNombreAsc(String universidadId);
}
