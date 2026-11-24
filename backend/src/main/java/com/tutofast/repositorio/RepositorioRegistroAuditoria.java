package com.tutofast.repositorio;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.modelo.RegistroAuditoria;

public interface RepositorioRegistroAuditoria extends MongoRepository<RegistroAuditoria, String> {

    Page<RegistroAuditoria> findByUsuarioIdOrderByFechaDesc(String usuarioId, Pageable paginacion);

    Page<RegistroAuditoria> findByEntidadAfectadaOrderByFechaDesc(String entidadAfectada, Pageable paginacion);

    Page<RegistroAuditoria> findByFechaBetweenOrderByFechaDesc(Instant desde, Instant hasta, Pageable paginacion);
}
