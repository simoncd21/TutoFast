package com.tutofast.seguridad;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.tutofast.modelo.Rol;

/** Restringe un método o controlador a los roles indicados (basta con tener uno). */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RolesPermitidos {
    Rol[] value();
}
