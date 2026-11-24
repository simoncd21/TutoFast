package com.tutofast.controlador;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tutofast.dto.CambioEstadoCuentaDTO;
import com.tutofast.dto.MensajeReportadoDTO;
import com.tutofast.dto.ModeracionEntradaDTO;
import com.tutofast.dto.Pagina;
import com.tutofast.dto.RechazoSoporteDTO;
import com.tutofast.dto.RegistroAuditoriaDTO;
import com.tutofast.dto.ResenaReportadaDTO;
import com.tutofast.dto.RevisionSoporteDTO;
import com.tutofast.dto.SoporteColaDTO;
import com.tutofast.dto.SoporteDTO;
import com.tutofast.dto.UsuarioAdminDTO;
import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.EstadoSoporte;
import com.tutofast.modelo.Rol;
import com.tutofast.seguridad.RolesPermitidos;
import com.tutofast.seguridad.UsuarioAutenticado;
import com.tutofast.servicio.ServicioAuditoria;
import com.tutofast.servicio.ServicioIdentidad;
import com.tutofast.servicio.ServicioMensajeria;
import com.tutofast.servicio.ServicioReputacion;

import jakarta.validation.Valid;

/** Panel de administración: verificación de tutores, usuarios y auditoría (todo ADMINISTRADOR). */
@RestController
@RequestMapping("/api/admin")
@RolesPermitidos(Rol.ADMINISTRADOR)
public class AdministracionControlador {

    private final ServicioIdentidad identidad;
    private final ServicioAuditoria auditoria;
    private final ServicioMensajeria mensajeria;
    private final ServicioReputacion reputacion;

    public AdministracionControlador(ServicioIdentidad identidad, ServicioAuditoria auditoria,
                                     ServicioMensajeria mensajeria, ServicioReputacion reputacion) {
        this.identidad = identidad;
        this.auditoria = auditoria;
        this.mensajeria = mensajeria;
        this.reputacion = reputacion;
    }

    // ---- Moderación de mensajes y reseñas ----

    @GetMapping("/mensajes-reportados")
    public List<MensajeReportadoDTO> mensajesReportados(@RequestParam(defaultValue = "false") boolean incluirModerados) {
        return mensajeria.listarReportados(incluirModerados);
    }

    @PostMapping("/mensajes-reportados/{mensajeId}/moderar")
    public MensajeReportadoDTO moderarMensaje(UsuarioAutenticado admin, @PathVariable String mensajeId,
                                              @Valid @RequestBody ModeracionEntradaDTO dto) {
        return mensajeria.moderarMensaje(admin.id(), mensajeId, dto);
    }

    @GetMapping("/resenas-reportadas")
    public List<ResenaReportadaDTO> resenasReportadas() {
        return reputacion.listarResenasReportadas();
    }

    @PostMapping("/resenas-reportadas/{calificacionId}/moderar")
    public ResenaReportadaDTO moderarResena(UsuarioAutenticado admin, @PathVariable String calificacionId,
                                            @Valid @RequestBody ModeracionEntradaDTO dto) {
        return reputacion.moderarResena(admin.id(), calificacionId, dto);
    }

    @GetMapping("/soportes")
    public List<SoporteColaDTO> colaVerificacion(@RequestParam(defaultValue = "EN_REVISION") EstadoSoporte estado) {
        return identidad.consultarColaVerificacion(estado);
    }

    @PostMapping("/usuarios/{usuarioId}/soportes/{soporteId}/aprobar")
    public SoporteDTO aprobar(UsuarioAutenticado admin, @PathVariable String usuarioId,
                              @PathVariable String soporteId,
                              @Valid @RequestBody(required = false) RevisionSoporteDTO dto) {
        return identidad.verificarTutor(admin.id(), usuarioId, soporteId, true,
                dto == null ? null : dto.observacion());
    }

    @PostMapping("/usuarios/{usuarioId}/soportes/{soporteId}/rechazar")
    public SoporteDTO rechazar(UsuarioAutenticado admin, @PathVariable String usuarioId,
                               @PathVariable String soporteId, @Valid @RequestBody RechazoSoporteDTO dto) {
        return identidad.verificarTutor(admin.id(), usuarioId, soporteId, false, dto.observacion());
    }

    @GetMapping("/usuarios")
    public Pagina<UsuarioAdminDTO> usuarios(@RequestParam(required = false) Rol rol,
                                            @RequestParam(required = false) EstadoCuenta estado,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(defaultValue = "0") int pagina,
                                            @RequestParam(defaultValue = "20") int tamano) {
        return identidad.listarUsuarios(rol, estado, q, pagina, tamano);
    }

    @PatchMapping("/usuarios/{id}/estado")
    public UsuarioAdminDTO cambiarEstado(UsuarioAutenticado admin, @PathVariable String id,
                                         @Valid @RequestBody CambioEstadoCuentaDTO dto) {
        return identidad.cambiarEstadoCuenta(admin.id(), id, dto.estadoCuenta(), dto.motivo());
    }

    @GetMapping("/auditoria")
    public Pagina<RegistroAuditoriaDTO> auditoria(
            @RequestParam(required = false) String usuarioId,
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return auditoria.consultarPagina(usuarioId, entidad, desde, hasta, pagina, tamano);
    }
}
