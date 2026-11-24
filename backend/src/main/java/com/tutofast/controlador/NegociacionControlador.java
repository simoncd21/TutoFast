package com.tutofast.controlador;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tutofast.dto.ConfirmacionServicioDTO;
import com.tutofast.dto.EnlaceServicioDTO;
import com.tutofast.dto.ServicioDTO;
import com.tutofast.modelo.EstadoServicio;
import com.tutofast.modelo.Rol;
import com.tutofast.seguridad.RolesPermitidos;
import com.tutofast.seguridad.UsuarioAutenticado;
import com.tutofast.servicio.ServicioNegociacion;

import jakarta.validation.Valid;

/** Confirmación y ciclo del servicio (docs/arquitectura/diseno-api.md, sección 9). Sin lógica de negocio. */
@RestController
@RequestMapping("/api")
public class NegociacionControlador {

    private final ServicioNegociacion servicio;

    public NegociacionControlador(ServicioNegociacion servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/solicitudes/{solicitudId}/confirmar")
    @RolesPermitidos(Rol.ESTUDIANTE)
    public ServicioDTO confirmar(UsuarioAutenticado usuario, @PathVariable String solicitudId,
                                 @Valid @RequestBody ConfirmacionServicioDTO dto) {
        return servicio.confirmarServicio(usuario.id(), solicitudId, dto.tokenPagoSimulado());
    }

    @GetMapping("/servicios/mios")
    @RolesPermitidos({Rol.ESTUDIANTE, Rol.TUTOR})
    public List<ServicioDTO> mios(UsuarioAutenticado usuario, @RequestParam(required = false) EstadoServicio estado) {
        return servicio.listarMios(usuario.id(), estado);
    }

    @GetMapping("/servicios/{id}")
    public ServicioDTO obtener(UsuarioAutenticado usuario, @PathVariable String id) {
        return servicio.obtener(usuario, id);
    }

    @PatchMapping("/servicios/{id}/enlace")
    @RolesPermitidos(Rol.TUTOR)
    public ServicioDTO enlace(UsuarioAutenticado usuario, @PathVariable String id,
                              @Valid @RequestBody EnlaceServicioDTO dto) {
        return servicio.actualizarEnlace(usuario.id(), id, dto);
    }

    @PostMapping("/servicios/{id}/iniciar")
    @RolesPermitidos({Rol.ESTUDIANTE, Rol.TUTOR})
    public ServicioDTO iniciar(UsuarioAutenticado usuario, @PathVariable String id) {
        return servicio.iniciarSesion(usuario.id(), id);
    }
}
