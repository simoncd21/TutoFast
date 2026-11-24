package com.tutofast.controlador;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tutofast.dto.CalificacionDTO;
import com.tutofast.dto.CalificacionEntradaDTO;
import com.tutofast.dto.CalificacionesServicioDTO;
import com.tutofast.dto.ReporteResenaEntradaDTO;
import com.tutofast.modelo.Rol;
import com.tutofast.seguridad.RolesPermitidos;
import com.tutofast.seguridad.UsuarioAutenticado;
import com.tutofast.servicio.ServicioReputacion;

import jakarta.validation.Valid;

/** Calificaciones bilaterales y reporte de reseñas (docs/arquitectura/diseno-api.md, sección 13). Sin lógica de negocio. */
@RestController
@RequestMapping("/api")
public class CalificacionControlador {

    private final ServicioReputacion servicio;

    public CalificacionControlador(ServicioReputacion servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/servicios/{servicioId}/calificaciones")
    @ResponseStatus(HttpStatus.CREATED)
    @RolesPermitidos({Rol.ESTUDIANTE, Rol.TUTOR})
    public CalificacionDTO calificar(UsuarioAutenticado usuario, @PathVariable String servicioId,
                                     @Valid @RequestBody CalificacionEntradaDTO dto) {
        return servicio.calificar(usuario, servicioId, dto);
    }

    @GetMapping("/servicios/{servicioId}/calificaciones")
    @RolesPermitidos({Rol.ESTUDIANTE, Rol.TUTOR})
    public CalificacionesServicioDTO consultar(UsuarioAutenticado usuario, @PathVariable String servicioId) {
        return servicio.consultar(usuario, servicioId);
    }

    @PostMapping("/calificaciones/{id}/reportar")
    @RolesPermitidos({Rol.ESTUDIANTE, Rol.TUTOR})
    public CalificacionDTO reportar(UsuarioAutenticado usuario, @PathVariable String id,
                                    @Valid @RequestBody ReporteResenaEntradaDTO dto) {
        return servicio.reportarResena(usuario, id, dto);
    }
}
