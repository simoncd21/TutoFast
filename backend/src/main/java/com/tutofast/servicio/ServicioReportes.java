package com.tutofast.servicio;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.tutofast.dto.ApelacionDTO;
import com.tutofast.dto.ApelacionEntradaDTO;
import com.tutofast.dto.ExpedienteReporteDTO;
import com.tutofast.dto.LiquidacionDTO;
import com.tutofast.dto.MensajeDTO;
import com.tutofast.dto.Pagina;
import com.tutofast.dto.ReporteDTO;
import com.tutofast.dto.ReporteEntradaDTO;
import com.tutofast.dto.ReportesMiosDTO;
import com.tutofast.dto.ResolucionApelacionEntradaDTO;
import com.tutofast.dto.ResolucionEntradaDTO;
import com.tutofast.dto.SancionDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.mapper.MensajeMapper;
import com.tutofast.mapper.PagoMapper;
import com.tutofast.mapper.ReporteMapper;
import com.tutofast.mapper.ServicioMapper;
import com.tutofast.modelo.AccionPago;
import com.tutofast.modelo.Apelacion;
import com.tutofast.modelo.DecisionReporte;
import com.tutofast.modelo.EstadoPago;
import com.tutofast.modelo.EstadoReporte;
import com.tutofast.modelo.EstadoSancion;
import com.tutofast.modelo.EstadoServicio;
import com.tutofast.modelo.Mensaje;
import com.tutofast.modelo.Pago;
import com.tutofast.modelo.Reporte;
import com.tutofast.modelo.ResultadoApelacion;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.Sancion;
import com.tutofast.modelo.ServicioTutoria;
import com.tutofast.modelo.SolicitudTutoria;
import com.tutofast.modelo.TipoReporte;
import com.tutofast.modelo.Usuario;
import com.tutofast.repositorio.RepositorioMensaje;
import com.tutofast.repositorio.RepositorioReporte;
import com.tutofast.repositorio.RepositorioSancion;
import com.tutofast.repositorio.RepositorioServicioTutoria;
import com.tutofast.repositorio.RepositorioSolicitudTutoria;
import com.tutofast.repositorio.RepositorioUsuario;
import com.tutofast.seguridad.UsuarioAutenticado;

/**
 * Reportes, sanciones y apelaciones (R14, HU-10).
 *
 * <p><b>Radicación.</b> Un estudiante o tutor reporta a otro usuario, con o sin servicio. Si el reporte nace de un
 * servicio CONFIRMADO o EN_DESARROLLO (o FINALIZADO hace menos de 24 h con el pago todavía retenido) el servicio pasa a
 * EN_REVISION y el pago a CONGELADO: ni la finalización ni el cierre automático lo liquidan mientras el reporte esté
 * abierto. El reporte nace EN_REVISION.
 *
 * <p><b>Resolución.</b> El administrador decide (a favor del reportante o del denunciado, o desestima), ordena la acción
 * sobre el pago (reembolsar → servicio CANCELADO; liberar → servicio FINALIZADO; ninguna → el servicio y el pago se
 * reanudan si no quedan otros reportes abiertos) y puede sancionar al denunciado cuando falla a favor del reportante.
 * Todo se valida antes de cambiar nada; si un paso falla se compensan los anteriores.
 *
 * <p><b>Apelación.</b> El sancionado (aunque su cuenta esté suspendida o bloqueada) presenta un descargo con evidencias:
 * la sanción pasa a EN_APELACION (la cuenta sigue restringida). El administrador la ratifica o la revoca; al revocarla
 * se restablece la cuenta.
 *
 * <p>Las evidencias se suben a GridFS antes de la transacción (nunca dentro de ella) y se borran si el guardado falla.
 */
@Service
public class ServicioReportes {

    private static final Logger log = LoggerFactory.getLogger(ServicioReportes.class);

    public static final int MAXIMO_EVIDENCIAS = 5;
    public static final int MINIMO_MOTIVO = 10;
    public static final int MAXIMO_MOTIVO = 1000;
    public static final int MINIMO_DESCARGO = 10;
    public static final int MAXIMO_DESCARGO = 2000;
    public static final int MAXIMO_OBSERVACION = 1000;
    /** Un servicio ya finalizado puede reportarse mientras no pasen estas horas y su pago siga retenido. */
    static final Duration VENTANA_REPORTE_FINALIZADO = Duration.ofHours(24);
    static final int TAMANO_MAXIMO_PAGINA = 100;

    private static final List<EstadoReporte> ABIERTOS = List.of(EstadoReporte.RADICADO, EstadoReporte.EN_REVISION);
    /** Tipos que solo tienen sentido sobre un servicio concreto. */
    private static final Set<TipoReporte> EXIGEN_SERVICIO = Set.of(
            TipoReporte.INASISTENCIA, TipoReporte.COBRO_EXTERNO, TipoReporte.DESACUERDO_ACADEMICO);

    private final RepositorioReporte reportes;
    private final RepositorioSancion sanciones;
    private final RepositorioServicioTutoria servicios;
    private final RepositorioSolicitudTutoria solicitudes;
    private final RepositorioMensaje mensajes;
    private final RepositorioUsuario usuarios;
    private final ServicioPagos pagos;
    private final ServicioArchivos archivos;
    private final ServicioAuditoria auditoria;
    private final ServicioNotificaciones notificaciones;
    private final AplicadorSanciones aplicador;
    private final ServicioReputacion reputacion;
    private final TransactionTemplate transaccion;

    public ServicioReportes(RepositorioReporte reportes, RepositorioSancion sanciones,
                            RepositorioServicioTutoria servicios, RepositorioSolicitudTutoria solicitudes,
                            RepositorioMensaje mensajes, RepositorioUsuario usuarios, ServicioPagos pagos,
                            ServicioArchivos archivos, ServicioAuditoria auditoria,
                            ServicioNotificaciones notificaciones, AplicadorSanciones aplicador,
                            ServicioReputacion reputacion, PlatformTransactionManager gestorTransacciones) {
        this.reportes = reportes;
        this.sanciones = sanciones;
        this.servicios = servicios;
        this.solicitudes = solicitudes;
        this.mensajes = mensajes;
        this.usuarios = usuarios;
        this.pagos = pagos;
        this.archivos = archivos;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
        this.aplicador = aplicador;
        this.reputacion = reputacion;
        this.transaccion = new TransactionTemplate(gestorTransacciones);
    }

    // ---------------------------------------------------------------- radicación

    /**
     * Radica un reporte (estudiante o tutor). Errores: 400 (datos o más de 5 evidencias), 403 (autorreporte, o no
     * participa en el servicio), 404 (servicio o denunciado), 409 {@code REPORTE_DUPLICADO} y 422 (tipo que exige
     * servicio, denunciado que no es la otra parte, o administrador).
     */
    public ReporteDTO radicarReporte(UsuarioAutenticado usuario, ReporteEntradaDTO dto, List<MultipartFile> evidencias) {
        Usuario reportante = exigirCuentaActiva(usuario.id());
        if (dto == null || dto.tipo() == null) {
            throw datosInvalidos("El tipo de reporte es obligatorio");
        }
        String motivo = limpiar(dto.motivo());
        if (motivo == null || motivo.length() < MINIMO_MOTIVO || motivo.length() > MAXIMO_MOTIVO) {
            throw datosInvalidos("El motivo debe tener entre " + MINIMO_MOTIVO + " y " + MAXIMO_MOTIVO + " caracteres");
        }
        List<MultipartFile> adjuntos = depurar(evidencias);
        String servicioId = limpiar(dto.servicioId());
        String denunciadoId = limpiar(dto.denunciadoId());
        if (usuario.id().equals(denunciadoId)) {
            throw new ExcepcionAutorizacion("No puedes reportarte a ti mismo");
        }

        ServicioTutoria servicio = null;
        if (servicioId != null) {
            servicio = servicios.findById(servicioId)
                    .orElseThrow(() -> new ExcepcionNoEncontrado("Servicio no encontrado"));
            if (!servicio.esParticipante(usuario.id())) {
                throw new ExcepcionAutorizacion("Solo los participantes del servicio pueden reportarlo");
            }
            String otraParte = usuario.id().equals(servicio.getEstudianteId())
                    ? servicio.getTutorId() : servicio.getEstudianteId();
            if (denunciadoId != null && !denunciadoId.equals(otraParte)) {
                throw new ExcepcionNegocio("El denunciado debe ser la otra parte del servicio");
            }
            denunciadoId = otraParte;
        } else {
            if (EXIGEN_SERVICIO.contains(dto.tipo())) {
                throw new ExcepcionNegocio("Un reporte por " + dto.tipo().name().toLowerCase().replace('_', ' ')
                        + " debe indicar el servicio al que se refiere");
            }
            if (denunciadoId == null) {
                throw datosInvalidos("Indique a quién denuncia o el servicio al que se refiere");
            }
        }
        Usuario denunciado = usuarios.findById(denunciadoId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("El usuario denunciado no existe"));
        if (denunciado.tieneRol(Rol.ADMINISTRADOR)) {
            throw new ExcepcionNegocio("No se puede reportar a un administrador");
        }
        if (reportes.existsByReportanteIdAndDenunciadoIdAndServicioIdAndEstadoIn(
                usuario.id(), denunciadoId, servicioId, ABIERTOS)) {
            throw new ExcepcionNegocio("REPORTE_DUPLICADO",
                    "Ya tienes un reporte abierto contra este usuario para este servicio", HttpStatus.CONFLICT);
        }
        Instant ahora = Instant.now();
        // Se valida el modelo antes de subir nada (motivo, autorreporte).
        Reporte.radicar(usuario.id(), denunciadoId, servicioId, dto.tipo(), motivo, List.of(), ahora);

        // Orden: validar → subir evidencias a GridFS (fuera de la transacción) → persistir → congelar → auditar y avisar.
        List<String> subidas = subir(adjuntos);
        Reporte reporte = Reporte.radicar(usuario.id(), denunciadoId, servicioId, dto.tipo(), motivo, subidas, ahora);
        reporte.iniciarRevision();
        boolean congelado;
        try {
            ServicioTutoria objetivo = servicio;
            congelado = Boolean.TRUE.equals(transaccion.execute(estado -> persistirYCongelar(reporte, objetivo, usuario.id(), ahora)));
        } catch (RuntimeException e) {
            subidas.forEach(this::eliminarArchivoSilencioso);
            throw e;
        }
        auditoria.registrarAccion(usuario.id(), "RADICAR_REPORTE", "reporte:" + reporte.getId(),
                detalle("tipo", dto.tipo().name(), "denunciadoId", denunciadoId, "servicioId", servicioId,
                        "evidencias", subidas.size(), "pagoCongelado", congelado));
        String sobreServicio = servicio == null ? "" : " sobre un servicio" + (congelado ? "; el pago quedó congelado" : "");
        avisar(denunciadoId, TipoNotificacion.REPORTE_RECIBIDO, "Recibiste un reporte",
                "Se radicó un reporte por " + describir(dto.tipo()) + " en tu contra" + sobreServicio
                        + ". Un administrador lo revisará.", "reportes.html");
        for (Usuario admin : usuarios.findByRolesContaining(Rol.ADMINISTRADOR)) {
            avisar(admin.getId(), TipoNotificacion.REPORTE_RECIBIDO, "Nuevo reporte por revisar",
                    reportante.getNombre() + " reportó a " + denunciado.getNombre() + " por " + describir(dto.tipo()),
                    "admin.html#reportes");
        }
        Map<String, Usuario> personas = cargar(Set.of(usuario.id(), denunciadoId));
        return ReporteMapper.aDTO(reporte, personas);
    }

    /** Guarda el reporte y, si procede, congela el pago y pone el servicio EN_REVISION. Devuelve si congeló el pago. */
    private boolean persistirYCongelar(Reporte reporte, ServicioTutoria servicio, String actorId, Instant ahora) {
        Reporte guardado = reportes.save(reporte);
        reporte.setId(guardado.getId());
        if (servicio == null) {
            return false;
        }
        Pago pago = pagos.buscarPorServicio(servicio.getId());
        boolean pagoRetenido = pago != null && pago.getEstado() == EstadoPago.RETENIDO;
        boolean enCurso = servicio.getEstado() == EstadoServicio.CONFIRMADO
                || servicio.getEstado() == EstadoServicio.EN_DESARROLLO;
        boolean finalizadoReciente = servicio.getEstado() == EstadoServicio.FINALIZADO
                && servicio.getFechaFinalizacion() != null
                && servicio.getFechaFinalizacion().plus(VENTANA_REPORTE_FINALIZADO).isAfter(ahora);
        if (!pagoRetenido || !(enCurso || finalizadoReciente)) {
            return false; // sin nada que congelar (servicio cancelado, ya en revisión o pago ya liquidado)
        }
        boolean servicioPuestoEnRevision = false;
        try {
            if (enCurso) {
                servicio.ponerEnRevision();
                servicioPuestoEnRevision = true;
            }
            pagos.congelarPago(servicio.getId(), actorId, ahora);
            if (servicioPuestoEnRevision) {
                servicios.save(servicio);
            }
            return true;
        } catch (RuntimeException e) {
            compensarRadicacion(reporte, servicio, servicioPuestoEnRevision, actorId, ahora);
            throw e;
        }
    }

    private void compensarRadicacion(Reporte reporte, ServicioTutoria servicio, boolean servicioEnRevision,
                                     String actorId, Instant ahora) {
        try {
            if (servicioEnRevision) {
                servicio.reanudar();
            }
            Pago pago = pagos.buscarPorServicio(servicio.getId());
            if (pago != null && pago.getEstado() == EstadoPago.CONGELADO) {
                pagos.descongelarPago(servicio.getId(), actorId, ahora);
            }
            if (reporte.getId() != null) {
                reportes.deleteById(reporte.getId());
            }
        } catch (RuntimeException ex) {
            log.warn("No se pudo compensar la radicación del reporte {}", reporte.getId(), ex);
        }
    }

    // ---------------------------------------------------------------- consultas del usuario

    /** Los reportes que radiqué y los que recibí (sin la identidad del reportante ni sus evidencias). */
    public ReportesMiosDTO listarMios(String usuarioId) {
        List<Reporte> radicados = reportes.findByReportanteIdOrderByFechaRadicacionDesc(usuarioId);
        List<Reporte> recibidos = reportes.findByDenunciadoIdOrderByFechaRadicacionDesc(usuarioId);
        Set<String> ids = new LinkedHashSet<>();
        ids.add(usuarioId);
        for (Reporte r : radicados) {
            ids.add(r.getDenunciadoId());
            agregarResolutor(ids, r);
        }
        for (Reporte r : recibidos) {
            agregarResolutor(ids, r);
        }
        Map<String, Usuario> personas = cargar(ids);
        return new ReportesMiosDTO(radicados.stream().map(r -> ReporteMapper.aDTO(r, personas)).toList(),
                recibidos.stream().map(r -> ReporteMapper.aDTORecibido(r, personas)).toList());
    }

    /** Las sanciones del usuario (más recientes primero), incluidas las ya revocadas o cumplidas. */
    public List<SancionDTO> listarSancionesMias(String usuarioId) {
        return aSancionesDTO(sanciones.findByUsuarioIdOrderByIdDesc(usuarioId));
    }

    // ---------------------------------------------------------------- apelación

    /**
     * El sancionado presenta su descargo. La sanción debe ser suya (403) y estar VIGENTE sin apelación pendiente (409
     * {@code APELACION_NO_PERMITIDA}); pasa a EN_APELACION y se avisa a los administradores. No exige cuenta activa:
     * un suspendido o bloqueado sí puede apelar.
     */
    public ApelacionDTO apelarSancion(UsuarioAutenticado usuario, String sancionId, ApelacionEntradaDTO dto,
                                      List<MultipartFile> evidencias) {
        Usuario apelante = usuarios.findById(usuario.id())
                .orElseThrow(() -> new ExcepcionNoEncontrado("Usuario no encontrado"));
        Sancion sancion = sanciones.findById(sancionId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Sanción no encontrada"));
        if (!sancion.getUsuarioId().equals(usuario.id())) {
            throw new ExcepcionAutorizacion("Solo el sancionado puede apelar esta sanción");
        }
        String descargo = dto == null ? null : limpiar(dto.descargo());
        if (descargo == null || descargo.length() < MINIMO_DESCARGO || descargo.length() > MAXIMO_DESCARGO) {
            throw datosInvalidos("El descargo debe tener entre " + MINIMO_DESCARGO + " y " + MAXIMO_DESCARGO + " caracteres");
        }
        List<MultipartFile> adjuntos = depurar(evidencias);
        exigirApelable(sancion);

        List<String> subidas = subir(adjuntos);
        Instant ahora = Instant.now();
        Apelacion apelacion;
        try {
            apelacion = sancion.apelar(descargo, subidas, ahora); // 409 si ya hay una pendiente o no está VIGENTE
            sancion = sanciones.save(sancion);
        } catch (RuntimeException e) {
            subidas.forEach(this::eliminarArchivoSilencioso);
            throw e;
        }
        auditoria.registrarAccion(usuario.id(), "APELACION_PRESENTADA", "sancion:" + sancion.getId(),
                detalle("apelacionId", apelacion.getId(), "tipo", sancion.getTipo().name(),
                        "evidencias", subidas.size()));
        for (Usuario admin : usuarios.findByRolesContaining(Rol.ADMINISTRADOR)) {
            avisar(admin.getId(), TipoNotificacion.REPORTE_RECIBIDO, "Nueva apelación por resolver",
                    apelante.getNombre() + " apeló una sanción (" + sancion.getTipo().name().toLowerCase().replace('_', ' ') + ")",
                    "admin.html#reportes");
        }
        return ReporteMapper.aDTO(apelacion);
    }

    private static void exigirApelable(Sancion sancion) {
        if (sancion.getEstado() != EstadoSancion.VIGENTE || sancion.tieneApelacionPendiente()) {
            throw new ExcepcionNegocio("APELACION_NO_PERMITIDA",
                    "Ya hay una apelación pendiente o la sanción no está vigente", HttpStatus.CONFLICT);
        }
    }

    // ---------------------------------------------------------------- administración: reportes

    /** Cola de reportes (más recientes primero), con filtro opcional por estado. */
    public Pagina<ReporteDTO> listarReportes(EstadoReporte estado, int pagina, int tamano) {
        Pageable paginacion = PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), TAMANO_MAXIMO_PAGINA),
                Sort.by(Sort.Direction.DESC, "fechaRadicacion"));
        Page<Reporte> resultado = estado == null ? reportes.findAll(paginacion) : reportes.findByEstado(estado, paginacion);
        Set<String> ids = new LinkedHashSet<>();
        for (Reporte r : resultado.getContent()) {
            ids.add(r.getReportanteId());
            ids.add(r.getDenunciadoId());
            agregarResolutor(ids, r);
        }
        Map<String, Usuario> personas = cargar(ids);
        return Pagina.de(resultado, r -> ReporteMapper.aDTO(r, personas));
    }

    /** Expediente completo: reporte, servicio, pago, historial íntegro del chat y sanciones derivadas. */
    public ExpedienteReporteDTO consultarExpediente(String reporteId) {
        Reporte reporte = obtenerReporte(reporteId);
        ServicioTutoria servicio = reporte.getServicioId() == null ? null
                : servicios.findById(reporte.getServicioId()).orElse(null);
        Pago pago = servicio == null ? null : pagos.buscarPorServicio(servicio.getId());
        List<Mensaje> chat = servicio == null ? List.of() : mensajes
                .findBySolicitudIdAndTutorIdOrderByMarcaTemporalAsc(servicio.getSolicitudId(), servicio.getTutorId());
        List<Sancion> derivadas = sanciones.findByReporteId(reporte.getId());

        Set<String> ids = new LinkedHashSet<>();
        ids.add(reporte.getReportanteId());
        ids.add(reporte.getDenunciadoId());
        agregarResolutor(ids, reporte);
        if (servicio != null) {
            ids.add(servicio.getEstudianteId());
            ids.add(servicio.getTutorId());
        }
        chat.forEach(m -> ids.add(m.getEmisorId()));
        derivadas.forEach(s -> ids.add(s.getUsuarioId()));
        Map<String, Usuario> personas = cargar(ids);

        SolicitudTutoria solicitud = servicio == null ? null : solicitudes.findById(servicio.getSolicitudId()).orElse(null);
        List<MensajeDTO> historial = chat.stream().map(m -> {
            Usuario emisor = m.getEmisorId() == null ? null : personas.get(m.getEmisorId());
            return MensajeMapper.aDTO(m, emisor == null ? null : emisor.getNombre(), true);
        }).toList();
        LiquidacionDTO liquidacion = servicio == null || pago == null ? null : PagoMapper.aLiquidacionDTO(pago, servicio);
        return new ExpedienteReporteDTO(ReporteMapper.aDTO(reporte, personas),
                servicio == null ? null : ServicioMapper.aDTO(servicio, solicitud,
                        personas.get(servicio.getEstudianteId()), personas.get(servicio.getTutorId()), pago),
                liquidacion, historial,
                derivadas.stream().map(s -> ReporteMapper.aDTO(s, reporte, personas)).toList());
    }

    /**
     * Resuelve un reporte EN_REVISION (409 si ya se resolvió). Antes de cambiar nada se comprueba que la acción de
     * pago exista y proceda, que la sanción sea válida y que solo se sancione a favor del reportante. Orden: sanción →
     * reporte → pago y servicio; si un paso falla se compensan los anteriores. Con {@code accionPago=NINGUNA} el
     * servicio y el pago se reanudan si no quedan otros reportes abiertos sobre el mismo servicio.
     */
    public ReporteDTO resolverReporte(String administradorId, String reporteId, ResolucionEntradaDTO dto) {
        Reporte reporte = obtenerReporte(reporteId);
        if (dto == null || dto.decision() == null) {
            throw datosInvalidos("La decisión es obligatoria");
        }
        String observacion = limpiar(dto.observacion());
        if (observacion == null || observacion.length() > MAXIMO_OBSERVACION) {
            throw datosInvalidos("La observación es obligatoria y admite hasta " + MAXIMO_OBSERVACION + " caracteres");
        }
        if (reporte.getEstado() == EstadoReporte.RESUELTO || reporte.getEstado() == EstadoReporte.DESESTIMADO) {
            throw new ExcepcionNegocio("REPORTE_YA_RESUELTO", "El reporte ya fue resuelto", HttpStatus.CONFLICT);
        }
        AccionPago accion = dto.accionPago() == null ? AccionPago.NINGUNA : dto.accionPago();
        ServicioTutoria servicio = reporte.getServicioId() == null ? null
                : servicios.findById(reporte.getServicioId()).orElse(null);
        Instant ahora = Instant.now();

        // Todas las comprobaciones antes de cambiar nada.
        if (accion != AccionPago.NINGUNA && servicio == null) {
            throw new ExcepcionNegocio("La acción de pago exige un reporte ligado a un servicio");
        }
        if (dto.decision() == DecisionReporte.DESESTIMADO && accion != AccionPago.NINGUNA) {
            throw new ExcepcionNegocio("Un reporte desestimado no puede ordenar movimientos de pago");
        }
        Pago pago = servicio == null ? null : pagos.buscarPorServicio(servicio.getId());
        if (accion == AccionPago.REEMBOLSAR && (pago == null || pago.getEstado() != EstadoPago.CONGELADO)) {
            throw new ExcepcionNegocio("PAGO_NO_REEMBOLSABLE",
                    "Solo se puede reembolsar un pago congelado por este reporte", HttpStatus.CONFLICT);
        }
        if (accion == AccionPago.LIBERAR && (pago == null
                || (pago.getEstado() != EstadoPago.CONGELADO && pago.getEstado() != EstadoPago.RETENIDO))) {
            throw new ExcepcionNegocio("PAGO_NO_LIBERABLE",
                    "Solo se puede liberar un pago retenido o congelado", HttpStatus.CONFLICT);
        }
        Usuario denunciado = null;
        if (dto.sancion() != null) {
            if (dto.decision() != DecisionReporte.A_FAVOR_REPORTANTE) {
                throw datosInvalidos("Solo se puede sancionar cuando la decisión es a favor del reportante");
            }
            aplicador.validar(dto.sancion(), ahora);
            denunciado = usuarios.findById(reporte.getDenunciadoId())
                    .orElseThrow(() -> new ExcepcionNoEncontrado("Usuario no encontrado"));
            if (denunciado.tieneRol(Rol.ADMINISTRADOR)) {
                throw new ExcepcionNegocio("No se puede sancionar a un administrador");
            }
        }

        Reporte.Resolucion anterior = reporte.getResolucion();
        EstadoReporte estadoAnterior = reporte.getEstado();
        Sancion sancion = null;
        try {
            if (reporte.getEstado() == EstadoReporte.RADICADO) {
                reporte.iniciarRevision();
            }
            if (dto.sancion() != null) {
                sancion = aplicador.aplicar(administradorId, reporte.getId(), denunciado, dto.sancion(), ahora);
            }
            reporte.resolver(dto.decision(), accion, observacion, administradorId, ahora);
            reportes.save(reporte);
            ejecutarAccionDePago(reporte, servicio, accion, administradorId, ahora);
        } catch (RuntimeException e) {
            restaurar(reporte, estadoAnterior, anterior);
            if (sancion != null) {
                aplicador.descartar(sancion, ahora);
            }
            throw e;
        }

        auditoria.registrarAccion(administradorId, "RESOLVER_REPORTE", "reporte:" + reporte.getId(),
                detalle("decision", dto.decision().name(), "accionPago", accion.name(),
                        "servicioId", reporte.getServicioId(), "sancionId", sancion == null ? null : sancion.getId(),
                        "observacion", observacion));
        notificarResolucion(reporte, servicio, accion, dto.decision(), sancion != null);
        Set<String> ids = new LinkedHashSet<>(List.of(reporte.getReportanteId(), reporte.getDenunciadoId(), administradorId));
        return ReporteMapper.aDTO(reporte, cargar(ids));
    }

    private void ejecutarAccionDePago(Reporte reporte, ServicioTutoria servicio, AccionPago accion,
                                      String administradorId, Instant ahora) {
        if (servicio == null) {
            return;
        }
        switch (accion) {
            case REEMBOLSAR -> {
                pagos.reembolsarPago(servicio.getId(), administradorId, ahora);
                if (servicio.getEstado() == EstadoServicio.EN_REVISION) {
                    servicio.resolverCancelando();
                    servicios.save(servicio);
                }
            }
            case LIBERAR -> {
                pagos.liberarPago(servicio, administradorId, ahora);
                if (servicio.getEstado() == EstadoServicio.EN_REVISION) {
                    servicio.resolverFinalizando(ahora);
                    servicios.save(servicio);
                }
                try {
                    reputacion.recalcularTutor(servicio.getTutorId());
                } catch (RuntimeException e) {
                    log.warn("No se pudo recalcular los servicios completados del tutor {}", servicio.getTutorId(), e);
                }
            }
            case NINGUNA -> reanudarSiNoQuedanReportes(reporte, servicio, administradorId, ahora);
        }
    }

    /** Sin otros reportes abiertos, el servicio vuelve a su curso y el pago congelado a la custodia normal. */
    private void reanudarSiNoQuedanReportes(Reporte resuelto, ServicioTutoria servicio, String administradorId, Instant ahora) {
        boolean otrosAbiertos = reportes.findByServicioId(servicio.getId()).stream()
                .anyMatch(r -> !r.getId().equals(resuelto.getId()) && r.estaAbierto());
        if (otrosAbiertos) {
            return;
        }
        Pago pago = pagos.buscarPorServicio(servicio.getId());
        if (pago != null && pago.getEstado() == EstadoPago.CONGELADO) {
            pagos.descongelarPago(servicio.getId(), administradorId, ahora);
        }
        if (servicio.getEstado() == EstadoServicio.EN_REVISION) {
            servicio.reanudar();
            servicios.save(servicio);
        }
    }

    private void restaurar(Reporte reporte, EstadoReporte estado, Reporte.Resolucion resolucion) {
        try {
            reporte.setEstado(estado);
            reporte.setResolucion(resolucion);
            reportes.save(reporte);
        } catch (RuntimeException e) {
            log.warn("No se pudo restaurar el reporte {}", reporte.getId(), e);
        }
    }

    private void notificarResolucion(Reporte reporte, ServicioTutoria servicio, AccionPago accion,
                                     DecisionReporte decision, boolean conSancion) {
        String resultado = switch (decision) {
            case A_FAVOR_REPORTANTE -> "a favor del reportante";
            case A_FAVOR_DENUNCIADO -> "a favor del denunciado";
            case DESESTIMADO -> "desestimado";
        };
        String detallePago = switch (accion) {
            case REEMBOLSAR -> " Se reembolsó el pago al estudiante y el servicio quedó cancelado.";
            case LIBERAR -> " Se liberó el pago al tutor y el servicio quedó finalizado.";
            case NINGUNA -> servicio == null ? "" : " El servicio sigue su curso.";
        };
        String enlace = "reportes.html";
        avisar(reporte.getReportanteId(), TipoNotificacion.REPORTE_RESUELTO, "Tu reporte fue resuelto",
                "El reporte fue resuelto " + resultado + "." + detallePago, enlace);
        avisar(reporte.getDenunciadoId(), TipoNotificacion.REPORTE_RESUELTO, "Un reporte en tu contra fue resuelto",
                "El reporte fue resuelto " + resultado + "." + detallePago
                        + (conSancion ? " Se te aplicó una sanción: puedes apelarla desde tu perfil." : ""), enlace);
        if (servicio != null && accion == AccionPago.LIBERAR) {
            String enlaceServicio = "negociacion.html?id=" + servicio.getId();
            avisar(servicio.getTutorId(), TipoNotificacion.PAGO_LIBERADO, "Pago liberado",
                    "El administrador liberó el pago del servicio tras revisar el reporte", enlaceServicio);
            avisar(servicio.getEstudianteId(), TipoNotificacion.CALIFICACION_PENDIENTE, "Califica tu tutoría",
                    "El servicio quedó finalizado: ya puedes calificarlo", enlaceServicio);
            avisar(servicio.getTutorId(), TipoNotificacion.CALIFICACION_PENDIENTE, "Califica a tu estudiante",
                    "El servicio quedó finalizado: ya puedes calificarlo", enlaceServicio);
        }
    }

    // ---------------------------------------------------------------- administración: sanciones y apelaciones

    /** Sanciones (más recientes primero) con filtro opcional por estado; {@code EN_APELACION} es la cola de apelaciones. */
    public Pagina<SancionDTO> listarSanciones(EstadoSancion estado, int pagina, int tamano) {
        Pageable paginacion = PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), TAMANO_MAXIMO_PAGINA),
                Sort.by(Sort.Direction.DESC, "id"));
        Page<Sancion> resultado = estado == null ? sanciones.findAll(paginacion) : sanciones.findByEstado(estado, paginacion);
        List<SancionDTO> convertidas = aSancionesDTO(resultado.getContent());
        return new Pagina<>(convertidas, resultado.getNumber(), resultado.getSize(), resultado.getTotalElements(),
                resultado.getTotalPages());
    }

    /**
     * Resuelve la apelación pendiente: RATIFICADA mantiene la sanción (si su vigencia ya terminó se cierra como
     * CUMPLIDA) y REVOCADA la deja sin efecto y restablece la cuenta. 404 si la sanción o la apelación no existen,
     * 409 si la apelación ya se resolvió o la sanción no está en apelación, 422 si el resultado es PENDIENTE.
     */
    public SancionDTO resolverApelacion(String administradorId, String sancionId, String apelacionId,
                                        ResolucionApelacionEntradaDTO dto) {
        Sancion sancion = sanciones.findById(sancionId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Sanción no encontrada"));
        if (dto == null || dto.resultado() == null) {
            throw datosInvalidos("El resultado es obligatorio");
        }
        String observacion = limpiar(dto.observacion());
        if (observacion == null || observacion.length() > MAXIMO_OBSERVACION) {
            throw datosInvalidos("La observación es obligatoria y admite hasta " + MAXIMO_OBSERVACION + " caracteres");
        }
        Instant ahora = Instant.now();
        Apelacion apelacion = sancion.getApelaciones().stream().filter(a -> a.getId().equals(apelacionId)).findFirst()
                .orElseThrow(() -> new ExcepcionNoEncontrado("Apelación no encontrada"));
        if (!apelacion.estaPendiente()) {
            throw new ExcepcionNegocio("APELACION_YA_RESUELTA", "La apelación ya fue resuelta", HttpStatus.CONFLICT);
        }
        if (dto.resultado() == ResultadoApelacion.PENDIENTE) {
            throw new ExcepcionNegocio("La resolución debe ser RATIFICADA o REVOCADA");
        }
        try {
            sancion.resolverApelacion(apelacionId, dto.resultado(), ahora, observacion, administradorId);
            sancion = sanciones.save(sancion);
            if (dto.resultado() == ResultadoApelacion.REVOCADA) {
                aplicador.restablecerCuenta(sancion, ahora);
            } else {
                aplicador.cerrarSiVencida(sancion, ahora);
            }
        } catch (RuntimeException e) {
            deshacerResolucion(sancion, apelacion);
            throw e;
        }
        auditoria.registrarAccion(administradorId, "APELACION_RESUELTA", "sancion:" + sancion.getId(),
                detalle("apelacionId", apelacionId, "resultado", dto.resultado().name(),
                        "usuarioId", sancion.getUsuarioId(), "observacion", observacion));
        boolean revocada = dto.resultado() == ResultadoApelacion.REVOCADA;
        avisar(sancion.getUsuarioId(), TipoNotificacion.APELACION_RESUELTA,
                revocada ? "Tu apelación fue aceptada" : "Tu apelación fue rechazada",
                revocada ? "La sanción fue revocada" + (sancion.getTipo() == com.tutofast.modelo.TipoSancion.ADVERTENCIA
                                ? "." : " y tu cuenta fue restablecida.")
                        : "La sanción fue ratificada. " + observacion,
                "perfil.html");
        Reporte reporte = reportes.findById(sancion.getReporteId()).orElse(null);
        return ReporteMapper.aDTO(sancion, reporte, cargar(Set.of(sancion.getUsuarioId())));
    }

    private void deshacerResolucion(Sancion sancion, Apelacion apelacion) {
        try {
            apelacion.setResultado(ResultadoApelacion.PENDIENTE);
            apelacion.setFechaResolucion(null);
            apelacion.setObservacionResolucion(null);
            apelacion.setResueltaPor(null);
            sancion.setEstado(EstadoSancion.EN_APELACION);
            sanciones.save(sancion);
        } catch (RuntimeException ex) {
            log.warn("No se pudo deshacer la resolución de la sanción {}", sancion.getId(), ex);
        }
    }

    // ---------------------------------------------------------------- internos

    private Usuario exigirCuentaActiva(String usuarioId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> new ExcepcionNoEncontrado("Usuario no encontrado"));
        if (!u.estaActiva()) {
            throw new ExcepcionAutorizacion("Su cuenta no está activa; no puede radicar reportes");
        }
        return u;
    }

    private Reporte obtenerReporte(String id) {
        return reportes.findById(id).orElseThrow(() -> new ExcepcionNoEncontrado("Reporte no encontrado"));
    }

    private List<SancionDTO> aSancionesDTO(List<Sancion> lista) {
        Map<String, Reporte> deReporte = new HashMap<>();
        for (Sancion s : lista) {
            if (s.getReporteId() != null && !deReporte.containsKey(s.getReporteId())) {
                reportes.findById(s.getReporteId()).ifPresent(r -> deReporte.put(r.getId(), r));
            }
        }
        Map<String, Usuario> personas = cargar(lista.stream().map(Sancion::getUsuarioId).collect(Collectors.toSet()));
        return lista.stream().map(s -> ReporteMapper.aDTO(s, deReporte.get(s.getReporteId()), personas)).toList();
    }

    private static void agregarResolutor(Set<String> ids, Reporte r) {
        if (r.getResolucion() != null && r.getResolucion().getResueltoPor() != null) {
            ids.add(r.getResolucion().getResueltoPor());
        }
    }

    private Map<String, Usuario> cargar(Set<String> ids) {
        Set<String> validos = ids.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<String, Usuario> mapa = new HashMap<>();
        if (!validos.isEmpty()) {
            usuarios.findAllById(validos).forEach(u -> mapa.put(u.getId(), u));
        }
        return mapa;
    }

    /** Quita las partes vacías y limita a {@link #MAXIMO_EVIDENCIAS} (400 si hay más). */
    private static List<MultipartFile> depurar(List<MultipartFile> evidencias) {
        List<MultipartFile> validas = evidencias == null ? List.of()
                : evidencias.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (validas.size() > MAXIMO_EVIDENCIAS) {
            throw datosInvalidos("Se admiten hasta " + MAXIMO_EVIDENCIAS + " evidencias");
        }
        return validas;
    }

    /** Sube cada archivo a GridFS; si alguno falla borra los ya subidos y propaga el error. */
    private List<String> subir(List<MultipartFile> adjuntos) {
        List<String> ids = new ArrayList<>();
        try {
            for (MultipartFile f : adjuntos) {
                ids.add(archivos.guardar(f).archivoId());
            }
        } catch (RuntimeException e) {
            ids.forEach(this::eliminarArchivoSilencioso);
            throw e;
        }
        return ids;
    }

    private void eliminarArchivoSilencioso(String archivoId) {
        try {
            archivos.eliminar(archivoId);
        } catch (RuntimeException e) {
            log.warn("No se pudo compensar el archivo {}", archivoId, e);
        }
    }

    /** Las notificaciones son secundarias: si fallan, la operación ya hecha no se revierte. */
    private void avisar(String usuarioId, TipoNotificacion tipo, String titulo, String mensaje, String enlace) {
        if (usuarioId == null) {
            return;
        }
        try {
            notificaciones.notificar(usuarioId, tipo, titulo, mensaje, enlace);
        } catch (RuntimeException e) {
            log.warn("No se pudo notificar al usuario {}", usuarioId, e);
        }
    }

    private static String describir(TipoReporte tipo) {
        return switch (tipo) {
            case INASISTENCIA -> "inasistencia";
            case COBRO_EXTERNO -> "cobro externo";
            case FRAUDE_ACADEMICO -> "fraude académico";
            case CONDUCTA_ABUSIVA -> "conducta abusiva";
            case ACOSO -> "acoso";
            case FALSEDAD_CREDENCIALES -> "falsedad de credenciales";
            case DESACUERDO_ACADEMICO -> "desacuerdo académico";
        };
    }

    private static String limpiar(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static ExcepcionNegocio datosInvalidos(String mensaje) {
        return new ExcepcionNegocio("VALIDACION", mensaje, HttpStatus.BAD_REQUEST);
    }

    private static Map<String, Object> detalle(Object... pares) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pares.length; i += 2) {
            if (pares[i + 1] != null) {
                mapa.put((String) pares[i], pares[i + 1]);
            }
        }
        return mapa;
    }
}
