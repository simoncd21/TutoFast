package com.tutofast.repositorio;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;

import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.NivelVerificacion;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.Usuario;

/** Implementación con {@link Criteria}; Spring Data la une a {@code RepositorioUsuario}. */
public class RepositorioUsuarioPersonalizadoImpl implements RepositorioUsuarioPersonalizado {

    private final MongoTemplate plantilla;

    public RepositorioUsuarioPersonalizadoImpl(MongoTemplate plantilla) {
        this.plantilla = plantilla;
    }

    @Override
    public Page<Usuario> buscar(Rol rol, EstadoCuenta estado, String texto, Pageable paginacion) {
        Query consulta = construirConsulta(rol, estado, texto);
        long total = plantilla.count(Query.of(consulta).limit(-1).skip(-1), Usuario.class);
        consulta.with(PageRequest.of(paginacion.getPageNumber(), paginacion.getPageSize(),
                Sort.by(Sort.Direction.DESC, "fechaRegistro")));
        List<Usuario> contenido = plantilla.find(consulta, Usuario.class);
        return PageableExecutionUtils.getPage(contenido, paginacion, () -> total);
    }

    @Override
    public Page<Usuario> buscarTutores(FiltroTutores filtro, Pageable paginacion) {
        Query consulta = construirConsultaTutores(filtro);
        long total = plantilla.count(Query.of(consulta).limit(-1).skip(-1), Usuario.class);
        String campo = filtro.orden() == FiltroTutores.Orden.SERVICIOS
                ? "perfilTutor.serviciosCompletados" : "perfilTutor.promedioCalificacion";
        consulta.with(PageRequest.of(paginacion.getPageNumber(), paginacion.getPageSize(),
                Sort.by(Sort.Order.desc(campo), Sort.Order.asc("nombre"))));
        List<Usuario> contenido = plantilla.find(consulta, Usuario.class);
        return PageableExecutionUtils.getPage(contenido, paginacion, () -> total);
    }

    /** Función pura (sin E/S). Solo tutores con cuenta activa y perfil de tutor. */
    static Query construirConsultaTutores(FiltroTutores f) {
        List<Criteria> condiciones = new ArrayList<>();
        condiciones.add(Criteria.where("roles").is(Rol.TUTOR));
        condiciones.add(Criteria.where("estadoCuenta").is(EstadoCuenta.ACTIVA));
        condiciones.add(Criteria.where("perfilTutor").exists(true));
        if (f.asignaturaIds() != null) {
            condiciones.add(Criteria.where("perfilTutor.asignaturasDominadasIds").in(f.asignaturaIds()));
        }
        if (f.modalidad() != null) {
            condiciones.add(Criteria.where("perfilTutor.modalidades").is(f.modalidad()));
        }
        if (f.disponibilidad() != null && !f.disponibilidad().isBlank()) {
            condiciones.add(Criteria.where("perfilTutor.disponibilidad")
                    .regex(Pattern.compile(Pattern.quote(f.disponibilidad().strip()), Pattern.CASE_INSENSITIVE)));
        }
        if (f.calificacionMin() != null) {
            condiciones.add(Criteria.where("perfilTutor.promedioCalificacion").gte(f.calificacionMin()));
        }
        if (f.soloVerificados()) {
            condiciones.add(Criteria.where("perfilTutor.nivelVerificacion").is(NivelVerificacion.VERIFICADO));
        }
        return new Query(new Criteria().andOperator(condiciones));
    }

    /** Función pura (sin E/S) para poder probarla sin MongoDB. */
    static Query construirConsulta(Rol rol, EstadoCuenta estado, String texto) {
        List<Criteria> condiciones = new ArrayList<>();
        if (rol != null) {
            condiciones.add(Criteria.where("roles").is(rol));
        }
        if (estado != null) {
            condiciones.add(Criteria.where("estadoCuenta").is(estado));
        }
        if (texto != null && !texto.isBlank()) {
            Pattern patron = Pattern.compile(Pattern.quote(texto.trim()), Pattern.CASE_INSENSITIVE);
            condiciones.add(new Criteria().orOperator(
                    Criteria.where("nombre").regex(patron), Criteria.where("correo").regex(patron)));
        }
        return condiciones.isEmpty() ? new Query() : new Query(new Criteria().andOperator(condiciones));
    }
}
