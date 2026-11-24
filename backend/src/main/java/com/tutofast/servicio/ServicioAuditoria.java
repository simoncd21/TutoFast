package com.tutofast.servicio;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.tutofast.dto.Pagina;
import com.tutofast.dto.RegistroAuditoriaDTO;
import com.tutofast.mapper.AuditoriaMapper;
import com.tutofast.modelo.RegistroAuditoria;
import com.tutofast.repositorio.RepositorioRegistroAuditoria;
import com.tutofast.repositorio.RepositorioUsuario;

/** Bitácora de acciones relevantes (solo inserción). Nunca recibe claves, tokens ni datos de tarjeta. */
@Service
public class ServicioAuditoria {

    static final int TAMANO_MAXIMO_PAGINA = 100;

    private final RepositorioRegistroAuditoria repositorio;
    private final RepositorioUsuario usuarios;

    public ServicioAuditoria(RepositorioRegistroAuditoria repositorio, RepositorioUsuario usuarios) {
        this.repositorio = repositorio;
        this.usuarios = usuarios;
    }

    /** Consulta para el panel de administración: añade el nombre de quien ejecutó cada acción. */
    public Pagina<RegistroAuditoriaDTO> consultarPagina(String usuarioId, String entidadAfectada, Instant desde,
                                                        Instant hasta, int pagina, int tamano) {
        Page<RegistroAuditoria> resultado = consultar(usuarioId, entidadAfectada, desde, hasta,
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), TAMANO_MAXIMO_PAGINA)));
        Set<String> ids = resultado.getContent().stream().map(RegistroAuditoria::getUsuarioId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> nombres = new HashMap<>();
        if (!ids.isEmpty()) {
            usuarios.findAllById(ids).forEach(u -> nombres.put(u.getId(), u.getNombre()));
        }
        return Pagina.de(resultado, r -> AuditoriaMapper.aDTO(r, nombres));
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
