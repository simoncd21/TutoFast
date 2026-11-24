package com.tutofast.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Parte {@code datos} de {@code POST /api/solicitudes/{id}/mensajes}. {@code tutorId} es obligatorio para el
 * estudiante (indica el hilo); un tutor escribe siempre en su propio hilo.
 */
public record MensajeEntradaDTO(@Pattern(regexp = "^[0-9a-fA-F]{24}$|^$", message = "El identificador del tutor no es válido")
                                String tutorId,
                                @Size(max = 2000, message = "El mensaje admite hasta 2000 caracteres") String contenido) {
}
