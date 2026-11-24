package com.tutofast.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/servicios/{id}/calificaciones}. El servidor decide el tipo según quién califica: el
 * estudiante usa los seis criterios del tutor y el tutor usa {@code puntualidad}, {@code comunicacion} y
 * {@code cumplimiento}. Los criterios que no corresponden al tipo se ignoran; los que corresponden son obligatorios.
 */
public record CalificacionEntradaDTO(
        @Min(value = 1, message = "La claridad pedagógica debe estar entre 1 y 5") @Max(value = 5, message = "La claridad pedagógica debe estar entre 1 y 5") Integer claridadPedagogica,
        @Min(value = 1, message = "El dominio temático debe estar entre 1 y 5") @Max(value = 5, message = "El dominio temático debe estar entre 1 y 5") Integer dominioTematico,
        @Min(value = 1, message = "La puntualidad debe estar entre 1 y 5") @Max(value = 5, message = "La puntualidad debe estar entre 1 y 5") Integer puntualidad,
        @Min(value = 1, message = "El cumplimiento debe estar entre 1 y 5") @Max(value = 5, message = "El cumplimiento debe estar entre 1 y 5") Integer cumplimiento,
        @Min(value = 1, message = "El trato debe estar entre 1 y 5") @Max(value = 5, message = "El trato debe estar entre 1 y 5") Integer trato,
        @Min(value = 1, message = "La relación calidad-precio debe estar entre 1 y 5") @Max(value = 5, message = "La relación calidad-precio debe estar entre 1 y 5") Integer relacionCalidadPrecio,
        @Min(value = 1, message = "La comunicación debe estar entre 1 y 5") @Max(value = 5, message = "La comunicación debe estar entre 1 y 5") Integer comunicacion,
        @Size(max = 500, message = "El comentario admite hasta 500 caracteres") String comentario) {
}
