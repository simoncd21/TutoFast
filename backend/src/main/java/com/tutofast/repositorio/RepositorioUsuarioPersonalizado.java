package com.tutofast.repositorio;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.Usuario;

/** Consultas dinámicas de usuarios: panel de administración y búsqueda de tutores. */
public interface RepositorioUsuarioPersonalizado {

    /** {@code texto} se busca (sin distinguir mayúsculas) en nombre y correo. */
    Page<Usuario> buscar(Rol rol, EstadoCuenta estado, String texto, Pageable paginacion);

    /** Tutores con la cuenta activa que cumplen los filtros; el orden lo fija {@code filtro.orden()}. */
    Page<Usuario> buscarTutores(FiltroTutores filtro, Pageable paginacion);
}
