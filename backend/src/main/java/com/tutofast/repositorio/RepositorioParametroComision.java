package com.tutofast.repositorio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.modelo.ParametroComision;

public interface RepositorioParametroComision extends MongoRepository<ParametroComision, String> {

    /** El parámetro vigente: {@code vigente == true}, equivalente a {@code vigenteHasta == null}. */
    Optional<ParametroComision> findByVigenteTrue();

    List<ParametroComision> findAllByOrderByVigenteDesdeDesc();
}
