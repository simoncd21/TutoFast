package com.tutofast.servicio;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.tutofast.modelo.RegistroAuditoria;
import com.tutofast.repositorio.RepositorioRegistroAuditoria;

/** Bitácora de acciones relevantes (solo inserción). Nunca recibe claves, tokens ni datos de tarjeta. */
@Service
public class ServicioAuditoria {

    private final RepositorioRegistroAuditoria repositorio;

    public ServicioAuditoria(RepositorioRegistroAuditoria repositorio) {
        this.repositorio = repositorio;
    }

    /** Registra una acción; {@code usuarioId} es {@code null} cuando actúa el sistema. */
    public RegistroAuditoria registrarAccion(String usuarioId, String accion, String entidadAfectada,
                                             Map<String, Object> detalle) {
        if (accion == null || accion.isBlank()) {
            throw new IllegalArgumentException("La acción de auditoría es obligatoria");
        }
        Map<String, Object> copia = detalle == null ? new LinkedHashMap<>() : new LinkedHashMap<>(detalle);
        return repositorio.save(new RegistroAuditoria(usuarioId, accion, entidadAfectada, Instant.now(), copia));
    }

    /** Consulta paginada (más recientes primero) con filtros opcionales combinables. */
    public Page<RegistroAuditoria> consultar(String usuarioId, String entidadAfectada, Instant desde, Instant hasta,
                                             Pageable paginacion) {
        return repositorio.buscar(usuarioId, entidadAfectada, desde, hasta, paginacion);
    }
}
