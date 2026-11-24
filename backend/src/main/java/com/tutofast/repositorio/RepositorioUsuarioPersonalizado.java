package com.tutofast.repositorio;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.Usuario;

/** Consulta de usuarios para el panel de administración, con filtros opcionales combinables. */
public interface RepositorioUsuarioPersonalizado {

    /** {@code texto} se busca (sin distinguir mayúsculas) en nombre y correo. */
    Page<Usuario> buscar(Rol rol, EstadoCuenta estado, String texto, Pageable paginacion);
}
