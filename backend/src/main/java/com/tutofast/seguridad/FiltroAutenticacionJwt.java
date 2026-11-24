package com.tutofast.seguridad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutofast.dto.ErrorDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNoAutenticado;
import com.tutofast.servicio.ServicioIdentidad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro HTTP liviano y sin estado: lee {@code Authorization: Bearer <token>}, resuelve el usuario
 * (verificando que su cuenta siga activa) y lo deja como atributo de la petición. Las rutas
 * públicas y las que no empiezan por /api pasan sin token.
 */
@Component
public class FiltroAutenticacionJwt extends OncePerRequestFilter {

    public static final String ATRIBUTO_USUARIO = "tutofast.usuarioAutenticado";

    private static final String PREFIJO_BEARER = "Bearer ";
    private static final Set<String> RUTAS_PUBLICAS = Set.of(
            "/api/salud", "/api/auth/registro", "/api/auth/iniciar-sesion");

    private final ServicioIdentidad servicioIdentidad;
    private final ObjectMapper objectMapper;

    public FiltroAutenticacionJwt(ServicioIdentidad servicioIdentidad, ObjectMapper objectMapper) {
        this.servicioIdentidad = servicioIdentidad;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String ruta = rutaNormalizada(request);
        return !ruta.startsWith("/api/") || RUTAS_PUBLICAS.contains(ruta) || esConsultaPublicaDeCatalogo(request, ruta);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain cadena) throws ServletException, IOException {
        String cabecera = request.getHeader("Authorization");
        if (cabecera == null || !cabecera.regionMatches(true, 0, PREFIJO_BEARER, 0, PREFIJO_BEARER.length())) {
            escribirError(response, HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                    "Debe iniciar sesión para acceder a este recurso");
            return;
        }
        try {
            UsuarioAutenticado usuario = servicioIdentidad.autenticarToken(
                    cabecera.substring(PREFIJO_BEARER.length()).trim());
            request.setAttribute(ATRIBUTO_USUARIO, usuario);
        } catch (ExcepcionNoAutenticado e) {
            escribirError(response, HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", e.getMessage());
            return;
        } catch (ExcepcionAutorizacion e) {
            escribirError(response, HttpStatus.FORBIDDEN, "NO_AUTORIZADO", e.getMessage());
            return;
        }
        cadena.doFilter(request, response);
    }

    /** El catálogo académico se consulta sin sesión (lo necesita el formulario de registro); solo lecturas. */
    private static boolean esConsultaPublicaDeCatalogo(HttpServletRequest request, String ruta) {
        return HttpMethod.GET.matches(request.getMethod()) && ruta.startsWith("/api/catalogos/");
    }

    private static String rutaNormalizada(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        String contexto = request.getContextPath();
        if (contexto != null && !contexto.isEmpty() && ruta.startsWith(contexto)) {
            ruta = ruta.substring(contexto.length());
        }
        return ruta.length() > 1 && ruta.endsWith("/") ? ruta.substring(0, ruta.length() - 1) : ruta;
    }

    private void escribirError(HttpServletResponse response, HttpStatus estado, String codigo,
                               String mensaje) throws IOException {
        response.setStatus(estado.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(objectMapper.writeValueAsBytes(ErrorDTO.de(codigo, mensaje)));
    }
}
