package com.tutofast.repositorio;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.modelo.Asignatura;

public interface RepositorioAsignatura extends MongoRepository<Asignatura, String> {

    boolean existsByProgramaIdAndCodigo(String programaId, String codigo);

    List<Asignatura> findByProgramaIdAndActivaTrueOrderByNombreAsc(String programaId);
}
