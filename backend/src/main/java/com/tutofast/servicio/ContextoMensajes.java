package com.tutofast.servicio;

import org.springframework.stereotype.Component;

import com.tutofast.modelo.Mensaje;
import com.tutofast.modelo.Rol;
import com.tutofast.repositorio.RepositorioMensaje;
import com.tutofast.repositorio.RepositorioSolicitudTutoria;
import com.tutofast.seguridad.UsuarioAutenticado;

/**
 * Adjuntos del chat: los descargan los dos participantes del hilo (el estudiante dueño y el tutor) y los
 * administradores. Si un administrador retiró el mensaje por infracción, el adjunto solo lo ve el administrador.
 */
@Component
public class ContextoMensajes implements ContextoArchivo {

    private final RepositorioMensaje mensajes;
    private final RepositorioSolicitudTutoria solicitudes;

    public ContextoMensajes(RepositorioMensaje mensajes, RepositorioSolicitudTutoria solicitudes) {
        this.mensajes = mensajes;
        this.solicitudes = solicitudes;
    }

    @Override
    public boolean referencia(String archivoId) {
        return mensajes.findByArchivoGridFsId(archivoId).isPresent();
    }

    @Override
    public boolean puedeAcceder(String archivoId, UsuarioAutenticado usuario) {
        Mensaje mensaje = mensajes.findByArchivoGridFsId(archivoId).orElse(null);
        if (mensaje == null) {
            return false;
        }
        if (usuario.tieneAlgunRol(Rol.ADMINISTRADOR)) {
            return true;
        }
        if (mensaje.estaRetirado()) {
            return false;
        }
        boolean tutorDelHilo = usuario.id().equals(mensaje.getTutorId());
        return tutorDelHilo || solicitudes.findById(mensaje.getSolicitudId())
                .map(s -> s.getEstudianteId().equals(usuario.id())).orElse(false);
    }
}
