package com.tutofast.repositorio;

import java.util.List;
import java.util.Optional;

import com.tutofast.modelo.TipoCalificacion;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.modelo.Calificacion;

public interface RepositorioCalificacion extends MongoRepository<Calificacion, String> {

    List<Calificacion> findByServicioId(String servicioId);

    Optional<Calificacion> findByServicioIdAndTipo(String servicioId, TipoCalificacion tipo);

    boolean existsByServicioIdAndTipo(String servicioId, TipoCalificacion tipo);

    List<Calificacion> findByDestinatarioIdAndVisibleTrueOrderByFechaEmisionDesc(String destinatarioId);
}
