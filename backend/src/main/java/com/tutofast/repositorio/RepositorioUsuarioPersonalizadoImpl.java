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
