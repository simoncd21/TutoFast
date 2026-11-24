package com.tutofast.seguridad;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.tutofast.excepcion.ExcepcionNoAutenticado;

/** Permite recibir {@link UsuarioAutenticado} como parámetro de un método de controlador. */
public class ResolvedorUsuarioAutenticado implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parametro) {
        return UsuarioAutenticado.class.equals(parametro.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer contenedor,
                                  NativeWebRequest peticion, WebDataBinderFactory fabrica) {
        Object usuario = peticion.getAttribute(FiltroAutenticacionJwt.ATRIBUTO_USUARIO,
                RequestAttributes.SCOPE_REQUEST);
        if (usuario instanceof UsuarioAutenticado autenticado) {
            return autenticado;
        }
        throw new ExcepcionNoAutenticado("Debe iniciar sesión para acceder a este recurso");
    }
}
