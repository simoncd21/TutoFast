package com.tutofast.dto;

import java.time.Instant;
import java.util.List;

import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.Rol;

/** Respuesta de {@code GET /api/usuarios/yo} y de las actualizaciones de perfil. */
public record PerfilCompletoDTO(
        String id,
        String nombre,
        String correo,
        String documento,
        List<Rol> roles,
        EstadoCuenta estadoCuenta,
        Instant fechaRegistro,
        PerfilEstudianteDTO perfilEstudiante,
        PerfilTutorVistaDTO perfilTutor) {
}
