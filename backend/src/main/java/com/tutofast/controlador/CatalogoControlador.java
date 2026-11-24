package com.tutofast.controlador;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tutofast.dto.AsignaturaDetalleDTO;
import com.tutofast.dto.CatalogoActivaDTO;
import com.tutofast.dto.CatalogoEntradaDTO;
import com.tutofast.dto.ElementoCatalogoDTO;
import com.tutofast.dto.ProgramaDetalleDTO;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.TipoCatalogo;
import com.tutofast.seguridad.RolesPermitidos;
import com.tutofast.seguridad.UsuarioAutenticado;
import com.tutofast.servicio.ServicioCatalogo;

import jakarta.validation.Valid;

/** Catálogo académico: las consultas son públicas (GET); la gestión es solo del administrador. */
@RestController
@RequestMapping("/api/catalogos")
public class CatalogoControlador {

    private final ServicioCatalogo servicio;

    public CatalogoControlador(ServicioCatalogo servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/universidades")
    public List<? extends ElementoCatalogoDTO> universidades(@RequestParam(defaultValue = "true") boolean soloActivas) {
        return servicio.consultarCatalogo(TipoCatalogo.UNIVERSIDADES, null, soloActivas);
    }

    @GetMapping("/universidades/{id}/facultades")
    public List<? extends ElementoCatalogoDTO> facultades(@PathVariable String id,
                                                          @RequestParam(defaultValue = "true") boolean soloActivas) {
        return servicio.consultarCatalogo(TipoCatalogo.FACULTADES, id, soloActivas);
    }

    @GetMapping("/facultades/{id}/programas")
    public List<? extends ElementoCatalogoDTO> programas(@PathVariable String id,
                                                         @RequestParam(defaultValue = "true") boolean soloActivas) {
        return servicio.consultarCatalogo(TipoCatalogo.PROGRAMAS, id, soloActivas);
    }

    @GetMapping("/programas/{id}")
    public ProgramaDetalleDTO programa(@PathVariable String id) {
        return servicio.consultarPrograma(id);
    }

    @GetMapping("/programas/{id}/asignaturas")
    public List<? extends ElementoCatalogoDTO> asignaturas(@PathVariable String id,
                                                           @RequestParam(defaultValue = "true") boolean soloActivas) {
        return servicio.consultarCatalogo(TipoCatalogo.ASIGNATURAS, id, soloActivas);
    }

    @GetMapping("/asignaturas/{id}")
    public AsignaturaDetalleDTO asignatura(@PathVariable String id) {
        return servicio.consultarAsignatura(id);
    }

    @PostMapping("/{tipo}")
    @ResponseStatus(HttpStatus.CREATED)
    @RolesPermitidos(Rol.ADMINISTRADOR)
    public ElementoCatalogoDTO crear(UsuarioAutenticado usuario, @PathVariable String tipo,
                                     @Valid @RequestBody CatalogoEntradaDTO dto) {
        return servicio.gestionarCatalogo(usuario.id(), TipoCatalogo.deSegmento(tipo), null, dto);
    }

    @PutMapping("/{tipo}/{id}")
    @RolesPermitidos(Rol.ADMINISTRADOR)
    public ElementoCatalogoDTO editar(UsuarioAutenticado usuario, @PathVariable String tipo, @PathVariable String id,
                                      @Valid @RequestBody CatalogoEntradaDTO dto) {
        return servicio.gestionarCatalogo(usuario.id(), TipoCatalogo.deSegmento(tipo), id, dto);
    }

    @PatchMapping("/{tipo}/{id}/activa")
    @RolesPermitidos(Rol.ADMINISTRADOR)
    public ElementoCatalogoDTO cambiarActiva(UsuarioAutenticado usuario, @PathVariable String tipo,
                                             @PathVariable String id, @Valid @RequestBody CatalogoActivaDTO dto) {
        return servicio.cambiarEstadoCatalogo(usuario.id(), TipoCatalogo.deSegmento(tipo), id, dto.activa());
    }
}
