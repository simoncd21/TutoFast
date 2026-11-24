package com.tutofast.dto;

import java.time.Instant;
import java.util.Map;

public record RegistroAuditoriaDTO(String id, String usuarioId, String usuarioNombre, String accion,
                                   String entidadAfectada, Instant fecha, Map<String, Object> detalle) {
}
