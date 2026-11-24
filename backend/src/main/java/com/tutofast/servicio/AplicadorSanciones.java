package com.tutofast.servicio;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.tutofast.dto.SancionEntradaDTO;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.modelo.AccionPago;
import com.tutofast.modelo.DecisionReporte;
import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.EstadoSancion;
import com.tutofast.modelo.Periodo;
import com.tutofast.modelo.Reporte;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.Sancion;
import com.tutofast.modelo.TipoReporte;
import com.tutofast.modelo.TipoSancion;
import com.tutofast.modelo.Usuario;
import com.tutofast.repositorio.RepositorioReporte;
import com.tutofast.repositorio.RepositorioSancion;
import com.tutofast.repositorio.RepositorioUsuario;

/**
 * Aplica y levanta sanciones. Lo usan la moderación de mensajes y de reseñas; el módulo de reportes lo reutilizará.
 *
 * <p>Una sanción siempre cuelga de un {@link Reporte}: cuando nace de la moderación se radica y resuelve un reporte
 * (a favor del reportante) para que la sanción tenga su expediente y pueda apelarse. Una suspensión temporal pone la
 * cuenta en SUSPENDIDA y {@link #levantarVencidas} la reactiva al vencer la vigencia; un bloqueo permanente la pone
 * en BLOQUEADA; una advertencia solo deja constancia y avisa al usuario.
 */
@Component
public class AplicadorSanciones {

    private static final Logger log = LoggerFactory.getLogger(AplicadorSanciones.class);

    private final RepositorioSancion sanciones;
    private final RepositorioReporte reportes;
    private final RepositorioUsuario usuarios;
    private final ServicioAuditoria auditoria;
    private final ServicioNotificaciones notificaciones;

    public AplicadorSanciones(RepositorioSancion sanciones, RepositorioReporte reportes, RepositorioUsuario usuarios,
                              ServicioAuditoria auditoria, ServicioNotificaciones notificaciones) {
        this.sanciones = sanciones;
        this.reportes = reportes;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
    }

    /**
     * Comprueba la sanción solicitada antes de tocar nada: suspensión temporal con fin futuro, inicio no posterior al
     * momento actual y fin posterior al inicio.
     */
    public void validar(SancionEntradaDTO dto, Instant ahora) {
        if (dto == null || dto.tipo() == null) {
            throw new ExcepcionNegocio("VALIDACION", "El tipo de sanción es obligatorio", HttpStatus.BAD_REQUEST);
        }
        if (dto.inicio() != null && dto.inicio().isAfter(ahora)) {
            throw new ExcepcionNegocio("El inicio de la sanción no puede ser posterior al momento de la decisión");
        }
        if (dto.tipo() == TipoSancion.SUSPENSION_TEMPORAL) {
            if (dto.fin() == null) {
                throw new ExcepcionNegocio("La suspensión temporal requiere una fecha de fin");
            }
            Instant inicio = dto.inicio() == null ? ahora : dto.inicio();
            if (!dto.fin().isAfter(ahora) || !dto.fin().isAfter(inicio)) {
                throw new ExcepcionNegocio("La fecha de fin de la suspensión debe ser futura");
            }
        }
    }

    /**
     * Radica y resuelve un reporte a favor del reportante y aplica la sanción sobre el denunciado. Si la sanción
     * falla, el reporte recién creado se elimina para no dejar expedientes huérfanos.
     */
    public Sancion aplicarPorModeracion(String administradorId, String reportanteId, String denunciadoId,
                                        TipoReporte tipo, String motivo, String observacion, SancionEntradaDTO dto,
                                        Instant ahora) {
        validar(dto, ahora);
        Usuario denunciado = usuarios.findById(denunciadoId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Usuario no encontrado"));
        if (denunciado.tieneRol(Rol.ADMINISTRADOR)) {
            throw new ExcepcionNegocio("No se puede sancionar a un administrador");
        }
        Reporte reporte = Reporte.radicar(reportanteId, denunciadoId, null, tipo, motivo, List.of(), ahora);
        reporte.iniciarRevision();
        reporte.resolver(DecisionReporte.A_FAVOR_REPORTANTE, AccionPago.NINGUNA, observacion, administradorId, ahora);
        reporte = reportes.save(reporte);
        try {
            return aplicar(administradorId, reporte.getId(), denunciado, dto, ahora);
        } catch (RuntimeException e) {
            try {
                reportes.deleteById(reporte.getId());
            } catch (RuntimeException ex) {
                log.warn("No se pudo compensar el reporte {}", reporte.getId(), ex);
            }
            throw e;
        }
    }

    /** Guarda la sanción y ajusta el estado de la cuenta; si el guardado de la cuenta falla, elimina la sanción. */
    Sancion aplicar(String administradorId, String reporteId, Usuario usuario, SancionEntradaDTO dto, Instant ahora) {
        Instant inicio = dto.inicio() == null ? ahora : dto.inicio();
        Periodo vigencia = switch (dto.tipo()) {
            case SUSPENSION_TEMPORAL -> new Periodo(inicio, dto.fin());
            case BLOQUEO_PERMANENTE -> new Periodo(inicio, null);
            case ADVERTENCIA -> null;
        };
        Sancion sancion = sanciones.save(Sancion.aplicar(reporteId, usuario.getId(), dto.tipo(), vigencia));
        try {
            EstadoCuenta antes = usuario.getEstadoCuenta();
            switch (dto.tipo()) {
                case SUSPENSION_TEMPORAL -> {
                    if (antes == EstadoCuenta.ACTIVA) {
                        usuario.setEstadoCuenta(EstadoCuenta.SUSPENDIDA);
                    }
                }
                case BLOQUEO_PERMANENTE -> usuario.setEstadoCuenta(EstadoCuenta.BLOQUEADA);
                case ADVERTENCIA -> { }
            }
            if (usuario.getEstadoCuenta() != antes) {
                usuarios.save(usuario);
            }
        } catch (RuntimeException e) {
            try {
                sanciones.deleteById(sancion.getId());
            } catch (RuntimeException ex) {
                log.warn("No se pudo compensar la sanción {}", sancion.getId(), ex);
            }
            throw e;
        }
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("usuarioId", usuario.getId());
        detalle.put("tipo", dto.tipo().name());
        detalle.put("reporteId", reporteId);
        if (vigencia != null && vigencia.getFin() != null) {
            detalle.put("fin", vigencia.getFin().toString());
        }
        auditoria.registrarAccion(administradorId, "SANCION_APLICADA", "sancion:" + sancion.getId(), detalle);
        avisar(usuario.getId(), "Sanción aplicada: " + descripcion(dto.tipo()),
                mensajeSancion(dto.tipo(), vigencia));
        return sancion;
    }

    /**
     * Cierra las suspensiones temporales cuya vigencia terminó (VIGENTE o RATIFICADA → CUMPLIDA) y reactiva la
     * cuenta si ninguna otra sanción la mantiene suspendida. Una suspensión en apelación no se toca: la resuelve el
     * administrador. Devuelve cuántas sanciones cerró; un fallo en una no impide procesar las demás.
     */
    public int levantarVencidas(Instant ahora) {
        int cerradas = 0;
        for (Sancion sancion : sanciones.findByEstadoInAndVigenciaFinLessThanEqual(
                List.of(EstadoSancion.VIGENTE, EstadoSancion.RATIFICADA), ahora)) {
            try {
                sancion.cumplir(ahora);
                sanciones.save(sancion);
                reactivarSiCorresponde(sancion.getUsuarioId(), ahora);
                auditoria.registrarAccion(null, "SANCION_CUMPLIDA", "sancion:" + sancion.getId(),
                        Map.of("usuarioId", sancion.getUsuarioId()));
                avisar(sancion.getUsuarioId(), "Tu suspensión terminó", "Tu cuenta volvió a estar activa.");
                cerradas++;
            } catch (RuntimeException e) {
                log.warn("No se pudo levantar la sanción {}", sancion.getId(), e);
            }
        }
        return cerradas;
    }

    private void reactivarSiCorresponde(String usuarioId, Instant ahora) {
        Usuario usuario = usuarios.findById(usuarioId).orElse(null);
        if (usuario == null || usuario.getEstadoCuenta() != EstadoCuenta.SUSPENDIDA) {
            return;
        }
        boolean sigueSuspendida = sanciones.findByUsuarioIdAndEstadoIn(usuarioId,
                        List.of(EstadoSancion.VIGENTE, EstadoSancion.EN_APELACION, EstadoSancion.RATIFICADA))
                .stream().anyMatch(s -> s.suspendeCuenta(ahora));
        if (!sigueSuspendida) {
            usuario.setEstadoCuenta(EstadoCuenta.ACTIVA);
            usuarios.save(usuario);
        }
    }

    private void avisar(String usuarioId, String titulo, String mensaje) {
        try {
            notificaciones.notificar(usuarioId, TipoNotificacion.SANCION_APLICADA, titulo, mensaje, "perfil.html");
        } catch (RuntimeException e) {
            log.warn("No se pudo notificar al usuario {}", usuarioId, e);
        }
    }

    private static String descripcion(TipoSancion tipo) {
        return switch (tipo) {
            case ADVERTENCIA -> "advertencia";
            case SUSPENSION_TEMPORAL -> "suspensión temporal";
            case BLOQUEO_PERMANENTE -> "bloqueo permanente";
        };
    }

    private static String mensajeSancion(TipoSancion tipo, Periodo vigencia) {
        return switch (tipo) {
            case ADVERTENCIA -> "Recibiste una advertencia por incumplir las normas de la comunidad TutoFast.";
            case SUSPENSION_TEMPORAL -> "Tu cuenta está suspendida hasta " + vigencia.getFin()
                    + ". Puedes apelar la decisión desde tu perfil.";
            case BLOQUEO_PERMANENTE -> "Tu cuenta fue bloqueada de forma permanente. Puedes apelar la decisión.";
        };
    }
}
