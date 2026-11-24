package com.tutofast.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de {@code POST /api/auth/iniciar-sesion}. */
public record InicioSesionDTO(
        @NotBlank(message = "El correo es obligatorio") String correo,
        @NotBlank(message = "La clave es obligatoria") String clave) {
}
