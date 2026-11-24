package com.tutofast.dto;

import java.time.Instant;

import com.tutofast.modelo.TipoSancion;

import jakarta.validation.constraints.NotNull;

/**
 * Sanción que acompaña una decisión de moderación. {@code inicio} por omisión es el momento de la decisión;
 * {@code fin} es obligatorio en la suspensión temporal y se ignora en los demás tipos.
 */
public record SancionEntradaDTO(@NotNull(message = "El tipo de sanción es obligatorio") TipoSancion tipo,
                                Instant inicio, Instant fin) {
}
