package com.tutofast.controlador;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tutofast.dto.ContraofertaDTO;
import com.tutofast.dto.PropuestaEntradaDTO;
import com.tutofast.dto.PropuestaVistaDTO;
import com.tutofast.dto.RechazoPropuestaDTO;
import com.tutofast.modelo.Rol;
import com.tutofast.seguridad.RolesPermitidos;
import com.tutofast.seguridad.UsuarioAutenticado;
import com.tutofast.servicio.ServicioPropuestas;

import jakarta.validation.Valid;

/** Propuestas y negociación (docs/arquitectura/diseno-api.md, sección 9). Sin lógica de negocio. */
@RestController
@RequestMapping("/api")
public class PropuestaControlador {

    private final ServicioPropuestas servicio;

    public PropuestaControlador(ServicioPropuestas servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/solicitudes/{solicitudId}/propuestas")
    @ResponseStatus(HttpStatus.CREATED)
    @RolesPermitidos(Rol.TUTOR)
    public PropuestaVistaDTO enviar(UsuarioAutenticado usuario, @PathVariable String solicitudId,
                                    @Valid @RequestBody PropuestaEntradaDTO dto) {
        return servicio.enviarPropuesta(usuario.id(), solicitudId, dto);
    }

    @GetMapping("/solicitudes/{solicitudId}/propuestas")
    public List<PropuestaVistaDTO> listar(UsuarioAutenticado usuario, @PathVariable String solicitudId) {
        return servicio.listarPropuestas(usuario, solicitudId);
    }

    @GetMapping("/propuestas/mias")
    @RolesPermitidos(Rol.TUTOR)
    public List<PropuestaVistaDTO> mias(UsuarioAutenticado usuario) {
        return servicio.listarMisPropuestas(usuario.id());
    }

    @PutMapping("/propuestas/{id}")
    @RolesPermitidos(Rol.TUTOR)
    public PropuestaVistaDTO modificar(UsuarioAutenticado usuario, @PathVariable String id,
                                       @Valid @RequestBody PropuestaEntradaDTO dto) {
        return servicio.modificarPropuesta(usuario.id(), id, dto);
    }

    @PostMapping("/propuestas/{id}/retirar")
    @RolesPermitidos(Rol.TUTOR)
    public PropuestaVistaDTO retirar(UsuarioAutenticado usuario, @PathVariable String id) {
        return servicio.retirarPropuesta(usuario.id(), id);
    }

    @PostMapping("/propuestas/{id}/contraofertar")
    @RolesPermitidos(Rol.ESTUDIANTE)
    public PropuestaVistaDTO contraofertar(UsuarioAutenticado usuario, @PathVariable String id,
                                           @Valid @RequestBody ContraofertaDTO dto) {
        return servicio.contraofertar(usuario.id(), id, dto.precioCOP(), dto.nota());
    }

    @PostMapping("/propuestas/{id}/seleccionar")
    @RolesPermitidos(Rol.ESTUDIANTE)
    public PropuestaVistaDTO seleccionar(UsuarioAutenticado usuario, @PathVariable String id) {
        return servicio.aceptarPropuesta(usuario.id(), id);
    }

    @PostMapping("/propuestas/{id}/rechazar")
    @RolesPermitidos(Rol.ESTUDIANTE)
    public PropuestaVistaDTO rechazar(UsuarioAutenticado usuario, @PathVariable String id,
                                      @Valid @RequestBody(required = false) RechazoPropuestaDTO dto) {
        return servicio.rechazarPropuesta(usuario.id(), id, dto == null ? null : dto.motivo());
    }
}
