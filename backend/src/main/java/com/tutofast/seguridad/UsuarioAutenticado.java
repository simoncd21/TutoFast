package com.tutofast.seguridad;

import java.util.Set;

import com.tutofast.modelo.Rol;

/** Usuario resuelto por el filtro JWT para la petición en curso. */
public record UsuarioAutenticado(String id, String correo, String nombre, Set<Rol> roles) {

    public boolean tieneAlgunRol(Rol... requeridos) {
        for (Rol r : requeridos) {
            if (roles.contains(r)) {
                return true;
            }
        }
        return false;
    }
}
