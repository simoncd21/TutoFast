package com.tutofast.controlador;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tutofast.dto.ArchivoDTO;
import com.tutofast.dto.Pagina;
import com.tutofast.dto.SolicitudDetalleDTO;
import com.tutofast.dto.SolicitudEntradaDTO;
import com.tutofast.dto.SolicitudResumenDTO;
import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.Rol;
import com.tutofast.repositorio.FiltroSolicitudes;
import com.tutofast.seguridad.RolesPermitidos;
import com.tutofast.seguridad.UsuarioAutenticado;
import com.tutofast.servicio.ServicioSolicitudes;

import jakarta.validation.Valid;

/** Solicitudes de tutoría (docs/arquitectura/diseno-api.md, sección 8). Sin lógica de negocio. */
@RestController
@RequestMapping("/api/solicitudes")
public class SolicitudControlador {

    private final ServicioSolicitudes servicio;

    public SolicitudControlador(ServicioSolicitudes servicio) {
        this.servicio = servicio;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RolesPermitidos(Rol.ESTUDIANTE)
    public SolicitudDetalleDTO publicar(UsuarioAutenticado usuario,
                                        @Valid @RequestPart("datos") SolicitudEntradaDTO datos,
                                        @RequestPart(name = "archivos", required = false) List<MultipartFile> archivos) {
        return servicio.publicarSolicitud(usuario.id(), datos, archivos);
    }

    @GetMapping
    public Pagina<SolicitudResumenDTO> buscar(
            @RequestParam(required = false) String universidadId,
            @RequestParam(required = false) String facultadId,
            @RequestParam(required = false) String programaId,
            @RequestParam(required = false) String asignaturaId,
            @RequestParam(required = false) String tema,
            @RequestParam(required = false) Modalidad modalidad,
            @RequestParam(required = false) Instant fechaDesde,
            @RequestParam(required = false) Instant fechaHasta,
            @RequestParam(required = false) BigDecimal precioMin,
            @RequestParam(required = false) BigDecimal precioMax,
            @RequestParam(required = false) String orden,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "12") int tamano) {
        FiltroSolicitudes filtro = new FiltroSolicitudes(universidadId, facultadId, programaId, asignaturaId, tema,
                modalidad, fechaDesde, fechaHasta, precioMin, precioMax, null, FiltroSolicitudes.Orden.deTexto(orden));
        return servicio.buscarSolicitudes(filtro, pagina, tamano);
    }

    @GetMapping("/mias")
    @RolesPermitidos(Rol.ESTUDIANTE)
    public List<SolicitudResumenDTO> mias(UsuarioAutenticado usuario) {
        return servicio.misSolicitudes(usuario.id());
    }

    @GetMapping("/{id}")
    public SolicitudDetalleDTO detalle(UsuarioAutenticado usuario, @PathVariable String id) {
        return servicio.obtenerSolicitud(usuario, id);
    }

    @PutMapping("/{id}")
    @RolesPermitidos(Rol.ESTUDIANTE)
    public SolicitudDetalleDTO editar(UsuarioAutenticado usuario, @PathVariable String id,
                                      @Valid @RequestBody SolicitudEntradaDTO dto) {
        return servicio.editarSolicitud(usuario.id(), id, dto);
    }

    @PostMapping("/{id}/cancelar")
    @RolesPermitidos(Rol.ESTUDIANTE)
    public SolicitudDetalleDTO cancelar(UsuarioAutenticado usuario, @PathVariable String id) {
        return servicio.cancelarSolicitud(usuario.id(), id);
    }

    @PostMapping(path = "/{id}/archivos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RolesPermitidos(Rol.ESTUDIANTE)
    public List<ArchivoDTO> adjuntar(UsuarioAutenticado usuario, @PathVariable String id,
                                     @RequestPart("archivos") List<MultipartFile> archivos) {
        return servicio.agregarArchivos(usuario.id(), id, archivos);
    }
}
