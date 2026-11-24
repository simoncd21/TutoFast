package com.tutofast.controlador;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tutofast.dto.HiloMensajesDTO;
import com.tutofast.dto.HiloResumenDTO;
import com.tutofast.dto.MensajeDTO;
import com.tutofast.dto.MensajeEntradaDTO;
import com.tutofast.dto.ReporteMensajeEntradaDTO;
import com.tutofast.modelo.Rol;
import com.tutofast.seguridad.RolesPermitidos;
import com.tutofast.seguridad.UsuarioAutenticado;
import com.tutofast.servicio.ServicioMensajeria;

import jakarta.validation.Valid;

/** Chat entre estudiante y tutor (docs/arquitectura/diseno-api.md, sección 11). Sin lógica de negocio. */
@RestController
@RequestMapping("/api")
public class MensajeControlador {

    private final ServicioMensajeria servicio;

    public MensajeControlador(ServicioMensajeria servicio) {
        this.servicio = servicio;
    }

    /** Polling: el cliente repite la llamada con {@code desde} = marca temporal del último mensaje recibido. */
    @GetMapping("/solicitudes/{solicitudId}/mensajes")
    @RolesPermitidos({Rol.ESTUDIANTE, Rol.TUTOR, Rol.ADMINISTRADOR})
    public HiloMensajesDTO historial(UsuarioAutenticado usuario, @PathVariable String solicitudId,
                                     @RequestParam(required = false) String tutorId,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde) {
        return servicio.consultarHistorial(usuario, solicitudId, tutorId, desde);
    }

    @GetMapping("/solicitudes/{solicitudId}/hilos")
    @RolesPermitidos({Rol.ESTUDIANTE, Rol.ADMINISTRADOR})
    public List<HiloResumenDTO> hilos(UsuarioAutenticado usuario, @PathVariable String solicitudId) {
        return servicio.listarHilos(usuario, solicitudId);
    }

    /** Mensaje con adjunto opcional: parte {@code datos} (JSON) y parte {@code archivo}. */
    @PostMapping(path = "/solicitudes/{solicitudId}/mensajes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RolesPermitidos({Rol.ESTUDIANTE, Rol.TUTOR, Rol.ADMINISTRADOR})
    public MensajeDTO enviarConArchivo(UsuarioAutenticado usuario, @PathVariable String solicitudId,
                                       @Valid @RequestPart("datos") MensajeEntradaDTO datos,
                                       @RequestPart(name = "archivo", required = false) MultipartFile archivo) {
        return servicio.enviarMensaje(usuario, solicitudId, datos, archivo);
    }

    /** Mensaje de solo texto (JSON). */
    @PostMapping(path = "/solicitudes/{solicitudId}/mensajes", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RolesPermitidos({Rol.ESTUDIANTE, Rol.TUTOR, Rol.ADMINISTRADOR})
    public MensajeDTO enviar(UsuarioAutenticado usuario, @PathVariable String solicitudId,
                             @Valid @RequestBody MensajeEntradaDTO datos) {
        return servicio.enviarMensaje(usuario, solicitudId, datos, null);
    }

    @PostMapping("/mensajes/{id}/reportar")
    @RolesPermitidos({Rol.ESTUDIANTE, Rol.TUTOR})
    public MensajeDTO reportar(UsuarioAutenticado usuario, @PathVariable String id,
                               @Valid @RequestBody ReporteMensajeEntradaDTO datos) {
        return servicio.reportarMensaje(usuario, id, datos);
    }
}
