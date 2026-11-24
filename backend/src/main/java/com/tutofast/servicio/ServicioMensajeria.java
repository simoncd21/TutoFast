package com.tutofast.servicio;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.tutofast.dto.ArchivoDTO;
import com.tutofast.dto.HiloMensajesDTO;
import com.tutofast.dto.HiloResumenDTO;
import com.tutofast.dto.MensajeDTO;
import com.tutofast.dto.MensajeEntradaDTO;
import com.tutofast.dto.MensajeReportadoDTO;
import com.tutofast.dto.ModeracionEntradaDTO;
import com.tutofast.dto.ParticipanteDTO;
import com.tutofast.dto.ReporteMensajeEntradaDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.mapper.MensajeMapper;
import com.tutofast.modelo.Archivo;
import com.tutofast.modelo.DecisionModeracion;
import com.tutofast.modelo.EstadoServicio;
import com.tutofast.modelo.EstadoSolicitud;
import com.tutofast.modelo.Mensaje;
import com.tutofast.modelo.Moderacion;
import com.tutofast.modelo.Propuesta;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.Sancion;
import com.tutofast.modelo.SolicitudTutoria;
import com.tutofast.modelo.Usuario;
import com.tutofast.repositorio.RepositorioMensaje;
import com.tutofast.repositorio.RepositorioPropuesta;
import com.tutofast.repositorio.RepositorioServicioTutoria;
import com.tutofast.repositorio.RepositorioSolicitudTutoria;
import com.tutofast.repositorio.RepositorioUsuario;
import com.tutofast.seguridad.UsuarioAutenticado;

/**
 * Chat entre el estudiante dueño de una solicitud y cada tutor que propuso sobre ella (R15, HU-10). Un hilo es el par
 * {@code (solicitudId, tutorId)}: ningún tutor ve la conversación de otro. Los mensajes son inmutables.
 *
 * <p>Reglas de escritura: el hilo solo admite mensajes mientras la solicitud no esté cancelada, el tutor conserve una
 * propuesta activa (una descartada o retirada lo cierra, y la confirmación del servicio cierra a los no elegidos) y el
 * servicio, si ya existe, no haya concluido. Ningún mensaje puede compartir contactos externos ni proponer pagos fuera
 * de la plataforma ({@link FiltroContactoExterno}). El administrador solo lee y modera.
 */
@Service
public class ServicioMensajeria {

    private static final Logger log = LoggerFactory.getLogger(ServicioMensajeria.class);

    /** Máximo de mensajes por respuesta (carga inicial y polling). */
    public static final int MAXIMO_POR_RESPUESTA = 100;
    public static final int MAXIMO_CARACTERES = 2000;
    /** Para no saturar la campana: dentro de este margen, mensajes seguidos del mismo emisor no vuelven a avisar. */
    static final Duration MARGEN_ENTRE_AVISOS = Duration.ofMinutes(10);

    private final RepositorioMensaje mensajes;
    private final RepositorioSolicitudTutoria solicitudes;
    private final RepositorioPropuesta propuestas;
    private final RepositorioServicioTutoria servicios;
    private final RepositorioUsuario usuarios;
    private final ServicioArchivos archivos;
    private final ServicioAuditoria auditoria;
    private final ServicioNotificaciones notificaciones;
    private final AplicadorSanciones sanciones;

    public ServicioMensajeria(RepositorioMensaje mensajes, RepositorioSolicitudTutoria solicitudes,
                              RepositorioPropuesta propuestas, RepositorioServicioTutoria servicios,
                              RepositorioUsuario usuarios, ServicioArchivos archivos, ServicioAuditoria auditoria,
                              ServicioNotificaciones notificaciones, AplicadorSanciones sanciones) {
        this.mensajes = mensajes;
        this.solicitudes = solicitudes;
        this.propuestas = propuestas;
        this.servicios = servicios;
        this.usuarios = usuarios;
        this.archivos = archivos;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
        this.sanciones = sanciones;
    }

    // ---------------------------------------------------------------- envío

    /**
     * Envía un mensaje al hilo. El estudiante dueño indica el {@code tutorId}; un tutor escribe en su propio hilo (y
     * necesita una propuesta en la solicitud). Errores: 400 (vacío, demasiado largo o sin tutor), 403 (tercero,
     * administrador o hilo de solo lectura), 404, 413/415 (adjunto) y 422 {@code CONTACTO_EXTERNO}.
     */
    public MensajeDTO enviarMensaje(UsuarioAutenticado usuario, String solicitudId, MensajeEntradaDTO dto,
                                    MultipartFile adjunto) {
        Usuario emisor = exigirCuentaActiva(usuario.id());
        SolicitudTutoria solicitud = obtenerSolicitud(solicitudId);
        Hilo hilo = resolverHilo(usuario, solicitud, dto == null ? null : dto.tutorId(), true);
        if (hilo.estado().soloLectura()) {
            throw new ExcepcionAutorizacion("Este hilo es de solo lectura: " + hilo.estado().motivo());
        }

        String contenido = dto == null || dto.contenido() == null ? "" : dto.contenido().strip();
        boolean conAdjunto = adjunto != null && !adjunto.isEmpty();
        if (contenido.isEmpty() && !conAdjunto) {
            throw datosInvalidos("Escriba un mensaje o adjunte un archivo");
        }
        if (contenido.length() > MAXIMO_CARACTERES) {
            throw datosInvalidos("El mensaje admite hasta " + MAXIMO_CARACTERES + " caracteres");
        }
        Optional<String> infraccion = FiltroContactoExterno.detectar(contenido);
        if (infraccion.isPresent()) {
            registrarBloqueo(emisor, solicitud, hilo.tutorId(), infraccion.get());
            throw new ExcepcionNegocio("CONTACTO_EXTERNO",
                    "No se permite compartir " + infraccion.get() + " ni acordar pagos fuera de TutoFast; "
                            + "mantén la conversación y el pago dentro de la plataforma", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // Orden: validar → subir a GridFS → persistir (si persistir falla, se borra el archivo subido).
        ArchivoDTO subido = conAdjunto ? archivos.guardar(adjunto) : null;
        Instant ahora = Instant.now();
        Mensaje ultimo = ultimoMensaje(solicitudId, hilo.tutorId());
        Mensaje mensaje;
        try {
            Archivo embebido = subido == null ? null
                    : new Archivo(subido.nombre(), subido.tipo(), subido.tamanoMB(), subido.archivoId());
            mensaje = mensajes.save(Mensaje.nuevo(solicitudId, hilo.tutorId(), usuario.id(),
                    contenido.isEmpty() ? null : contenido, embebido, ahora));
        } catch (RuntimeException e) {
            if (subido != null) {
                eliminarArchivoSilencioso(subido.archivoId());
            }
            throw e;
        }

        String receptorId = usuario.id().equals(solicitud.getEstudianteId()) ? hilo.tutorId() : solicitud.getEstudianteId();
        if (debeAvisar(ultimo, usuario.id(), ahora)) {
            avisar(receptorId, TipoNotificacion.NUEVO_MENSAJE, "Nuevo mensaje",
                    emisor.getNombre() + " te escribió sobre «" + solicitud.getTema() + "»",
                    "solicitudes.html?id=" + solicitudId + "&tutorId=" + hilo.tutorId());
        }
        return MensajeMapper.aDTO(mensaje, emisor.getNombre(), false);
    }

    // ---------------------------------------------------------------- consulta

    /**
     * Mensajes del hilo en orden ascendente. Sin {@code desde} devuelve los más recientes (hasta 100); con {@code desde}
     * (exclusivo) devuelve solo los posteriores, que es lo que pide el polling cada 4 s. Los mensajes de la otra
     * parte quedan marcados como leídos para el lector (el administrador no marca nada).
     */
    public HiloMensajesDTO consultarHistorial(UsuarioAutenticado usuario, String solicitudId, String tutorId,
                                              Instant desde) {
        SolicitudTutoria solicitud = obtenerSolicitud(solicitudId);
        Hilo hilo = resolverHilo(usuario, solicitud, tutorId, false);
        PageRequest tope = PageRequest.of(0, MAXIMO_POR_RESPUESTA);
        List<Mensaje> lista;
        if (desde == null) {
            lista = new ArrayList<>(mensajes.findBySolicitudIdAndTutorIdOrderByMarcaTemporalDesc(
                    solicitudId, hilo.tutorId(), tope));
            Collections.reverse(lista);
        } else {
            lista = mensajes.findBySolicitudIdAndTutorIdAndMarcaTemporalAfterOrderByMarcaTemporalAsc(
                    solicitudId, hilo.tutorId(), desde, tope);
        }
        boolean administrador = esSoloAdministrador(usuario, solicitud, hilo.tutorId());
        if (!administrador) {
            marcarLeidos(lista, usuario.id());
        }
        boolean soloLectura = administrador || hilo.estado().soloLectura();
        String motivo = administrador ? "El administrador solo puede consultar la conversación" : hilo.estado().motivo();
        return new HiloMensajesDTO(soloLectura, soloLectura ? motivo : null, aDTOs(lista, administrador));
    }

    /** Hilos de una solicitud (uno por tutor con propuesta) para su dueño o un administrador. */
    public List<HiloResumenDTO> listarHilos(UsuarioAutenticado usuario, String solicitudId) {
        SolicitudTutoria solicitud = obtenerSolicitud(solicitudId);
        boolean administrador = usuario.tieneAlgunRol(Rol.ADMINISTRADOR) && !solicitud.getEstudianteId().equals(usuario.id());
        if (!administrador && !solicitud.getEstudianteId().equals(usuario.id())) {
            throw new ExcepcionAutorizacion("Solo el dueño de la solicitud puede ver sus conversaciones");
        }
        Map<String, List<Propuesta>> porTutor = new LinkedHashMap<>();
        for (Propuesta p : propuestas.findBySolicitudIdOrderByFechaCreacionDesc(solicitudId)) {
            porTutor.computeIfAbsent(p.getTutorId(), k -> new ArrayList<>()).add(p);
        }
        Map<String, Usuario> tutores = cargar(porTutor.keySet());
        List<HiloResumenDTO> resumen = new ArrayList<>();
        for (Map.Entry<String, List<Propuesta>> e : porTutor.entrySet()) {
            String tutorId = e.getKey();
            Mensaje ultimo = ultimoMensaje(solicitudId, tutorId);
            long noLeidos = administrador ? 0
                    : mensajes.countBySolicitudIdAndTutorIdAndEmisorIdNotAndLeidoFalse(solicitudId, tutorId, usuario.id());
            Usuario tutor = tutores.get(tutorId);
            resumen.add(new HiloResumenDTO(tutorId, tutor == null ? null : tutor.getNombre(),
                    ultimo == null ? null : vistaPrevia(ultimo, administrador),
                    ultimo == null ? null : ultimo.getMarcaTemporal(), noLeidos,
                    administrador || evaluarHilo(solicitud, e.getValue()).soloLectura()));
        }
        resumen.sort(Comparator.comparing(HiloResumenDTO::ultimaActividad,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return resumen;
    }

    // ---------------------------------------------------------------- reportes

    /**
     * El receptor de un mensaje lo reporta (REPORTAR_MENSAJE): queda marcado, el contenido no cambia y se avisa a los
     * administradores. No se pueden reportar los mensajes propios ni los avisos del sistema; un segundo reporte da 409.
     */
    public MensajeDTO reportarMensaje(UsuarioAutenticado usuario, String mensajeId, ReporteMensajeEntradaDTO dto) {
        Usuario reportante = exigirCuentaActiva(usuario.id());
        Mensaje mensaje = mensajes.findById(mensajeId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Mensaje no encontrado"));
        SolicitudTutoria solicitud = obtenerSolicitud(mensaje.getSolicitudId());
        if (!esParticipante(usuario, solicitud, mensaje.getTutorId())) {
            throw new ExcepcionAutorizacion("Solo los participantes del hilo pueden reportar sus mensajes");
        }
        if (mensaje.esDeSistema()) {
            throw new ExcepcionNegocio("Los avisos de la plataforma no se pueden reportar");
        }
        if (usuario.id().equals(mensaje.getEmisorId())) {
            throw new ExcepcionAutorizacion("No puedes reportar tus propios mensajes");
        }
        Instant ahora = Instant.now();
        mensaje.reportar(dto.motivo().name(), dto.detalle(), usuario.id(), ahora); // 409 si ya estaba reportado
        mensaje = mensajes.save(mensaje);
        auditoria.registrarAccion(usuario.id(), "REPORTAR_MENSAJE", "mensaje:" + mensaje.getId(),
                detalle("solicitudId", mensaje.getSolicitudId(), "tutorId", mensaje.getTutorId(),
                        "emisorId", mensaje.getEmisorId(), "motivo", dto.motivo().name()));
        for (Usuario admin : usuarios.findByRolesContaining(Rol.ADMINISTRADOR)) {
            avisar(admin.getId(), TipoNotificacion.REPORTE_RECIBIDO, "Mensaje reportado",
                    reportante.getNombre() + " reportó un mensaje por " + dto.motivo().name().toLowerCase().replace('_', ' '),
                    "admin.html#mensajes");
        }
        Usuario emisor = usuarios.findById(mensaje.getEmisorId()).orElse(null);
        return MensajeMapper.aDTO(mensaje, emisor == null ? null : emisor.getNombre(), false);
    }

    // ---------------------------------------------------------------- moderación (administrador)

    /** Cola de moderación: mensajes reportados pendientes; con {@code incluirModerados} también los ya resueltos. */
    public List<MensajeReportadoDTO> listarReportados(boolean incluirModerados) {
        List<Mensaje> lista = incluirModerados
                ? mensajes.findByReportadoTrueOrderByMarcaTemporalDesc()
                : mensajes.findByReportadoTrueAndModeracionIsNullOrderByMarcaTemporalDesc();
        Set<String> ids = new LinkedHashSet<>();
        lista.forEach(m -> {
            ids.add(m.getEmisorId());
            ids.add(m.getTutorId());
            ids.add(m.getReportadoPorId());
        });
        Map<String, Usuario> personas = cargar(ids);
        return lista.stream().map(m -> aReportadoDTO(m, personas)).toList();
    }

    /**
     * Resuelve el reporte de un mensaje. {@code DESESTIMAR} lo deja como estaba; {@code CONFIRMAR_INFRACCION} retira el
     * contenido de la vista de los participantes y, si el administrador lo pide, sanciona a quien lo escribió (se
     * radica un reporte resuelto que respalda la sanción y permite apelarla). Un reporte se resuelve una sola vez (409).
     */
    @Transactional
    public MensajeReportadoDTO moderarMensaje(String administradorId, String mensajeId, ModeracionEntradaDTO dto) {
        Mensaje mensaje = mensajes.findById(mensajeId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Mensaje no encontrado"));
        if (dto.sancion() != null && dto.decision() != DecisionModeracion.CONFIRMAR_INFRACCION) {
            throw datosInvalidos("Solo se puede sancionar cuando se confirma la infracción");
        }
        Instant ahora = Instant.now();
        String observacion = limpiar(dto.observacion());
        // Todas las comprobaciones antes de cambiar nada: 409 si no está reportado o ya se resolvió; 422 si la
        // sanción no es válida.
        mensaje.exigirModerable();
        if (dto.sancion() != null) {
            sanciones.validar(dto.sancion(), ahora);
        }
        Moderacion moderacion = new Moderacion(dto.decision(), observacion, administradorId, ahora);
        if (dto.sancion() != null) {
            Sancion sancion = sanciones.aplicarPorModeracion(administradorId, mensaje.getReportadoPorId(),
                    mensaje.getEmisorId(), tipoDeReporte(mensaje), "Mensaje reportado (" + mensaje.getId() + "): "
                            + mensaje.getMotivoReporte(), observacion, dto.sancion(), ahora);
            moderacion.setSancionId(sancion.getId());
        }
        mensaje.moderar(moderacion);
        mensaje = mensajes.save(mensaje);
        auditoria.registrarAccion(administradorId, "MODERAR_MENSAJE", "mensaje:" + mensaje.getId(),
                detalle("decision", dto.decision().name(), "emisorId", mensaje.getEmisorId(),
                        "sancionId", moderacion.getSancionId(), "observacion", observacion));
        String tema = solicitudes.findById(mensaje.getSolicitudId()).map(SolicitudTutoria::getTema)
                .orElse("tu solicitud");
        avisar(mensaje.getReportadoPorId(), TipoNotificacion.REPORTE_RESUELTO, "Reporte de mensaje resuelto",
                dto.decision() == DecisionModeracion.CONFIRMAR_INFRACCION
                        ? "Confirmamos la infracción del mensaje que reportaste en «" + tema + "»"
                        : "Revisamos el mensaje que reportaste en «" + tema + "» y no encontramos una infracción",
                "solicitudes.html?id=" + mensaje.getSolicitudId());
        if (dto.decision() == DecisionModeracion.CONFIRMAR_INFRACCION && moderacion.getSancionId() == null) {
            avisar(mensaje.getEmisorId(), TipoNotificacion.REPORTE_RECIBIDO, "Mensaje retirado",
                    "Un mensaje tuyo en «" + tema + "» fue retirado por incumplir las normas de TutoFast",
                    "solicitudes.html?id=" + mensaje.getSolicitudId());
        }
        Map<String, Usuario> personas = cargar(new LinkedHashSet<>(java.util.Arrays.asList(mensaje.getEmisorId(), mensaje.getTutorId(),
                mensaje.getReportadoPorId())));
        return aReportadoDTO(mensaje, personas);
    }

    // ---------------------------------------------------------------- cierre de hilos

    /**
     * Cierra los hilos de los tutores no elegidos al confirmar el servicio: deja un aviso del sistema en cada hilo
     * que tuvo conversación (el bloqueo de escritura ya se deduce de que su propuesta quedó descartada). Devuelve
     * cuántos hilos cerró. Los tutores sin mensajes no necesitan aviso.
     */
    public int cerrarHilos(String solicitudId, String tutorElegidoId, String motivo) {
        Instant ahora = Instant.now();
        Set<String> tutores = propuestas.findBySolicitudIdOrderByFechaCreacionDesc(solicitudId).stream()
                .map(Propuesta::getTutorId).filter(t -> !t.equals(tutorElegidoId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        int cerrados = 0;
        for (String tutorId : tutores) {
            if (ultimoMensaje(solicitudId, tutorId) == null) {
                continue;
            }
            mensajes.save(Mensaje.deSistema(solicitudId, tutorId, motivo, ahora));
            auditoria.registrarAccion(null, "HILO_CERRADO", "solicitud:" + solicitudId,
                    detalle("tutorId", tutorId, "motivo", motivo));
            cerrados++;
        }
        return cerrados;
    }

    // ---------------------------------------------------------------- reglas del hilo

    /** Estado de escritura de un hilo y la causa legible cuando es de solo lectura. */
    record EstadoHilo(boolean soloLectura, String motivo) {
        static EstadoHilo abierto() {
            return new EstadoHilo(false, null);
        }

        static EstadoHilo lectura(String motivo) {
            return new EstadoHilo(true, motivo);
        }
    }

    private record Hilo(String tutorId, EstadoHilo estado) {
    }

    EstadoHilo evaluarHilo(SolicitudTutoria solicitud, List<Propuesta> delTutor) {
        if (solicitud.getEstado() == EstadoSolicitud.CANCELADA) {
            return EstadoHilo.lectura("la solicitud fue cancelada");
        }
        if (delTutor.stream().noneMatch(Propuesta::isActiva)) {
            return EstadoHilo.lectura("la propuesta de este tutor fue descartada o retirada");
        }
        if (solicitud.getEstado() == EstadoSolicitud.FORMALIZADA) {
            boolean concluido = servicios.findBySolicitudId(solicitud.getId())
                    .map(s -> s.getEstado() == EstadoServicio.FINALIZADO || s.getEstado() == EstadoServicio.CANCELADO)
                    .orElse(false);
            if (concluido) {
                return EstadoHilo.lectura("el servicio ya concluyó");
            }
        }
        return EstadoHilo.abierto();
    }

    /**
     * Identifica el hilo al que accede el usuario. El estudiante dueño y el administrador deben indicar el tutor; un
     * tutor siempre usa el suyo. {@code escritura} excluye al administrador. 403 para cualquier tercero; 404 si el
     * estudiante apunta a un tutor que no tiene hilo en la solicitud.
     */
    private Hilo resolverHilo(UsuarioAutenticado usuario, SolicitudTutoria solicitud, String tutorIdSolicitado,
                              boolean escritura) {
        boolean dueno = solicitud.getEstudianteId().equals(usuario.id());
        boolean administrador = !dueno && usuario.tieneAlgunRol(Rol.ADMINISTRADOR);
        String tutorId;
        if (dueno || administrador) {
            if (administrador && escritura) {
                throw new ExcepcionAutorizacion("El administrador solo puede consultar las conversaciones");
            }
            if (tutorIdSolicitado == null || tutorIdSolicitado.isBlank()) {
                throw datosInvalidos("Indique el tutor del hilo (parámetro tutorId)");
            }
            tutorId = tutorIdSolicitado;
        } else if (usuario.tieneAlgunRol(Rol.TUTOR)) {
            if (tutorIdSolicitado != null && !tutorIdSolicitado.isBlank() && !tutorIdSolicitado.equals(usuario.id())) {
                throw new ExcepcionAutorizacion("No tiene acceso a la conversación de otro tutor");
            }
            tutorId = usuario.id();
        } else {
            throw new ExcepcionAutorizacion("No tiene acceso a esta conversación");
        }
        List<Propuesta> delTutor = propuestas.findBySolicitudIdAndTutorIdOrderByFechaCreacionDesc(solicitud.getId(), tutorId);
        if (delTutor.isEmpty()) {
            if (dueno || administrador) {
                throw new ExcepcionNoEncontrado("Ese tutor no tiene una conversación en esta solicitud");
            }
            throw new ExcepcionAutorizacion("Debe tener una propuesta en la solicitud para conversar con el estudiante");
        }
        return new Hilo(tutorId, evaluarHilo(solicitud, delTutor));
    }

    private boolean esParticipante(UsuarioAutenticado usuario, SolicitudTutoria solicitud, String tutorId) {
        return usuario.id().equals(solicitud.getEstudianteId()) || usuario.id().equals(tutorId);
    }

    private boolean esSoloAdministrador(UsuarioAutenticado usuario, SolicitudTutoria solicitud, String tutorId) {
        return !esParticipante(usuario, solicitud, tutorId) && usuario.tieneAlgunRol(Rol.ADMINISTRADOR);
    }

    // ---------------------------------------------------------------- internos

    private Usuario exigirCuentaActiva(String usuarioId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> new ExcepcionNoEncontrado("Usuario no encontrado"));
        if (!u.estaActiva()) {
            throw new ExcepcionAutorizacion("Su cuenta no está activa; no puede usar la mensajería");
        }
        return u;
    }

    private SolicitudTutoria obtenerSolicitud(String id) {
        return solicitudes.findById(id).orElseThrow(() -> new ExcepcionNoEncontrado("Solicitud no encontrada"));
    }

    private Mensaje ultimoMensaje(String solicitudId, String tutorId) {
        List<Mensaje> ultimos = mensajes.findBySolicitudIdAndTutorIdOrderByMarcaTemporalDesc(
                solicitudId, tutorId, PageRequest.of(0, 1));
        return ultimos == null || ultimos.isEmpty() ? null : ultimos.get(0);
    }

    private static boolean debeAvisar(Mensaje anterior, String emisorId, Instant ahora) {
        return anterior == null || !emisorId.equals(anterior.getEmisorId())
                || anterior.getMarcaTemporal().plus(MARGEN_ENTRE_AVISOS).isBefore(ahora);
    }

    private void marcarLeidos(List<Mensaje> lista, String lectorId) {
        List<Mensaje> pendientes = lista.stream()
                .filter(m -> !m.isLeido() && !lectorId.equals(m.getEmisorId())).toList();
        if (pendientes.isEmpty()) {
            return;
        }
        pendientes.forEach(m -> m.setLeido(true));
        mensajes.saveAll(pendientes);
    }

    private List<MensajeDTO> aDTOs(List<Mensaje> lista, boolean verOriginal) {
        Map<String, Usuario> personas = cargar(lista.stream().map(Mensaje::getEmisorId).collect(Collectors.toSet()));
        return lista.stream().map(m -> {
            Usuario u = m.getEmisorId() == null ? null : personas.get(m.getEmisorId());
            return MensajeMapper.aDTO(m, u == null ? null : u.getNombre(), verOriginal);
        }).toList();
    }

    private MensajeReportadoDTO aReportadoDTO(Mensaje m, Map<String, Usuario> personas) {
        Usuario emisor = personas.get(m.getEmisorId());
        Usuario tutor = personas.get(m.getTutorId());
        Usuario reportante = m.getReportadoPorId() == null ? null : personas.get(m.getReportadoPorId());
        return new MensajeReportadoDTO(MensajeMapper.aDTO(m, emisor == null ? null : emisor.getNombre(), true),
                m.getSolicitudId(), m.getTutorId(), tutor == null ? null : tutor.getNombre(), m.getMotivoReporte(),
                m.getDetalleReporte(),
                m.getReportadoPorId() == null ? null
                        : new ParticipanteDTO(m.getReportadoPorId(), reportante == null ? null : reportante.getNombre()),
                m.getFechaReporte(), MensajeMapper.aModeracionDTO(m.getModeracion()));
    }

    private Map<String, Usuario> cargar(Set<String> ids) {
        Set<String> validos = ids.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<String, Usuario> mapa = new HashMap<>();
        if (!validos.isEmpty()) {
            usuarios.findAllById(validos).forEach(u -> mapa.put(u.getId(), u));
        }
        return mapa;
    }

    private static String vistaPrevia(Mensaje m, boolean verOriginal) {
        if (m.estaRetirado() && !verOriginal) {
            return MensajeMapper.TEXTO_RETIRADO;
        }
        String texto = m.getContenido();
        if (texto == null || texto.isBlank()) {
            return "[Archivo adjunto]";
        }
        return texto.length() > 120 ? texto.substring(0, 117) + "…" : texto;
    }

    private static com.tutofast.modelo.TipoReporte tipoDeReporte(Mensaje m) {
        try {
            return com.tutofast.modelo.MotivoReporteMensaje.valueOf(m.getMotivoReporte()).tipoReporte();
        } catch (IllegalArgumentException | NullPointerException e) {
            return com.tutofast.modelo.TipoReporte.CONDUCTA_ABUSIVA;
        }
    }

    private void registrarBloqueo(Usuario emisor, SolicitudTutoria solicitud, String tutorId, String motivo) {
        try {
            auditoria.registrarAccion(emisor.getId(), "MENSAJE_BLOQUEADO", "solicitud:" + solicitud.getId(),
                    detalle("tutorId", tutorId, "motivo", motivo));
        } catch (RuntimeException e) {
            log.warn("No se pudo auditar el mensaje bloqueado de {}", emisor.getId(), e);
        }
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
