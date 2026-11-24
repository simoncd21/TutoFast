package com.tutofast.servicio;

import org.springframework.stereotype.Component;

import com.tutofast.modelo.Rol;
import com.tutofast.repositorio.RepositorioUsuario;
import com.tutofast.seguridad.UsuarioAutenticado;

/** Soportes de verificación del tutor: los ve su dueño y cualquier administrador. */
@Component
public class ContextoSoportesVerificacion implements ContextoArchivo {

    private final RepositorioUsuario repositorio;

    public ContextoSoportesVerificacion(RepositorioUsuario repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public boolean referencia(String archivoId) {
        return repositorio.findByPerfilTutorSoportesRutaArchivo(archivoId).isPresent();
    }

    @Override
    public boolean puedeAcceder(String archivoId, UsuarioAutenticado usuario) {
        return repositorio.findByPerfilTutorSoportesRutaArchivo(archivoId)
                .map(dueno -> dueno.getId().equals(usuario.id()) || usuario.tieneAlgunRol(Rol.ADMINISTRADOR))
                .orElse(false);
    }
}
