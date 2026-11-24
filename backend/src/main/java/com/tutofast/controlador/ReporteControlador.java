package com.tutofast.controlador;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tutofast.dto.ApelacionDTO;
import com.tutofast.dto.ApelacionEntradaDTO;
import com.tutofast.dto.ReporteDTO;
import com.tutofast.dto.ReporteEntradaDTO;
import com.tutofast.dto.ReportesMiosDTO;
import com.tutofast.dto.SancionDTO;
import com.tutofast.modelo.Rol;
import com.tutofast.seguridad.RolesPermitidos;
import com.tutofast.seguridad.UsuarioAutenticado;
import com.tutofast.servicio.ServicioReportes;

import jakarta.validation.Valid;

/** Reportes, sanciones y apelaciones del usuario (docs/arquitectura/diseno-api.md, sección 14). Sin lógica de negocio. */
@RestController
@RequestMapping("/api")
public class ReporteControlador {

    private final ServicioReportes servicio;

    public ReporteControlador(ServicioReportes servicio) {
        this.servicio = servicio;
    }

    /** Parte {@code datos} (JSON) y hasta 5 partes {@code evidencias}. */
    @PostMapping(path = "/reportes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RolesPermitidos({Rol.ESTUDIANTE, Rol.TUTOR})
    public ReporteDTO radicar(UsuarioAutenticado usuario, @Valid @RequestPart("datos") ReporteEntradaDTO datos,
                              @RequestPart(name = "evidencias", required = false) List<MultipartFile> evidencias) {
        return servicio.radicarReporte(usuario, datos, evidencias);
    }

    @GetMapping("/reportes/mios")
    @RolesPermitidos({Rol.ESTUDIANTE, Rol.TUTOR})
    public ReportesMiosDTO misReportes(UsuarioAutenticado usuario) {
        return servicio.listarMios(usuario.id());
    }

    /** Cualquier usuario autenticado, también con la cuenta suspendida o bloqueada (sesión restringida). */
    @GetMapping("/sanciones/mias")
    public List<SancionDTO> misSanciones(UsuarioAutenticado usuario) {
        return servicio.listarSancionesMias(usuario.id());
    }

    @PostMapping(path = "/sanciones/{id}/apelaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApelacionDTO apelar(UsuarioAutenticado usuario, @PathVariable String id,
                               @Valid @RequestPart("datos") ApelacionEntradaDTO datos,
                               @RequestPart(name = "evidencias", required = false) List<MultipartFile> evidencias) {
        return servicio.apelarSancion(usuario, id, datos, evidencias);
    }
}
