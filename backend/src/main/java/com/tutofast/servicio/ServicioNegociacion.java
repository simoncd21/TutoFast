package com.tutofast.servicio;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.tutofast.dto.CierreAutomaticoDTO;
import com.tutofast.dto.EnlaceServicioDTO;
import com.tutofast.dto.LiquidacionDTO;
import com.tutofast.dto.ServicioDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.mapper.PagoMapper;
import com.tutofast.mapper.ServicioMapper;
import com.tutofast.modelo.EstadoPago;
import com.tutofast.modelo.EstadoPropuesta;
import com.tutofast.modelo.EstadoReporte;
import com.tutofast.modelo.EstadoServicio;
import com.tutofast.modelo.EstadoSolicitud;
import com.tutofast.modelo.Pago;
import com.tutofast.modelo.Propuesta;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.ServicioTutoria;
import com.tutofast.modelo.SolicitudTutoria;
import com.tutofast.modelo.Usuario;
import com.tutofast.repositorio.RepositorioPropuesta;
import com.tutofast.repositorio.RepositorioReporte;
import com.tutofast.repositorio.RepositorioServicioTutoria;
import com.tutofast.repositorio.RepositorioSolicitudTutoria;
import com.tutofast.repositorio.RepositorioUsuario;
import com.tutofast.seguridad.UsuarioAutenticado;

/**
 * Confirmación del servicio (R09–R12): formaliza la propuesta aceptada, congela precio y comisión, retiene el pago
 * simulado y descarta las demás propuestas. También gestiona el enlace y el inicio del servicio.
 *
 * <p>Orden de {@link #confirmarServicio}: primero se reserva el servicio (el índice único {@code uk_servicio_solicitud}
 * actúa de cerrojo: una segunda confirmación concurrente nunca llega a cobrar), luego se cobra, y solo si todo sale
 * bien se formaliza la solicitud. Si algo falla se compensan los guardados (con Atlas además revierte la transacción).
 */
@Service
public class ServicioNegociacion {

    private static final Logger log = LoggerFactory.getLogger(ServicioNegociacion.class);

    /** Reportes que bloquean la finalización y el cierre automático de un servicio. */
    private static final List<EstadoReporte> ESTADOS_REPORTE_ABIERTO = List.of(EstadoReporte.RADICADO, EstadoReporte.EN_REVISION);
    /** Con cuánta antelación se recuerda una sesión confirmada. */
    static final Duration VENTANA_RECORDATORIO = Duration.ofHours(24);

    private final RepositorioSolicitudTutoria solicitudes;
    private final RepositorioPropuesta propuestas;
    private final RepositorioServicioTutoria servicios;
    private final RepositorioUsuario usuarios;
    private final ServicioPropuestas servicioPropuestas;
    private final ServicioPagos pagos;
    private final ServicioCatalogo catalogo;
    private final ServicioAuditoria auditoria;
    private final ServicioNotificaciones notificaciones;
    private final ServicioMensajeria mensajeria;
    private final ServicioReputacion reputacion;
    private final RepositorioReporte reportes;
    /** Una transacción por servicio en los procesos por lotes (con el gestor no-op es solo un envoltorio). */
    private final TransactionTemplate transaccion;

    public ServicioNegociacion(RepositorioSolicitudTutoria solicitudes, RepositorioPropuesta propuestas,
                               RepositorioServicioTutoria servicios, RepositorioUsuario usuarios,
                               ServicioPropuestas servicioPropuestas, ServicioPagos pagos, ServicioCatalogo catalogo,
                               ServicioAuditoria auditoria, ServicioNotificaciones notificaciones,
                               ServicioMensajeria mensajeria, ServicioReputacion reputacion,
                               RepositorioReporte reportes, PlatformTransactionManager gestorTransacciones) {
        this.solicitudes = solicitudes;
        this.propuestas = propuestas;
        this.servicios = servicios;
        this.usuarios = usuarios;
        this.servicioPropuestas = servicioPropuestas;
        this.pagos = pagos;
        this.catalogo = catalogo;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
        this.mensajeria = mensajeria;
        this.reputacion = reputacion;
        this.reportes = reportes;
        this.transaccion = new TransactionTemplate(gestorTransacciones);
    }

    // ---- Confirmación ----

    /**
     * El estudiante dueño confirma al tutor seleccionado y autoriza el pago simulado. Errores: 403 (no es el dueño),
     * 404, 409 (ya confirmada, no hay tutor seleccionado o el tutor ya no está disponible) y 422
     * {@code PAGO_RECHAZADO} (sin ningún cambio de estado).
     */
    @Transactional
    public ServicioDTO confirmarServicio(String estudianteId, String solicitudId, String tokenPagoSimulado) {
        Usuario estudiante = usuarios.findById(estudianteId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Usuario no encontrado"));
        if (!estudiante.estaActiva() || !estudiante.tieneRol(Rol.ESTUDIANTE)) {
            throw new ExcepcionAutorizacion("Solo un estudiante con la cuenta activa puede confirmar un servicio");
        }
        SolicitudTutoria solicitud = solicitudes.findById(solicitudId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Solicitud no encontrada"));
        if (!solicitud.getEstudianteId().equals(estudianteId)) {
            throw new ExcepcionAutorizacion("Solo el dueño de la solicitud puede confirmar el servicio");
        }
        if (solicitud.getEstado() == EstadoSolicitud.FORMALIZADA || servicios.findBySolicitudId(solicitudId).isPresent()) {
            throw yaConfirmado();
        }
        if (solicitud.getEstado() != EstadoSolicitud.TUTOR_SELECCIONADO) {
            throw new ExcepcionNegocio("SOLICITUD_NO_CONFIRMABLE",
                    "Primero seleccione una propuesta: la solicitud está en estado " + solicitud.getEstado(),
                    HttpStatus.CONFLICT);
        }
        Propuesta elegida = propuestas.findFirstBySolicitudIdAndEstado(solicitudId, EstadoPropuesta.ACEPTADA)
                .orElseThrow(() -> new ExcepcionNegocio("PROPUESTA_NO_SELECCIONADA",
                        "La solicitud no tiene una propuesta seleccionada", HttpStatus.CONFLICT));
        Usuario tutor = usuarios.findById(elegida.getTutorId()).orElse(null);
        if (tutor == null || !tutor.estaActiva()) {
            throw new ExcepcionNegocio("TUTOR_NO_DISPONIBLE", "El tutor ya no está disponible", HttpStatus.CONFLICT);
        }

        BigDecimal comision = catalogo.consultarComision().porcentaje();
        Instant ahora = Instant.now();
        // Congela precio, duración, modalidad y comisión; deja la solicitud FORMALIZADA solo en memoria (si algo falla
        // más abajo se restaura TUTOR_SELECCIONADO).
        ServicioTutoria servicio = ServicioTutoria.confirmar(solicitud, elegida, comision, ahora);
        try {
            servicio = reservarServicio(servicio);
        } catch (RuntimeException e) {
            solicitud.setEstado(EstadoSolicitud.TUTOR_SELECCIONADO);
            throw e;
        }

        Pago pago;
        try {
            pago = pagos.retenerFondos(servicio, tokenPagoSimulado); // 422 si la pasarela rechaza
        } catch (RuntimeException e) {
            solicitud.setEstado(EstadoSolicitud.TUTOR_SELECCIONADO);
            descartarServicio(servicio);
            throw e;
        }
        try {
            solicitud.setFechaActualizacion(ahora);
            solicitudes.save(solicitud);
        } catch (RuntimeException e) {
            solicitud.setEstado(EstadoSolicitud.TUTOR_SELECCIONADO);
            pagos.descartarPago(pago);
            descartarServicio(servicio);
            throw e;
        }

        int descartadas = servicioPropuestas.descartarPropuestasDeSolicitud(solicitudId, elegida.getId(),
                "El estudiante confirmó el servicio con otro tutor");
        int hilosCerrados = cerrarHilosNoElegidos(solicitudId, servicio.getTutorId());
        auditoria.registrarAccion(estudianteId, "SERVICIO_CONFIRMADO", "servicio:" + servicio.getId(),
                detalle("solicitudId", solicitudId, "tutorId", servicio.getTutorId(),
                        "precioCOP", servicio.getPrecioFinalCOP().toPlainString(),
                        "comisionPct", comision.toPlainString(), "propuestasDescartadas", descartadas,
                        "hilosCerrados", hilosCerrados));
        String enlace = "negociacion.html?id=" + servicio.getId();
        avisar(servicio.getTutorId(), TipoNotificacion.SERVICIO_CONFIRMADO, "Servicio confirmado",
                estudiante.getNombre() + " confirmó «" + solicitud.getTema() + "»; los fondos quedaron retenidos", enlace);
        avisar(estudianteId, TipoNotificacion.SERVICIO_CONFIRMADO, "Servicio confirmado",
                "Tu tutoría «" + solicitud.getTema() + "» con " + tutor.getNombre() + " quedó confirmada y el pago retenido",
                enlace);
        return ServicioMapper.aDTO(servicio, solicitud, estudiante, tutor, pago);
    }

    // ---- Consultas ----

    /** Servicios del usuario como estudiante o tutor, los más recientes primero; {@code estado} filtra si no es nulo. */
    public List<ServicioDTO> listarMios(String usuarioId, EstadoServicio estado) {
        List<ServicioTutoria> lista = servicios.findByEstudianteIdOrTutorIdOrderByFechaConfirmacionDesc(usuarioId, usuarioId)
                .stream().filter(s -> estado == null || s.getEstado() == estado).toList();
        return vistas(lista);
    }

    /** Un participante o un administrador ve el servicio. */
    public ServicioDTO obtener(UsuarioAutenticado usuario, String servicioId) {
        return vistas(List.of(obtenerAccesible(usuario, servicioId))).get(0);
    }

    // ---- Ciclo del servicio ----

    /** El tutor del servicio fija el lugar o el enlace; solo en CONFIRMADO y sin tocar precio, duración ni modalidad. */
    public ServicioDTO actualizarEnlace(String tutorId, String servicioId, EnlaceServicioDTO dto) {
        ServicioTutoria servicio = servicios.findById(servicioId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Servicio no encontrado"));
        if (!servicio.getTutorId().equals(tutorId)) {
            throw new ExcepcionAutorizacion("Solo el tutor del servicio puede indicar el lugar o el enlace");
        }
        if (dto.precioFinalCOP() != null || dto.duracion() != null || dto.modalidad() != null) {
            throw new ExcepcionNegocio("SERVICIO_INMUTABLE",
                    "El precio, la duración y la modalidad no pueden modificarse tras la confirmación", HttpStatus.CONFLICT);
        }
        servicio.actualizarLugarOEnlace(dto.lugarOEnlace().strip()); // 409 si ya no está CONFIRMADO
        servicio = servicios.save(servicio);
        auditoria.registrarAccion(tutorId, "SERVICIO_ENLACE_ACTUALIZADO", "servicio:" + servicio.getId(),
                detalle("solicitudId", servicio.getSolicitudId()));
        avisar(servicio.getEstudianteId(), TipoNotificacion.SERVICIO_CONFIRMADO, "Lugar o enlace actualizado",
                "El tutor indicó dónde se realizará la tutoría", "negociacion.html?id=" + servicio.getId());
        return vistas(List.of(servicio)).get(0);
    }

    /** CONFIRMADO → EN_DESARROLLO, por cualquiera de los dos participantes. */
    public ServicioDTO iniciarSesion(String usuarioId, String servicioId) {
        ServicioTutoria servicio = servicios.findById(servicioId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Servicio no encontrado"));
        if (!servicio.esParticipante(usuarioId)) {
            throw new ExcepcionAutorizacion("Solo los participantes pueden iniciar el servicio");
        }
        servicio.iniciarSesion(Instant.now()); // 409 si no está CONFIRMADO
        servicio = servicios.save(servicio);
        auditoria.registrarAccion(usuarioId, "SERVICIO_INICIADO", "servicio:" + servicio.getId(),
                detalle("solicitudId", servicio.getSolicitudId()));
        return vistas(List.of(servicio)).get(0);
    }

    // ---- Finalización y liquidación ----

    /**
     * El estudiante del servicio lo da por finalizado: EN_DESARROLLO → FINALIZADO, el pago se libera al tutor con la
     * comisión congelada y se recalcula {@code serviciosCompletados}. Se abre el plazo de 14 días para calificar.
     * Errores: 403 (no es el estudiante del servicio), 404, 409 (ya finalizado, en revisión, sin iniciar, con un
     * reporte abierto o con el pago en un estado que no se puede liberar).
     */
    @Transactional
    public LiquidacionDTO finalizarServicio(String estudianteId, String servicioId) {
        ServicioTutoria servicio = servicios.findById(servicioId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Servicio no encontrado"));
        if (!servicio.getEstudianteId().equals(estudianteId)) {
            throw new ExcepcionAutorizacion("Solo el estudiante del servicio puede finalizarlo");
        }
        Pago pago = ejecutarFinalizacion(servicio, estudianteId);
        return PagoMapper.aLiquidacionDTO(pago, servicio);
    }

    /**
     * Cierre automático: finaliza los servicios EN_DESARROLLO cuyo fin programado más 24 h ya pasó (o todos con
     * {@code ignorarPlazo}, para la demostración) y que no tienen un reporte abierto. Cada servicio se cierra en su
     * propia transacción: uno que falle se cuenta como fallido y no impide cerrar los demás.
     */
    public CierreAutomaticoDTO cerrarAutomaticamente(boolean ignorarPlazo) {
        return cerrarAutomaticamente(Instant.now(), ignorarPlazo);
    }

    public CierreAutomaticoDTO cerrarAutomaticamente(Instant ahora, boolean ignorarPlazo) {
        List<ServicioTutoria> enCurso = servicios.findByEstado(EstadoServicio.EN_DESARROLLO);
        int omitidosPorPlazo = 0;
        int omitidosPorReporte = 0;
        int fallidos = 0;
        List<String> cerrados = new ArrayList<>();
        for (ServicioTutoria candidato : enCurso) {
            if (!candidato.puedeCerrarseAutomaticamente(ahora, ignorarPlazo)) {
                omitidosPorPlazo++;
                continue;
            }
            if (reportes.existsByServicioIdAndEstadoIn(candidato.getId(), ESTADOS_REPORTE_ABIERTO)) {
                omitidosPorReporte++;
                continue;
            }
            try {
                transaccion.executeWithoutResult(estado -> {
                    ServicioTutoria servicio = servicios.findById(candidato.getId())
                            .orElseThrow(() -> new ExcepcionNoEncontrado("Servicio no encontrado"));
                    ejecutarFinalizacion(servicio, null);
                });
                cerrados.add(candidato.getId());
            } catch (RuntimeException e) {
                fallidos++;
                log.warn("No se pudo cerrar automáticamente el servicio {}", candidato.getId(), e);
            }
        }
        return new CierreAutomaticoDTO(enCurso.size(), cerrados.size(), omitidosPorReporte, omitidosPorPlazo, fallidos,
                List.copyOf(cerrados));
    }

    /** Demostración: ejecuta ahora el cierre automático y lo deja en la auditoría (solo ADMINISTRADOR). */
    public CierreAutomaticoDTO simularCierreAutomatico(String administradorId, boolean ignorarPlazo) {
        CierreAutomaticoDTO resultado = cerrarAutomaticamente(ignorarPlazo);
        auditoria.registrarAccion(administradorId, "CIERRE_AUTOMATICO_MANUAL", "servicio",
                detalle("ignorarPlazo", ignorarPlazo, "revisados", resultado.revisados(),
                        "cerrados", resultado.cerrados(), "omitidosPorReporte", resultado.omitidosPorReporte(),
                        "omitidosPorPlazo", resultado.omitidosPorPlazo(), "fallidos", resultado.fallidos()));
        return resultado;
    }

    /**
     * Inicio automático: pasa a EN_DESARROLLO los servicios CONFIRMADOS cuya hora programada ya llegó y avisa a los
     * dos participantes. Una transacción por servicio. Devuelve cuántos inició.
     */
    public int iniciarAutomaticamente(Instant ahora) {
        int iniciados = 0;
        for (ServicioTutoria candidato : servicios.findByEstadoAndFechaHoraLessThanEqual(EstadoServicio.CONFIRMADO, ahora)) {
            if (!candidato.debeIniciar(ahora)) {
                continue;
            }
            try {
                transaccion.executeWithoutResult(estado -> {
                    ServicioTutoria servicio = servicios.findById(candidato.getId())
                            .orElseThrow(() -> new ExcepcionNoEncontrado("Servicio no encontrado"));
                    servicio.iniciarSesion(ahora);
                    servicios.save(servicio);
                    auditoria.registrarAccion(null, "SERVICIO_INICIADO_AUTOMATICO", "servicio:" + servicio.getId(),
                            detalle("solicitudId", servicio.getSolicitudId()));
                });
                String enlace = "negociacion.html?id=" + candidato.getId();
                avisar(candidato.getEstudianteId(), TipoNotificacion.RECORDATORIO_SESION, "Tu tutoría comenzó",
                        "Llegó la hora programada: el servicio está en desarrollo", enlace);
                avisar(candidato.getTutorId(), TipoNotificacion.RECORDATORIO_SESION, "Tu tutoría comenzó",
                        "Llegó la hora programada: el servicio está en desarrollo", enlace);
                iniciados++;
            } catch (RuntimeException e) {
                log.warn("No se pudo iniciar automáticamente el servicio {}", candidato.getId(), e);
            }
        }
        return iniciados;
    }

    /**
     * Recordatorio único: avisa a ambos participantes cuando faltan menos de 24 h para un servicio CONFIRMADO. Solo
     * marca el recordatorio como enviado si se pudo entregar. Devuelve cuántos servicios recordó.
     */
    public int recordarSesiones(Instant ahora) {
        int recordados = 0;
        for (ServicioTutoria servicio : servicios.findByEstadoAndRecordatorioEnviadoFalseAndFechaHoraBetween(
                EstadoServicio.CONFIRMADO, ahora, ahora.plus(VENTANA_RECORDATORIO))) {
            String enlace = "negociacion.html?id=" + servicio.getId();
            String mensaje = "Tu tutoría empieza el " + servicio.getFechaHora() + " (hora UTC)";
            boolean alEstudiante = notificaciones.recordar(servicio.getEstudianteId(), TipoNotificacion.RECORDATORIO_SESION,
                    "Recordatorio de tu tutoría", mensaje, enlace);
            boolean alTutor = notificaciones.recordar(servicio.getTutorId(), TipoNotificacion.RECORDATORIO_SESION,
                    "Recordatorio de tu tutoría", mensaje, enlace);
            if (alEstudiante && alTutor) {
                servicio.setRecordatorioEnviado(true);
                servicios.save(servicio);
                recordados++;
            }
        }
        return recordados;
    }

    /**
     * Finaliza el servicio y libera el pago. Orden: comprobaciones → FINALIZADO guardado → pago LIBERADO (si el pago
     * falla se restaura el servicio) → reputación del tutor recalculada → auditoría → avisos. {@code actorId} nulo =
     * cierre automático del sistema.
     */
    private Pago ejecutarFinalizacion(ServicioTutoria servicio, String actorId) {
        Instant ahora = Instant.now();
        servicio.finalizar(ahora); // 409 si ya estaba finalizado, en revisión o sin iniciar (todavía solo en memoria)
        try {
            exigirLiquidable(servicio);
        } catch (RuntimeException e) {
            servicio.setEstado(EstadoServicio.EN_DESARROLLO);
            servicio.setFechaFinalizacion(null);
            throw e;
        }
        servicio = servicios.save(servicio);
        Pago pago;
        try {
            pago = pagos.liberarPago(servicio, actorId, ahora);
        } catch (RuntimeException e) {
            restaurarEnDesarrollo(servicio);
            throw e;
        }
        try {
            reputacion.recalcularTutor(servicio.getTutorId());
        } catch (RuntimeException e) {
            log.warn("No se pudo recalcular los servicios completados del tutor {}", servicio.getTutorId(), e);
        }
        boolean automatico = actorId == null;
        auditoria.registrarAccion(actorId, automatico ? "SERVICIO_CERRADO_AUTOMATICAMENTE" : "SERVICIO_FINALIZADO",
                "servicio:" + servicio.getId(),
                detalle("solicitudId", servicio.getSolicitudId(), "comisionCOP", pago.getComisionCOP().toPlainString(),
                        "montoNetoCOP", pago.getMontoNetoCOP().toPlainString()));
        Usuario tutor = usuarios.findById(servicio.getTutorId()).orElse(null);
        Usuario estudiante = usuarios.findById(servicio.getEstudianteId()).orElse(null);
        String enlace = "negociacion.html?id=" + servicio.getId();
        String como = automatico ? "se cerró automáticamente" : "fue finalizada por el estudiante";
        avisar(servicio.getEstudianteId(), TipoNotificacion.SERVICIO_FINALIZADO, "Servicio finalizado",
                "Tu tutoría " + como + ". Tienes 14 días para calificar a " + nombre(tutor), enlace);
        avisar(servicio.getEstudianteId(), TipoNotificacion.CALIFICACION_PENDIENTE, "Califica tu tutoría",
                "Cuéntanos cómo te fue con " + nombre(tutor), enlace);
        avisar(servicio.getTutorId(), TipoNotificacion.SERVICIO_FINALIZADO, "Servicio finalizado",
                "La tutoría con " + nombre(estudiante) + " " + como, enlace);
        avisar(servicio.getTutorId(), TipoNotificacion.PAGO_LIBERADO, "Pago liberado",
                "Se liberaron $" + pago.getMontoNetoCOP().toPlainString() + " netos tras la comisión de TutoFast", enlace);
        avisar(servicio.getTutorId(), TipoNotificacion.CALIFICACION_PENDIENTE, "Califica a tu estudiante",
                "Tienes 14 días para calificar a " + nombre(estudiante), enlace);
        return pago;
    }

    /** Sin reporte abierto y con el pago RETENIDO: solo entonces se puede liquidar. */
    private void exigirLiquidable(ServicioTutoria servicio) {
        if (reportes.existsByServicioIdAndEstadoIn(servicio.getId(), ESTADOS_REPORTE_ABIERTO)) {
            throw new ExcepcionNegocio("REPORTE_ABIERTO",
                    "El servicio tiene un reporte abierto y no puede finalizarse hasta que se resuelva", HttpStatus.CONFLICT);
        }
        Pago actual = pagos.buscarPorServicio(servicio.getId());
        if (actual == null || actual.getEstado() != EstadoPago.RETENIDO) {
            throw new ExcepcionNegocio("PAGO_NO_LIBERABLE",
                    "El pago del servicio no está retenido, por lo que no se puede liquidar", HttpStatus.CONFLICT);
        }
    }

    private void restaurarEnDesarrollo(ServicioTutoria servicio) {
        try {
            servicio.setEstado(EstadoServicio.EN_DESARROLLO);
            servicio.setFechaFinalizacion(null);
            servicios.save(servicio);
        } catch (RuntimeException e) {
            log.warn("No se pudo restaurar el servicio {}", servicio.getId(), e);
        }
    }

    private int cerrarHilosNoElegidos(String solicitudId, String tutorElegidoId) {
        try {
            return mensajeria.cerrarHilos(solicitudId, tutorElegidoId,
                    "El estudiante confirmó el servicio con otro tutor: esta conversación quedó en solo lectura.");
        } catch (RuntimeException e) {
            log.warn("No se pudieron cerrar los hilos de la solicitud {}", solicitudId, e);
            return 0;
        }
    }

    private static String nombre(Usuario u) {
        return u == null ? "la otra parte" : u.getNombre();
    }

    // ---- Internos ----

    private ServicioTutoria reservarServicio(ServicioTutoria servicio) {
        try {
            return servicios.save(servicio);
        } catch (DuplicateKeyException e) {
            throw yaConfirmado();
        }
    }

    private void descartarServicio(ServicioTutoria servicio) {
        try {
            servicios.deleteById(servicio.getId());
        } catch (RuntimeException e) {
            log.warn("No se pudo compensar el servicio {}", servicio.getId(), e);
        }
    }

    private ServicioTutoria obtenerAccesible(UsuarioAutenticado usuario, String servicioId) {
        ServicioTutoria servicio = servicios.findById(servicioId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Servicio no encontrado"));
        if (!servicio.esParticipante(usuario.id()) && !usuario.tieneAlgunRol(Rol.ADMINISTRADOR)) {
            throw new ExcepcionAutorizacion("No tiene acceso a este servicio");
        }
        return servicio;
    }

    private List<ServicioDTO> vistas(List<ServicioTutoria> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        Map<String, Usuario> personas = new HashMap<>();
        Set<String> ids = lista.stream().flatMap(s -> Stream.of(s.getEstudianteId(), s.getTutorId()))
                .collect(Collectors.toSet());
        usuarios.findAllById(ids).forEach(u -> personas.put(u.getId(), u));
        Map<String, SolicitudTutoria> porSolicitud = new HashMap<>();
        solicitudes.findAllById(lista.stream().map(ServicioTutoria::getSolicitudId).collect(Collectors.toSet()))
                .forEach(s -> porSolicitud.put(s.getId(), s));
        return lista.stream().map(s -> ServicioMapper.aDTO(s, porSolicitud.get(s.getSolicitudId()),
                personas.get(s.getEstudianteId()), personas.get(s.getTutorId()), pagos.buscarPorServicio(s.getId()))).toList();
    }

    /** Las notificaciones son secundarias: si fallan, la operación ya hecha no se revierte. */
    private void avisar(String usuarioId, TipoNotificacion tipo, String titulo, String mensaje, String enlace) {
        try {
            notificaciones.notificar(usuarioId, tipo, titulo, mensaje, enlace);
        } catch (RuntimeException e) {
            log.warn("No se pudo notificar al usuario {}", usuarioId, e);
        }
    }

    private static ExcepcionNegocio yaConfirmado() {
        return new ExcepcionNegocio("SERVICIO_YA_CONFIRMADO",
                "La solicitud ya fue confirmada; no se realizó un segundo cobro", HttpStatus.CONFLICT);
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
