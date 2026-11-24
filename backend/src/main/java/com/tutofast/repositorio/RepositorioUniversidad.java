package com.tutofast.repositorio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.modelo.Universidad;

public interface RepositorioUniversidad extends MongoRepository<Universidad, String> {

    boolean existsByNombre(String nombre);

    Optional<Universidad> findByNombre(String nombre);

    List<Universidad> findByActivaTrueOrderByNombreAsc();

    List<Universidad> findAllByOrderByNombreAsc();
}
