package com.tutofast.controlador;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tutofast.dto.InicioSesionDTO;
import com.tutofast.dto.RegistroDTO;
import com.tutofast.dto.RegistroRespuestaDTO;
import com.tutofast.dto.SesionDTO;
import com.tutofast.dto.UsuarioDTO;
import com.tutofast.seguridad.UsuarioAutenticado;
import com.tutofast.servicio.ServicioIdentidad;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AutenticacionControlador {

    private final ServicioIdentidad servicio;

    public AutenticacionControlador(ServicioIdentidad servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistroRespuestaDTO registro(@Valid @RequestBody RegistroDTO dto) {
        return servicio.registrarUsuario(dto);
    }

    @PostMapping("/iniciar-sesion")
    public SesionDTO iniciarSesion(@Valid @RequestBody InicioSesionDTO dto) {
        return servicio.iniciarSesion(dto);
    }

    @GetMapping("/yo")
    public UsuarioDTO yo(UsuarioAutenticado usuario) {
        return servicio.obtenerUsuario(usuario.id());
    }
}
