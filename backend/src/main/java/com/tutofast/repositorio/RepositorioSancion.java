package com.tutofast.repositorio;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.tutofast.modelo.EstadoSancion;
import com.tutofast.modelo.Sancion;

public interface RepositorioSancion extends MongoRepository<Sancion, String> {

    List<Sancion> findByUsuarioIdOrderByIdDesc(String usuarioId);

    List<Sancion> findByUsuarioIdAndEstadoIn(String usuarioId, Collection<EstadoSancion> estados);

    List<Sancion> findByReporteId(String reporteId);

    Page<Sancion> findByEstado(EstadoSancion estado, Pageable paginacion);

    /** Sanciones en esos estados cuya vigencia ya terminó (las temporales que deben levantarse). */
    List<Sancion> findByEstadoInAndVigenciaFinLessThanEqual(Collection<EstadoSancion> estados, java.time.Instant ahora);

    /** Sanciones cuyas apelaciones adjuntan ese archivo de GridFS (control de acceso y limpieza de huérfanos). */
    @Query("{ 'apelaciones.evidencias': ?0 }")
    List<Sancion> buscarPorEvidenciaDeApelacion(String archivoId);
}
