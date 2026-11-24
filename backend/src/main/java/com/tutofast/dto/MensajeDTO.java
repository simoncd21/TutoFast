package com.tutofast.dto;

import java.time.Instant;

/**
 * Mensaje de un hilo. {@code sistema} marca los avisos de la plataforma (sin emisor); {@code retirado} indica que un
 * administrador confirmó una infracción y el contenido se ocultó a los participantes.
 */
public record MensajeDTO(String id, String emisorId, String emisorNombre, String contenido, Instant marcaTemporal,
                         boolean reportado, boolean sistema, boolean retirado, ArchivoDTO archivo) {
}
