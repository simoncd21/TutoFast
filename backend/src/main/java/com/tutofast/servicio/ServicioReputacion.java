package com.tutofast.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tutofast.dto.CalificacionDTO;
import com.tutofast.dto.CalificacionEntradaDTO;
import com.tutofast.dto.CalificacionesServicioDTO;
import com.tutofast.dto.ModeracionEntradaDTO;
import com.tutofast.dto.ParticipanteDTO;
import com.tutofast.dto.ReporteResenaEntradaDTO;
import com.tutofast.dto.ResenaReportadaDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.mapper.CalificacionMapper;
import com.tutofast.mapper.MensajeMapper;
import com.tutofast.modelo.Calificacion;
import com.tutofast.modelo.CalificacionAlEstudiante;
import com.tutofast.modelo.CalificacionAlTutor;
import com.tutofast.modelo.DecisionModeracion;
import com.tutofast.modelo.EstadoServicio;
import com.tutofast.modelo.Moderacion;
import com.tutofast.modelo.PerfilEstudiante;
import com.tutofast.modelo.PerfilTutor;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.ServicioTutoria;
import com.tutofast.modelo.Sancion;
import com.tutofast.modelo.TipoCalificacion;
import com.tutofast.modelo.TipoReporte;
import com.tutofast.modelo.Usuario;
import com.tutofast.repositorio.RepositorioCalificacion;
import com.tutofast.repositorio.RepositorioServicioTutoria;
import com.tutofast.repositorio.RepositorioUsuario;
import com.tutofast.seguridad.UsuarioAutenticado;

/**
 * Reputación bilateral (R13, HU-09): al finalizar un servicio cada parte califica a la otra una sola vez, dentro de
 * los 14 días siguientes. Las calificaciones quedan ocultas hasta que ambas partes califican o vence el plazo; solo
 * las visibles cuentan para el {@code promedioCalificacion} del tutor, el {@code indicadorConfiabilidad} del
 * estudiante y las reseñas públicas. Un destinatario puede reportar una reseña publicada y un administrador decide si
 * la oculta (y, si procede, sanciona al autor).
 *
 * <p>Los contadores del perfil se <em>recalculan</em> desde los datos (no se incrementan): si una operación se
 * interrumpe, la siguiente los deja correctos.
 */
@Service
public class ServicioReputacion {

    private static final Logger log = LoggerFactory.getLogger(ServicioReputacion.class);

    /** Cuántos días antes del cierre del plazo se recuerda a quien no ha calificado. */
    static final Duration AVISO_PREVIO = Duration.ofDays(7);

    private final RepositorioCalificacion calificaciones;
    private final RepositorioServicioTutoria servicios;
    private final RepositorioUsuario usuarios;
    private final ServicioAuditoria auditoria;
    private final ServicioNotificaciones notificaciones;
    private final AplicadorSanciones sanciones;

    public ServicioReputacion(RepositorioCalificacion calificaciones, RepositorioServicioTutoria servicios,
                              RepositorioUsuario usuarios, ServicioAuditoria auditoria,
                              ServicioNotificaciones notificaciones, AplicadorSanciones sanciones) {
        this.calificaciones = calificaciones;
        this.servicios = servicios;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
        this.sanciones = sanciones;
    }

    // ---------------------------------------------------------------- calificar

    /**
     * El estudiante califica al tutor (seis criterios) y el tutor al estudiante (tres), cada uno una vez por servicio.
     * Errores: 403 (no participa o cuenta inactiva), 404, 422 (servicio no finalizado, plazo de 14 días vencido,
     * comentario con contacto externo), 400 (faltan criterios) y 409 {@code DUPLICADO}.
     */
    public CalificacionDTO calificar(UsuarioAutenticado usuario, String servicioId, CalificacionEntradaDTO dto) {
        Usuario autor = usuarios.findById(usuario.id()).orElseThrow(() -> new ExcepcionNoEncontrado("Usuario no encontrado"));
        if (!autor.estaActiva()) {
            throw new ExcepcionAutorizacion("Su cuenta no está activa; no puede calificar");
        }
        ServicioTutoria servicio = servicios.findById(servicioId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Servicio no encontrado"));
        if (!servicio.esParticipante(usuario.id())) {
            throw new ExcepcionAutorizacion("Solo los participantes del servicio pueden calificarlo");
        }
        if (servicio.getEstado() != EstadoServicio.FINALIZADO) {
            throw new ExcepcionNegocio("Las calificaciones y reseñas están restringidas exclusivamente a servicios "
                    + "registrados y finalizados en la plataforma (R13)");
        }
        Instant ahora = Instant.now();
        Instant limite = Calificacion.calcularFechaLimite(servicio.getFechaFinalizacion());
        if (ahora.isAfter(limite)) {
            throw new ExcepcionNegocio("PLAZO_VENCIDO", "El plazo de 14 días para calificar este servicio ya venció",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        boolean esEstudiante = servicio.getEstudianteId().equals(usuario.id());
        TipoCalificacion tipo = esEstudiante ? TipoCalificacion.AL_TUTOR : TipoCalificacion.AL_ESTUDIANTE;
        if (calificaciones.existsByServicioIdAndTipo(servicioId, tipo)) {
            throw duplicada();
        }
        String comentario = dto.comentario() == null || dto.comentario().isBlank() ? null : dto.comentario().strip();
        FiltroContactoExterno.detectar(comentario).ifPresent(motivo -> {
            throw new ExcepcionNegocio("CONTACTO_EXTERNO", "El comentario no puede incluir " + motivo
                    + ": las reseñas son públicas y la plataforma no admite contactos externos",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        });

        Calificacion nueva = esEstudiante ? alTutor(dto) : alEstudiante(dto);
        nueva.calcularPromedio(); // valida el rango de los criterios
        nueva.setServicioId(servicioId);
        nueva.setAutorId(usuario.id());
        nueva.setDestinatarioId(esEstudiante ? servicio.getTutorId() : servicio.getEstudianteId());
        nueva.setComentario(comentario);
        nueva.setFechaEmision(ahora);
        nueva.setFechaLimite(limite);
        try {
            nueva = calificaciones.save(nueva); // el índice uk_calificacion_servicio_tipo cubre la carrera
        } catch (DuplicateKeyException e) {
            throw duplicada();
        }

        TipoCalificacion otro = esEstudiante ? TipoCalificacion.AL_ESTUDIANTE : TipoCalificacion.AL_TUTOR;
        Calificacion contraparte = calificaciones.findByServicioIdAndTipo(servicioId, otro).orElse(null);
        String enlace = "negociacion.html?id=" + servicioId;
        if (contraparte != null) {
            // Las dos partes ya calificaron: se revelan juntas.
            nueva.hacerVisible();
            contraparte.hacerVisible();
            nueva = calificaciones.save(nueva);
            calificaciones.save(contraparte);
            recalcularTodos(servicio);
            avisar(nueva.getDestinatarioId(), TipoNotificacion.RESENA_DISPONIBLE, "Ya puedes ver tu reseña",
                    "Ambas partes calificaron el servicio; tu reseña ya está publicada", enlace);
            avisar(usuario.id(), TipoNotificacion.RESENA_DISPONIBLE, "Ya puedes ver tu reseña",
                    "Ambas partes calificaron el servicio; tu reseña ya está publicada", enlace);
        } else {
            avisar(nueva.getDestinatarioId(), TipoNotificacion.CALIFICACION_PENDIENTE, "Te calificaron",
                    autor.getNombre() + " calificó la tutoría; califica tú para ver la reseña antes de que venza el plazo",
                    enlace);
        }
        auditoria.registrarAccion(usuario.id(), "CALIFICACION_EMITIDA", "calificacion:" + nueva.getId(),
                detalle("servicioId", servicioId, "tipo", tipo.name(), "promedio", nueva.calcularPromedio().toPlainString(),
                        "visible", nueva.isVisible()));
        return CalificacionMapper.aDTO(nueva);
    }

    /** Calificaciones de un servicio vistas por uno de sus participantes (la de la contraparte, solo si ya es visible). */
    public CalificacionesServicioDTO consultar(UsuarioAutenticado usuario, String servicioId) {
        ServicioTutoria servicio = servicios.findById(servicioId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Servicio no encontrado"));
        if (!servicio.esParticipante(usuario.id())) {
            throw new ExcepcionAutorizacion("Solo los participantes del servicio pueden ver sus calificaciones");
        }
        boolean esEstudiante = servicio.getEstudianteId().equals(usuario.id());
        TipoCalificacion mio = esEstudiante ? TipoCalificacion.AL_TUTOR : TipoCalificacion.AL_ESTUDIANTE;
        TipoCalificacion otro = esEstudiante ? TipoCalificacion.AL_ESTUDIANTE : TipoCalificacion.AL_TUTOR;
        Calificacion propia = calificaciones.findByServicioIdAndTipo(servicioId, mio).orElse(null);
        Calificacion ajena = calificaciones.findByServicioIdAndTipo(servicioId, otro).filter(Calificacion::isVisible).orElse(null);
        boolean finalizado = servicio.getEstado() == EstadoServicio.FINALIZADO && servicio.getFechaFinalizacion() != null;
        Instant limite = finalizado ? Calificacion.calcularFechaLimite(servicio.getFechaFinalizacion()) : null;
        boolean puede = finalizado && propia == null && !Instant.now().isAfter(limite);
        return new CalificacionesServicioDTO(propia == null ? null : CalificacionMapper.aDTO(propia),
                ajena == null ? null : CalificacionMapper.aDTO(ajena), limite, puede);
    }

    // ---------------------------------------------------------------- reportes de reseñas

    /** El destinatario reporta una reseña ya publicada sobre él (una sola vez); se avisa a los administradores. */
    public CalificacionDTO reportarResena(UsuarioAutenticado usuario, String calificacionId, ReporteResenaEntradaDTO dto) {
        Calificacion c = calificaciones.findById(calificacionId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Reseña no encontrada"));
        if (!usuario.id().equals(c.getDestinatarioId())) {
            throw new ExcepcionAutorizacion("Solo la persona reseñada puede reportar una reseña");
        }
        c.reportar(dto.motivo(), usuario.id(), Instant.now()); // 409 si no está publicada o ya se reportó
        c = calificaciones.save(c);
        auditoria.registrarAccion(usuario.id(), "REPORTAR_RESENA", "calificacion:" + c.getId(),
                detalle("servicioId", c.getServicioId(), "autorId", c.getAutorId()));
        for (Usuario admin : usuarios.findByRolesContaining(Rol.ADMINISTRADOR)) {
            avisar(admin.getId(), TipoNotificacion.REPORTE_RECIBIDO, "Reseña reportada",
                    "Una reseña fue reportada y espera revisión", "admin.html#resenas");
        }
        return CalificacionMapper.aDTO(c);
    }

    /** Cola de moderación de reseñas reportadas (pendientes). */
    public List<ResenaReportadaDTO> listarResenasReportadas() {
        List<Calificacion> lista = calificaciones.findByReportadaTrueAndModeracionIsNullOrderByFechaReporteDesc();
        Set<String> ids = new LinkedHashSet<>();
        lista.forEach(c -> {
            ids.add(c.getAutorId());
            ids.add(c.getDestinatarioId());
            ids.add(c.getReportadaPorId());
        });
        Map<String, Usuario> personas = new HashMap<>();
        if (!ids.isEmpty()) {
            usuarios.findAllById(ids.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                    .forEach(u -> personas.put(u.getId(), u));
        }
        return lista.stream().map(c -> aResenaReportadaDTO(c, personas)).toList();
    }

    /**
     * Resuelve el reporte de una reseña: {@code DESESTIMAR} la deja publicada; {@code CONFIRMAR_INFRACCION} la oculta
     * de forma definitiva (deja de contar para el promedio) y, si se pide, sanciona al autor.
     */
    @Transactional
    public ResenaReportadaDTO moderarResena(String administradorId, String calificacionId, ModeracionEntradaDTO dto) {
        Calificacion c = calificaciones.findById(calificacionId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Reseña no encontrada"));
        if (dto.sancion() != null && dto.decision() != DecisionModeracion.CONFIRMAR_INFRACCION) {
            throw new ExcepcionNegocio("VALIDACION", "Solo se puede sancionar cuando se confirma la infracción",
                    HttpStatus.BAD_REQUEST);
        }
        Instant ahora = Instant.now();
        String observacion = dto.observacion() == null || dto.observacion().isBlank() ? null : dto.observacion().strip();
        // Todas las comprobaciones antes de cambiar nada: 409 si no está reportada o ya se resolvió; 422 si la
        // sanción no es válida.
        c.exigirModerable();
        if (dto.sancion() != null) {
            sanciones.validar(dto.sancion(), ahora);
        }
        Moderacion moderacion = new Moderacion(dto.decision(), observacion, administradorId, ahora);
        if (dto.sancion() != null) {
            Sancion sancion = sanciones.aplicarPorModeracion(administradorId, c.getReportadaPorId(), c.getAutorId(),
                    TipoReporte.CONDUCTA_ABUSIVA, "Reseña reportada (" + c.getId() + "): " + c.getMotivoReporte(),
                    observacion, dto.sancion(), ahora);
            moderacion.setSancionId(sancion.getId());
        }
        c.moderar(moderacion);
        c = calificaciones.save(c);
        if (c.isOcultaPorModeracion()) {
            servicios.findById(c.getServicioId()).ifPresent(this::recalcularTodos);
        }
        auditoria.registrarAccion(administradorId, "MODERAR_RESENA", "calificacion:" + c.getId(),
                detalle("decision", dto.decision().name(), "autorId", c.getAutorId(),
                        "sancionId", moderacion.getSancionId(), "observacion", observacion));
        avisar(c.getReportadaPorId(), TipoNotificacion.REPORTE_RESUELTO, "Reporte de reseña resuelto",
                dto.decision() == DecisionModeracion.CONFIRMAR_INFRACCION
                        ? "Confirmamos la infracción: la reseña fue ocultada y ya no cuenta en tu reputación"
                        : "Revisamos la reseña que reportaste y se mantiene publicada",
                "perfil.html");
        Map<String, Usuario> personas = new HashMap<>();
        usuarios.findAllById(new LinkedHashSet<>(java.util.Arrays.asList(c.getAutorId(), c.getDestinatarioId(),
                c.getReportadaPorId())))
                .forEach(u -> personas.put(u.getId(), u));
        return aResenaReportadaDTO(c, personas);
    }

    // ---------------------------------------------------------------- recálculo

    /**
     * Recalcula la reputación del tutor desde los datos: {@code promedioCalificacion} (media de sus calificaciones
     * visibles, dos decimales HALF_UP; nulo si no tiene) y {@code serviciosCompletados} (servicios FINALIZADOS).
     */
    public void recalcularTutor(String tutorId) {
        Usuario tutor = usuarios.findById(tutorId).orElse(null);
        if (tutor == null || tutor.getPerfilTutor() == null) {
            return;
        }
        PerfilTutor perfil = tutor.getPerfilTutor();
        perfil.setPromedioCalificacion(promedioVisible(tutorId, TipoCalificacion.AL_TUTOR));
        perfil.setServiciosCompletados((int) servicios.countByTutorIdAndEstado(tutorId, EstadoServicio.FINALIZADO));
        usuarios.save(tutor);
    }

    /** Recalcula el {@code indicadorConfiabilidad} y {@code calificacionesRecibidas} del estudiante. */
    public void recalcularEstudiante(String estudianteId) {
        Usuario estudiante = usuarios.findById(estudianteId).orElse(null);
        if (estudiante == null || estudiante.getPerfilEstudiante() == null) {
            return;
        }
        PerfilEstudiante perfil = estudiante.getPerfilEstudiante();
        List<Calificacion> visibles = visibles(estudianteId, TipoCalificacion.AL_ESTUDIANTE);
        perfil.setIndicadorConfiabilidad(promedio(visibles));
        perfil.setCalificacionesRecibidas(visibles.size());
        usuarios.save(estudiante);
    }

    private void recalcularTodos(ServicioTutoria servicio) {
        recalcularTutor(servicio.getTutorId());
        recalcularEstudiante(servicio.getEstudianteId());
    }

    // ---------------------------------------------------------------- tareas programadas

    /**
     * Publica las calificaciones que siguen ocultas porque la otra parte nunca calificó y ya venció el plazo de
     * 14 días (las ocultadas por moderación no se tocan), recalcula la reputación de los destinatarios y los avisa.
     * Devuelve cuántas publicó; un fallo en una no impide procesar las demás.
     */
    public int cerrarResenas(Instant ahora) {
        List<Calificacion> vencidas = calificaciones
                .findByVisibleFalseAndOcultaPorModeracionFalseAndFechaLimiteLessThanEqual(ahora);
        int publicadas = 0;
        Set<String> tutores = new LinkedHashSet<>();
        Set<String> estudiantes = new LinkedHashSet<>();
        for (Calificacion c : vencidas) {
            try {
                c.hacerVisible();
                calificaciones.save(c);
                (c.getTipo() == TipoCalificacion.AL_TUTOR ? tutores : estudiantes).add(c.getDestinatarioId());
                avisar(c.getDestinatarioId(), TipoNotificacion.RESENA_DISPONIBLE, "Tienes una nueva reseña",
                        "Venció el plazo para calificar y tu reseña ya está publicada", "negociacion.html?id=" + c.getServicioId());
                publicadas++;
            } catch (RuntimeException e) {
                log.warn("No se pudo publicar la calificación {}", c.getId(), e);
            }
        }
        tutores.forEach(this::recalcularSilencioso);
        estudiantes.forEach(id -> {
            try {
                recalcularEstudiante(id);
            } catch (RuntimeException e) {
                log.warn("No se pudo recalcular al estudiante {}", id, e);
            }
        });
        if (publicadas > 0) {
            auditoria.registrarAccion(null, "RESENAS_PUBLICADAS_POR_PLAZO", "calificacion",
                    Map.of("publicadas", publicadas));
        }
        return publicadas;
    }

    /**
     * Recuerda una sola vez, cuando faltan 7 días para que cierre el plazo, a quien todavía no ha calificado un
     * servicio finalizado. Devuelve cuántos servicios procesó.
     */
    public int recordarCalificaciones(Instant ahora) {
        Instant desde = ahora.minus(Calificacion.PLAZO_CALIFICACION);
        Instant hasta = ahora.minus(Calificacion.PLAZO_CALIFICACION.minus(AVISO_PREVIO));
        int procesados = 0;
        for (ServicioTutoria servicio : servicios.findByEstadoAndRecordatorioCalificacionEnviadoFalseAndFechaFinalizacionBetween(
                EstadoServicio.FINALIZADO, desde, hasta)) {
            try {
                String enlace = "negociacion.html?id=" + servicio.getId();
                if (!calificaciones.existsByServicioIdAndTipo(servicio.getId(), TipoCalificacion.AL_TUTOR)) {
                    avisar(servicio.getEstudianteId(), TipoNotificacion.CALIFICACION_PENDIENTE, "Califica tu tutoría",
                            "Te quedan 7 días para calificar a tu tutor", enlace);
                }
                if (!calificaciones.existsByServicioIdAndTipo(servicio.getId(), TipoCalificacion.AL_ESTUDIANTE)) {
                    avisar(servicio.getTutorId(), TipoNotificacion.CALIFICACION_PENDIENTE, "Califica tu tutoría",
                            "Te quedan 7 días para calificar a tu estudiante", enlace);
                }
                servicio.setRecordatorioCalificacionEnviado(true);
                servicios.save(servicio);
                procesados++;
            } catch (RuntimeException e) {
                log.warn("No se pudo recordar la calificación del servicio {}", servicio.getId(), e);
            }
        }
        return procesados;
    }

    // ---------------------------------------------------------------- internos

    private void recalcularSilencioso(String tutorId) {
        try {
            recalcularTutor(tutorId);
        } catch (RuntimeException e) {
            log.warn("No se pudo recalcular al tutor {}", tutorId, e);
        }
    }

    private List<Calificacion> visibles(String destinatarioId, TipoCalificacion tipo) {
        return calificaciones.findByDestinatarioIdAndVisibleTrueOrderByFechaEmisionDesc(destinatarioId).stream()
                .filter(c -> c.getTipo() == tipo).toList();
    }

    private BigDecimal promedioVisible(String destinatarioId, TipoCalificacion tipo) {
        return promedio(visibles(destinatarioId, tipo));
    }

    /** Media de los promedios individuales (todas las calificaciones de un tipo pesan igual); nulo si no hay. */
    static BigDecimal promedio(List<Calificacion> lista) {
        if (lista.isEmpty()) {
            return null;
        }
        BigDecimal suma = lista.stream().map(Calificacion::calcularPromedio).reduce(BigDecimal.ZERO, BigDecimal::add);
        return suma.divide(BigDecimal.valueOf(lista.size()), 2, RoundingMode.HALF_UP);
    }

    private static CalificacionAlTutor alTutor(CalificacionEntradaDTO dto) {
        exigir(dto, "claridadPedagogica", dto.claridadPedagogica(), "dominioTematico", dto.dominioTematico(),
                "puntualidad", dto.puntualidad(), "cumplimiento", dto.cumplimiento(), "trato", dto.trato(),
                "relacionCalidadPrecio", dto.relacionCalidadPrecio());
        CalificacionAlTutor c = new CalificacionAlTutor();
        c.setClaridadPedagogica(dto.claridadPedagogica());
        c.setDominioTematico(dto.dominioTematico());
        c.setPuntualidad(dto.puntualidad());
        c.setCumplimiento(dto.cumplimiento());
        c.setTrato(dto.trato());
        c.setRelacionCalidadPrecio(dto.relacionCalidadPrecio());
        return c;
    }

    private static CalificacionAlEstudiante alEstudiante(CalificacionEntradaDTO dto) {
        exigir(dto, "puntualidad", dto.puntualidad(), "comunicacion", dto.comunicacion(),
                "cumplimiento", dto.cumplimiento());
        CalificacionAlEstudiante c = new CalificacionAlEstudiante();
        c.setPuntualidad(dto.puntualidad());
        c.setComunicacion(dto.comunicacion());
        c.setCumplimiento(dto.cumplimiento());
        return c;
    }

    /** Pares nombre/valor: lanza 400 con la lista de criterios que faltan. */
    private static void exigir(CalificacionEntradaDTO dto, Object... pares) {
        List<String> faltan = new ArrayList<>();
        for (int i = 0; i + 1 < pares.length; i += 2) {
            if (pares[i + 1] == null) {
                faltan.add((String) pares[i]);
            }
        }
        if (!faltan.isEmpty()) {
            throw new ExcepcionNegocio("VALIDACION", "Faltan criterios por calificar: " + String.join(", ", faltan),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private ResenaReportadaDTO aResenaReportadaDTO(Calificacion c, Map<String, Usuario> personas) {
        return new ResenaReportadaDTO(CalificacionMapper.aDTO(c), participante(c.getAutorId(), personas),
                participante(c.getDestinatarioId(), personas), c.getMotivoReporte(),
                c.getReportadaPorId() == null ? null : participante(c.getReportadaPorId(), personas),
                c.getFechaReporte(), MensajeMapper.aModeracionDTO(c.getModeracion()));
    }

    private static ParticipanteDTO participante(String id, Map<String, Usuario> personas) {
        Usuario u = personas.get(id);
        return new ParticipanteDTO(id, u == null ? null : u.getNombre());
    }

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

    private static ExcepcionNegocio duplicada() {
        return new ExcepcionNegocio("DUPLICADO", "Ya calificaste este servicio", HttpStatus.CONFLICT);
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
