package com.tutofast.dto;

import java.util.List;

/** Respuesta de {@code GET /api/solicitudes/{id}/mensajes}: los mensajes y si el hilo admite escritura. */
public record HiloMensajesDTO(boolean soloLectura, String motivoSoloLectura, List<MensajeDTO> mensajes) {
}
