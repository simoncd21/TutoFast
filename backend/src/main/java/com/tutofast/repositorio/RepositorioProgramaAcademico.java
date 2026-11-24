package com.tutofast.repositorio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.modelo.ProgramaAcademico;

public interface RepositorioProgramaAcademico extends MongoRepository<ProgramaAcademico, String> {

    boolean existsByFacultadIdAndNombre(String facultadId, String nombre);

    Optional<ProgramaAcademico> findByFacultadIdAndNombre(String facultadId, String nombre);

    List<ProgramaAcademico> findByFacultadIdAndActivoTrueOrderByNombreAsc(String facultadId);

    List<ProgramaAcademico> findByFacultadIdOrderByNombreAsc(String facultadId);
}
