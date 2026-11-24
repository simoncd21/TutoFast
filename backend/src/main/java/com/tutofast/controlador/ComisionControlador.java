package com.tutofast.controlador;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tutofast.dto.ComisionDTO;
import com.tutofast.dto.ComisionHistorialDTO;
import com.tutofast.dto.ConfigurarComisionDTO;
import com.tutofast.modelo.Rol;
import com.tutofast.seguridad.RolesPermitidos;
import com.tutofast.seguridad.UsuarioAutenticado;
import com.tutofast.servicio.ServicioCatalogo;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/comision")
public class ComisionControlador {

    private final ServicioCatalogo servicio;

    public ComisionControlador(ServicioCatalogo servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/vigente")
    public ComisionDTO vigente() {
        return servicio.consultarComision();
    }

    @PutMapping
    @RolesPermitidos(Rol.ADMINISTRADOR)
    public ComisionDTO configurar(UsuarioAutenticado usuario, @Valid @RequestBody ConfigurarComisionDTO dto) {
        return servicio.configurarComision(usuario.id(), dto.porcentaje());
    }

    @GetMapping("/historial")
    @RolesPermitidos(Rol.ADMINISTRADOR)
    public List<ComisionHistorialDTO> historial() {
        return servicio.consultarHistorialComision();
    }
}
