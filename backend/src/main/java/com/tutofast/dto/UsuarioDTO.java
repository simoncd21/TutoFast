package com.tutofast.dto;

import java.util.List;

import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.Rol;

/** Vista pública mínima del usuario autenticado (nunca incluye la clave ni el documento). */
public record UsuarioDTO(String id, String nombre, String correo, List<Rol> roles, EstadoCuenta estadoCuenta) {
}
