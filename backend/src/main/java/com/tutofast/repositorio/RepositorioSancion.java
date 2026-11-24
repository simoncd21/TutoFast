package com.tutofast.repositorio;

import java.util.Collection;
import java.util.List;

import com.tutofast.modelo.EstadoSancion;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.modelo.Sancion;

public interface RepositorioSancion extends MongoRepository<Sancion, String> {

    List<Sancion> findByUsuarioIdOrderByIdDesc(String usuarioId);

    List<Sancion> findByUsuarioIdAndEstadoIn(String usuarioId, Collection<EstadoSancion> estados);

    List<Sancion> findByReporteId(String reporteId);

    /** Sanciones en esos estados cuya vigencia ya terminó (las temporales que deben levantarse). */
    List<Sancion> findByEstadoInAndVigenciaFinLessThanEqual(Collection<EstadoSancion> estados, java.time.Instant ahora);
}
