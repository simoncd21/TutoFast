package com.tutofast.repositorio;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.tutofast.modelo.EstadoReporte;
import com.tutofast.modelo.Reporte;

public interface RepositorioReporte extends MongoRepository<Reporte, String> {

    Page<Reporte> findByEstado(EstadoReporte estado, Pageable paginacion);

    List<Reporte> findByReportanteIdOrderByFechaRadicacionDesc(String reportanteId);

    List<Reporte> findByDenunciadoIdOrderByFechaRadicacionDesc(String denunciadoId);

    List<Reporte> findByServicioId(String servicioId);

    boolean existsByServicioIdAndEstadoIn(String servicioId, Collection<EstadoReporte> estados);

    /** Un mismo reportante no abre dos reportes iguales (mismo denunciado y servicio; el servicio puede ser nulo). */
    boolean existsByReportanteIdAndDenunciadoIdAndServicioIdAndEstadoIn(String reportanteId, String denunciadoId,
                                                                        String servicioId, Collection<EstadoReporte> estados);

    /** Reportes que adjuntan ese archivo de GridFS (control de acceso y limpieza de huérfanos). */
    @Query("{ 'evidencias': ?0 }")
    List<Reporte> buscarPorEvidencia(String archivoId);
}
