package com.tutofast.controlador;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tutofast.dto.LiquidacionDTO;
import com.tutofast.modelo.Rol;
import com.tutofast.seguridad.RolesPermitidos;
import com.tutofast.seguridad.UsuarioAutenticado;
import com.tutofast.servicio.ServicioPagos;

/** Consulta de pagos (docs/arquitectura/diseno-api.md, sección 10). Sin lógica de negocio. */
@RestController
@RequestMapping("/api/pagos")
public class PagoControlador {

    private final ServicioPagos servicio;

    public PagoControlador(ServicioPagos servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/servicio/{servicioId}")
    public LiquidacionDTO porServicio(UsuarioAutenticado usuario, @PathVariable String servicioId) {
        return servicio.consultarLiquidacion(usuario, servicioId);
    }

    @GetMapping("/mios")
    @RolesPermitidos({Rol.ESTUDIANTE, Rol.TUTOR})
    public List<LiquidacionDTO> mios(UsuarioAutenticado usuario) {
        return servicio.listarMios(usuario.id());
    }
}
