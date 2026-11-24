package com.tutofast.dto;

import java.time.Instant;

public record NotificacionDTO(String id, String tipo, String titulo, String mensaje, String enlace, boolean leida,
                              Instant fecha) {
}
