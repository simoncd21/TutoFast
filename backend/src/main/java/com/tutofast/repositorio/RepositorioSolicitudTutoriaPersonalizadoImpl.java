package com.tutofast.repositorio;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.support.PageableExecutionUtils;

import com.tutofast.modelo.EstadoSolicitud;
import com.tutofast.modelo.SolicitudTutoria;

/** Implementación con {@link Criteria} dinámicos; Spring Data la une a {@code RepositorioSolicitudTutoria}. */
public class RepositorioSolicitudTutoriaPersonalizadoImpl implements RepositorioSolicitudTutoriaPersonalizado {

    private static final Set<EstadoSolicitud> ESTADOS_EXPLORABLES = Set.of(
            EstadoSolicitud.PUBLICADA, EstadoSolicitud.PROPUESTAS_RECIBIDAS, EstadoSolicitud.NEGOCIACION_EN_CURSO);

    private final MongoTemplate plantilla;

    public RepositorioSolicitudTutoriaPersonalizadoImpl(MongoTemplate plantilla) {
        this.plantilla = plantilla;
    }

    @Override
    public Page<SolicitudTutoria> buscar(FiltroSolicitudes filtro, Pageable paginacion) {
        FiltroSolicitudes f = filtro == null ? FiltroSolicitudes.vacio() : filtro;
        Query consulta = construirConsulta(f);
        long total = plantilla.count(Query.of(consulta).limit(-1).skip(-1), SolicitudTutoria.class);
        consulta.with(PageRequest.of(paginacion.getPageNumber(), paginacion.getPageSize(), ordenar(f)));
        List<SolicitudTutoria> contenido = plantilla.find(consulta, SolicitudTutoria.class);
        return PageableExecutionUtils.getPage(contenido, paginacion, () -> total);
    }

    /** Construye el filtro sin paginar ni ordenar; es una función pura para poder probarla sin MongoDB. */
    static Query construirConsulta(FiltroSolicitudes f) {
        List<Criteria> condiciones = new ArrayList<>();
        Set<EstadoSolicitud> estados = f.estados() == null || f.estados().isEmpty() ? ESTADOS_EXPLORABLES : f.estados();
        condiciones.add(Criteria.where("estado").in(estados));
        agregarIgualdad(condiciones, "universidadId", f.universidadId());
        agregarIgualdad(condiciones, "facultadId", f.facultadId());
        agregarIgualdad(condiciones, "programaId", f.programaId());
        agregarIgualdad(condiciones, "asignaturaId", f.asignaturaId());
        if (f.modalidad() != null) {
            condiciones.add(Criteria.where("modalidad").is(f.modalidad()));
        }
        if (f.fechaDesde() != null || f.fechaHasta() != null) {
            Criteria fecha = Criteria.where("fechaHora");
            if (f.fechaDesde() != null) {
                fecha = fecha.gte(f.fechaDesde());
            }
            if (f.fechaHasta() != null) {
                fecha = fecha.lte(f.fechaHasta());
            }
            condiciones.add(fecha);
        }
        if (f.precioMin() != null || f.precioMax() != null) {
            Criteria precio = Criteria.where("precioInicialCOP");
            if (f.precioMin() != null) {
                precio = precio.gte(f.precioMin());
            }
            if (f.precioMax() != null) {
                precio = precio.lte(f.precioMax());
            }
            condiciones.add(precio);
        }
        Query consulta = new Query(new Criteria().andOperator(condiciones));
        if (f.tema() != null && !f.tema().isBlank()) {
            consulta.addCriteria(TextCriteria.forLanguage("spanish").matching(f.tema().strip()));
        }
        return consulta;
    }

    static Sort ordenar(FiltroSolicitudes f) {
        FiltroSolicitudes.Orden orden = f.orden() == null ? FiltroSolicitudes.Orden.RECIENTES : f.orden();
        return switch (orden) {
            case RECIENTES -> Sort.by(Sort.Direction.DESC, "fechaCreacion");
            case PRECIO_ASC -> Sort.by(Sort.Direction.ASC, "precioInicialCOP");
            case PRECIO_DESC -> Sort.by(Sort.Direction.DESC, "precioInicialCOP");
            case FECHA -> Sort.by(Sort.Direction.ASC, "fechaHora");
        };
    }

    private static void agregarIgualdad(List<Criteria> condiciones, String campo, String valor) {
        if (valor != null && !valor.isBlank()) {
            condiciones.add(Criteria.where(campo).is(valor));
        }
    }
}
