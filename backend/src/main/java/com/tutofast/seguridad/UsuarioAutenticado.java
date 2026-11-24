package com.tutofast.seguridad;

import java.util.Set;

import com.tutofast.modelo.Rol;

/**
 * Usuario resuelto por el filtro JWT para la petición en curso. {@code restringida} es {@code true} cuando la cuenta
 * está SUSPENDIDA o BLOQUEADA: puede iniciar sesión solo para consultar y apelar sus sanciones (ver
 * {@link FiltroAutenticacionJwt}).
 */
public record UsuarioAutenticado(String id, String correo, String nombre, Set<Rol> roles, boolean restringida) {

    /** Cuenta activa (sin restricciones). */
    public UsuarioAutenticado(String id, String correo, String nombre, Set<Rol> roles) {
        this(id, correo, nombre, roles, false);
    }

    public boolean tieneAlgunRol(Rol... requeridos) {
        for (Rol r : requeridos) {
            if (roles.contains(r)) {
                return true;
            }
        }
        return false;
    }
}
