package com.tutofast.repositorio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;

import com.tutofast.modelo.RegistroAuditoria;

/** Implementación con {@link Criteria}; Spring Data la une a {@code RepositorioRegistroAuditoria}. */
public class RepositorioRegistroAuditoriaPersonalizadoImpl implements RepositorioRegistroAuditoriaPersonalizado {

    private final MongoTemplate plantilla;

    public RepositorioRegistroAuditoriaPersonalizadoImpl(MongoTemplate plantilla) {
        this.plantilla = plantilla;
    }

    @Override
    public Page<RegistroAuditoria> buscar(String usuarioId, String entidadAfectada, Instant desde, Instant hasta,
                                          Pageable paginacion) {
        Query consulta = construirConsulta(usuarioId, entidadAfectada, desde, hasta);
        long total = plantilla.count(Query.of(consulta).limit(-1).skip(-1), RegistroAuditoria.class);
        consulta.with(PageRequest.of(paginacion.getPageNumber(), paginacion.getPageSize(),
                Sort.by(Sort.Direction.DESC, "fecha")));
        List<RegistroAuditoria> contenido = plantilla.find(consulta, RegistroAuditoria.class);
        return PageableExecutionUtils.getPage(contenido, paginacion, () -> total);
    }

    /** Función pura (sin E/S) para poder probarla sin MongoDB. */
    static Query construirConsulta(String usuarioId, String entidadAfectada, Instant desde, Instant hasta) {
        List<Criteria> condiciones = new ArrayList<>();
        if (usuarioId != null && !usuarioId.isBlank()) {
            condiciones.add(Criteria.where("usuarioId").is(usuarioId));
        }
        if (entidadAfectada != null && !entidadAfectada.isBlank()) {
            condiciones.add(Criteria.where("entidadAfectada").is(entidadAfectada));
        }
        if (desde != null || hasta != null) {
            Criteria fecha = Criteria.where("fecha");
            if (desde != null) {
                fecha = fecha.gte(desde);
            }
            if (hasta != null) {
                fecha = fecha.lte(hasta);
            }
            condiciones.add(fecha);
        }
        return condiciones.isEmpty() ? new Query() : new Query(new Criteria().andOperator(condiciones));
    }
}
