package com.tutofast.dto;

import java.time.Instant;
import java.util.List;

import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.NivelVerificacion;
import com.tutofast.modelo.Rol;

/** Vista de un usuario para el panel de administración (sin clave ni documento). */
public record UsuarioAdminDTO(String id, String nombre, String correo, List<Rol> roles, EstadoCuenta estadoCuenta,
                              Instant fechaRegistro, NivelVerificacion nivelVerificacion) {
}
