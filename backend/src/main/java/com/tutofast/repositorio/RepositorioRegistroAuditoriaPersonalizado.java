package com.tutofast.repositorio;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.tutofast.modelo.RegistroAuditoria;

/** Consulta de auditoría con filtros opcionales combinables. */
public interface RepositorioRegistroAuditoriaPersonalizado {

    Page<RegistroAuditoria> buscar(String usuarioId, String entidadAfectada, Instant desde, Instant hasta,
                                   Pageable paginacion);
}
