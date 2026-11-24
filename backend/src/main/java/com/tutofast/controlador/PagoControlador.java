package com.tutofast.controlador;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tutofast.dto.CierreAutomaticoDTO;
import com.tutofast.dto.LiquidacionDTO;
import com.tutofast.modelo.Rol;
import com.tutofast.seguridad.RolesPermitidos;
import com.tutofast.seguridad.UsuarioAutenticado;
import com.tutofast.servicio.ServicioNegociacion;
import com.tutofast.servicio.ServicioPagos;

/** Consulta de pagos (docs/arquitectura/diseno-api.md, sección 10). Sin lógica de negocio. */
@RestController
@RequestMapping("/api/pagos")
public class PagoControlador {

    private final ServicioPagos servicio;
    private final ServicioNegociacion negociacion;

    public PagoControlador(ServicioPagos servicio, ServicioNegociacion negociacion) {
        this.servicio = servicio;
        this.negociacion = negociacion;
    }

    /** Demostración: ejecuta ahora el cierre automático de servicios vencidos (solo ADMINISTRADOR). */
    @PostMapping("/simular-cierre-automatico")
    @RolesPermitidos(Rol.ADMINISTRADOR)
    public CierreAutomaticoDTO simularCierreAutomatico(UsuarioAutenticado usuario,
                                                       @RequestParam(defaultValue = "false") boolean ignorarPlazo) {
        return negociacion.simularCierreAutomatico(usuario.id(), ignorarPlazo);
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
