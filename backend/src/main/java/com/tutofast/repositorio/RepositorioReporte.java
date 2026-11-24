package com.tutofast.repositorio;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.tutofast.modelo.EstadoReporte;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.modelo.Reporte;

public interface RepositorioReporte extends MongoRepository<Reporte, String> {

    Page<Reporte> findByEstado(EstadoReporte estado, Pageable paginacion);

    List<Reporte> findByReportanteIdOrderByFechaRadicacionDesc(String reportanteId);

    List<Reporte> findByDenunciadoIdOrderByFechaRadicacionDesc(String denunciadoId);

    boolean existsByServicioIdAndEstadoIn(String servicioId, Collection<EstadoReporte> estados);
}
