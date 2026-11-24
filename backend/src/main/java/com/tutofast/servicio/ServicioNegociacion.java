package com.tutofast.servicio;

import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.transaction.annotation.Transactional;

import com.tutofast.dto.EnlaceServicioDTO;
import com.tutofast.dto.ServicioDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.mapper.ServicioMapper;
import com.tutofast.modelo.EstadoPropuesta;
import com.tutofast.modelo.EstadoServicio;
import com.tutofast.modelo.EstadoSolicitud;
import com.tutofast.modelo.Pago;
import com.tutofast.modelo.Propuesta;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.ServicioTutoria;
import com.tutofast.modelo.SolicitudTutoria;
import com.tutofast.modelo.Usuario;
import com.tutofast.repositorio.RepositorioPropuesta;
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

    private final RepositorioSolicitudTutoria solicitudes;
    private final RepositorioPropuesta propuestas;
    private final RepositorioServicioTutoria servicios;
    private final RepositorioUsuario usuarios;
    private final ServicioPropuestas servicioPropuestas;
    private final ServicioPagos pagos;
    private final ServicioCatalogo catalogo;
    private final ServicioAuditoria auditoria;
    private final ServicioNotificaciones notificaciones;

    public ServicioNegociacion(RepositorioSolicitudTutoria solicitudes, RepositorioPropuesta propuestas,
                               RepositorioServicioTutoria servicios, RepositorioUsuario usuarios,
                               ServicioPropuestas servicioPropuestas, ServicioPagos pagos, ServicioCatalogo catalogo,
                               ServicioAuditoria auditoria, ServicioNotificaciones notificaciones) {
        this.solicitudes = solicitudes;
        this.propuestas = propuestas;
        this.servicios = servicios;
        this.usuarios = usuarios;
        this.servicioPropuestas = servicioPropuestas;
        this.pagos = pagos;
        this.catalogo = catalogo;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
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
        auditoria.registrarAccion(estudianteId, "SERVICIO_CONFIRMADO", "servicio:" + servicio.getId(),
                detalle("solicitudId", solicitudId, "tutorId", servicio.getTutorId(),
                        "precioCOP", servicio.getPrecioFinalCOP().toPlainString(),
                        "comisionPct", comision.toPlainString(), "propuestasDescartadas", descartadas));
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
