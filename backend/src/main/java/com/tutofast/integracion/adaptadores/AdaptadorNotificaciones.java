package com.tutofast.integracion.adaptadores;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.tutofast.integracion.puertos.INotificador;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.repositorio.RepositorioNotificacion;

/** Guarda cada notificación en la colección {@code notificaciones}; la web las consulta por polling. */
@Component
public class AdaptadorNotificaciones implements INotificador {

    private final RepositorioNotificacion repositorio;

    public AdaptadorNotificaciones(RepositorioNotificacion repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public void notificar(String usuarioId, TipoNotificacion tipo, String titulo, String mensaje, String enlace) {
        repositorio.save(new DocumentoNotificacion(usuarioId, tipo.name(), titulo, mensaje, enlace, Instant.now()));
    }
}
