package com.tutofast.controlador;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tutofast.dto.CambioRolDTO;
import com.tutofast.dto.PerfilCompletoDTO;
import com.tutofast.dto.PerfilEstudianteDTO;
import com.tutofast.dto.PerfilTutorDTO;
import com.tutofast.dto.SoporteDTO;
import com.tutofast.dto.SoporteSolicitudDTO;
import com.tutofast.dto.UsuarioDTO;
import com.tutofast.modelo.Rol;
import com.tutofast.seguridad.RolesPermitidos;
import com.tutofast.seguridad.UsuarioAutenticado;
import com.tutofast.servicio.ServicioIdentidad;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/usuarios/yo")
public class UsuarioControlador {

    private final ServicioIdentidad servicio;

    public UsuarioControlador(ServicioIdentidad servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public PerfilCompletoDTO perfil(UsuarioAutenticado usuario) {
        return servicio.obtenerPerfil(usuario.id());
    }

    @PutMapping("/perfil-estudiante")
    @RolesPermitidos(Rol.ESTUDIANTE)
    public PerfilCompletoDTO actualizarPerfilEstudiante(UsuarioAutenticado usuario,
                                                        @Valid @RequestBody PerfilEstudianteDTO dto) {
        return servicio.actualizarPerfilEstudiante(usuario.id(), dto);
    }

    @PutMapping("/perfil-tutor")
    @RolesPermitidos(Rol.TUTOR)
    public PerfilCompletoDTO actualizarPerfilTutor(UsuarioAutenticado usuario,
                                                   @Valid @RequestBody PerfilTutorDTO dto) {
        return servicio.actualizarPerfilTutor(usuario.id(), dto);
    }

    @PostMapping("/roles")
    public UsuarioDTO activarRol(UsuarioAutenticado usuario, @Valid @RequestBody CambioRolDTO dto) {
        return servicio.activarRol(usuario.id(), dto.rol());
    }

    @PostMapping(path = "/soportes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RolesPermitidos(Rol.TUTOR)
    public SoporteDTO subirSoporte(UsuarioAutenticado usuario,
                                   @Valid @RequestPart("datos") SoporteSolicitudDTO datos,
                                   @RequestPart("archivo") MultipartFile archivo) {
        return servicio.subirSoporte(usuario.id(), datos.tipo(), archivo);
    }

    @GetMapping("/soportes")
    @RolesPermitidos(Rol.TUTOR)
    public List<SoporteDTO> listarSoportes(UsuarioAutenticado usuario) {
        return servicio.listarSoportes(usuario.id());
    }
}
