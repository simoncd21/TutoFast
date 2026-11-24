package com.tutofast.seguridad;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNoAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Valida {@link RolesPermitidos} usando el usuario que dejó el filtro JWT. */
public class InterceptorRoles implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod metodo)) {
            return true;
        }
        RolesPermitidos anotacion = metodo.getMethodAnnotation(RolesPermitidos.class);
        if (anotacion == null) {
            anotacion = metodo.getBeanType().getAnnotation(RolesPermitidos.class);
        }
        if (anotacion == null) {
            return true;
        }
        Object atributo = request.getAttribute(FiltroAutenticacionJwt.ATRIBUTO_USUARIO);
        if (!(atributo instanceof UsuarioAutenticado usuario)) {
            throw new ExcepcionNoAutenticado("Debe iniciar sesión para acceder a este recurso");
        }
        if (!usuario.tieneAlgunRol(anotacion.value())) {
            throw new ExcepcionAutorizacion("No tiene permisos para realizar esta acción");
        }
        return true;
    }
}
