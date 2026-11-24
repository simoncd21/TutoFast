package com.tutofast.servicio;

import org.springframework.stereotype.Component;

import com.tutofast.modelo.Rol;
import com.tutofast.repositorio.RepositorioSolicitudTutoria;
import com.tutofast.seguridad.UsuarioAutenticado;

/**
 * Adjuntos de las solicitudes: los ve su dueño y cualquier administrador; mientras la solicitud sigue abierta
 * (explorable) también los tutores, que los necesitan para preparar su propuesta.
 */
@Component
public class ContextoSolicitudes implements ContextoArchivo {

    private final RepositorioSolicitudTutoria repositorio;

    public ContextoSolicitudes(RepositorioSolicitudTutoria repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public boolean referencia(String archivoId) {
        return repositorio.findByArchivosGridFsId(archivoId).isPresent();
    }

    @Override
    public boolean puedeAcceder(String archivoId, UsuarioAutenticado usuario) {
        return repositorio.findByArchivosGridFsId(archivoId)
                .map(s -> s.getEstudianteId().equals(usuario.id())
                        || usuario.tieneAlgunRol(Rol.ADMINISTRADOR)
                        || (usuario.tieneAlgunRol(Rol.TUTOR) && s.getEstado().visibleParaExplorar()))
                .orElse(false);
    }
}
