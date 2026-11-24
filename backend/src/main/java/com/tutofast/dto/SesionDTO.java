package com.tutofast.dto;

import java.time.Instant;

/** Respuesta de inicio de sesión: token JWT y usuario. */
public record SesionDTO(String token, Instant expiraEn, UsuarioDTO usuario) {
}
