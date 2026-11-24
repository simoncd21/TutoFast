package com.tutofast.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.tutofast.dto.PropuestaEntradaDTO;
import com.tutofast.dto.PropuestaVistaDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.mapper.PropuestaMapper;
import com.tutofast.modelo.Duracion;
import com.tutofast.modelo.EstadoPropuesta;
import com.tutofast.modelo.EstadoSolicitud;
import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.Propuesta;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.SolicitudTutoria;
import com.tutofast.modelo.TipoPropuesta;
import com.tutofast.modelo.Usuario;
import com.tutofast.repositorio.RepositorioPropuesta;
import com.tutofast.repositorio.RepositorioSolicitudTutoria;
import com.tutofast.repositorio.RepositorioUsuario;
import com.tutofast.seguridad.UsuarioAutenticado;

/**
 * Propuestas de los tutores y negociación del precio con el estudiante (R08–R10). Una sola propuesta activa por
 * tutor y solicitud: lo garantiza el índice parcial único {@code uk_propuesta_activa} y, antes de llegar a él, una
 * comprobación explícita.
 */
@Service
public class ServicioPropuestas {

    private static final Logger log = LoggerFactory.getLogger(ServicioPropuestas.class);

    /** Tope de cordura para cualquier precio de propuesta o contraoferta (COP). */
    public static final BigDecimal PRECIO_MAXIMO_COP = new BigDecimal("5000000");

    private final RepositorioPropuesta propuestas;
    private final RepositorioSolicitudTutoria solicitudes;
    private final RepositorioUsuario usuarios;
    private final ServicioAuditoria auditoria;
    private final ServicioNotificaciones notificaciones;

    public ServicioPropuestas(RepositorioPropuesta propuestas, RepositorioSolicitudTutoria solicitudes,
                              RepositorioUsuario usuarios, ServicioAuditoria auditoria,
                              ServicioNotificaciones notificaciones) {
        this.propuestas = propuestas;
        this.solicitudes = solicitudes;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
    }

    // ---- Tutor ----

    /**
     * El tutor propone sobre una solicitud abierta. La primera propuesta lleva la solicitud a PROPUESTAS_RECIBIDAS;
     * una contrapropuesta o alternativa, a NEGOCIACION_EN_CURSO.
     */
    public PropuestaVistaDTO enviarPropuesta(String tutorId, String solicitudId, PropuestaEntradaDTO dto) {
        Usuario tutor = exigirTutorActivo(tutorId);
        SolicitudTutoria solicitud = obtenerSolicitud(solicitudId);
        if (solicitud.getEstudianteId().equals(tutorId)) {
            throw new ExcepcionAutorizacion("No puede enviar propuestas a su propia solicitud");
        }
        exigirSolicitudAbierta(solicitud);
        Condiciones c = condiciones(dto, solicitud);
        if (propuestas.existsBySolicitudIdAndTutorIdAndActivaTrue(solicitudId, tutorId)) {
            throw propuestaDuplicada();
        }
        Instant ahora = Instant.now();
        Propuesta propuesta = Propuesta.nueva(solicitudId, tutorId, dto.tipo(), c.precio(), c.duracion(),
                c.modalidad(), limpiar(dto.nota()), ahora);
        try {
            propuesta = propuestas.save(propuesta); // el índice uk_propuesta_activa cubre la carrera entre dos envíos
        } catch (DuplicateKeyException e) {
            throw propuestaDuplicada();
        }
        solicitud.registrarPropuesta();
        if (dto.tipo() != TipoPropuesta.ACEPTA_PRECIO) {
            solicitud.iniciarNegociacion();
        }
        guardarSolicitudOCompensar(solicitud, propuesta, ahora);
        auditoria.registrarAccion(tutorId, "PROPUESTA_ENVIADA", entidad(propuesta),
                detalle("solicitudId", solicitudId, "tipo", dto.tipo(), "precioCOP", texto(c.precio())));
        avisar(solicitud.getEstudianteId(), TipoNotificacion.NUEVA_PROPUESTA, "Nueva propuesta",
                tutor.getNombre() + " propone " + pesos(c.precio()) + " para «" + solicitud.getTema() + "»",
                solicitud);
        return PropuestaMapper.aVistaDTO(propuesta, tutor, solicitud);
    }

    /** El tutor edita su propuesta (también es su respuesta a una contraoferta): vuelve a ENVIADA. */
    public PropuestaVistaDTO modificarPropuesta(String tutorId, String propuestaId, PropuestaEntradaDTO dto) {
        Usuario tutor = exigirTutorActivo(tutorId);
        Propuesta propuesta = obtenerPropuestaDelTutor(tutorId, propuestaId);
        SolicitudTutoria solicitud = obtenerSolicitud(propuesta.getSolicitudId());
        exigirSolicitudAbierta(solicitud);
        Condiciones c = condiciones(dto, solicitud);
        BigDecimal anterior = propuesta.getPrecioCOP();
        boolean respondeContraoferta = propuesta.getEstado() == EstadoPropuesta.CONTRAOFERTA_ESTUDIANTE;
        Instant ahora = Instant.now();
        propuesta.modificar(dto.tipo(), c.precio(), c.duracion(), c.modalidad(), limpiar(dto.nota()), ahora);
        if (dto.tipo() != TipoPropuesta.ACEPTA_PRECIO) {
            solicitud.iniciarNegociacion();
            solicitud.setFechaActualizacion(ahora);
            solicitudes.save(solicitud);
        }
        propuesta = propuestas.save(propuesta);
        auditoria.registrarAccion(tutorId, "PROPUESTA_MODIFICADA", entidad(propuesta),
                detalle("solicitudId", solicitud.getId(), "precioAnteriorCOP", texto(anterior),
                        "precioNuevoCOP", texto(c.precio())));
        avisar(solicitud.getEstudianteId(), TipoNotificacion.NUEVA_PROPUESTA,
                respondeContraoferta ? "El tutor respondió a tu contraoferta" : "Propuesta actualizada",
                tutor.getNombre() + " ahora propone " + pesos(c.precio()) + " para «" + solicitud.getTema() + "»",
                solicitud);
        return PropuestaMapper.aVistaDTO(propuesta, tutor, solicitud);
    }

    /** El tutor retira su propuesta (RETIRADA, deja de estar activa y libera el índice único). */
    public PropuestaVistaDTO retirarPropuesta(String tutorId, String propuestaId) {
        Usuario tutor = exigirTutorActivo(tutorId);
        Propuesta propuesta = obtenerPropuestaDelTutor(tutorId, propuestaId);
        propuesta.retirar(Instant.now());
        propuesta = propuestas.save(propuesta);
        auditoria.registrarAccion(tutorId, "PROPUESTA_RETIRADA", entidad(propuesta),
                detalle("solicitudId", propuesta.getSolicitudId()));
        return PropuestaMapper.aVistaDTO(propuesta, tutor, solicitudes.findById(propuesta.getSolicitudId()).orElse(null));
    }

    // ---- Estudiante ----

    /** El estudiante responde con otro precio: ENVIADA → CONTRAOFERTA_ESTUDIANTE. El precio previo queda en la auditoría. */
    public PropuestaVistaDTO contraofertar(String estudianteId, String propuestaId, BigDecimal precioCOP, String nota) {
        exigirEstudianteActivo(estudianteId);
        Propuesta propuesta = obtenerPropuesta(propuestaId);
        SolicitudTutoria solicitud = obtenerSolicitudDelDueno(estudianteId, propuesta.getSolicitudId());
        exigirSolicitudAbierta(solicitud);
        BigDecimal precio = validarPrecio(precioCOP, "de la contraoferta");
        if (propuesta.getPrecioCOP().compareTo(precio) == 0) {
            throw new ExcepcionNegocio("La contraoferta debe ser distinta del precio propuesto; si le conviene, seleccione la propuesta");
        }
        BigDecimal anterior = propuesta.getPrecioCOP();
        Instant ahora = Instant.now();
        propuesta.contraofertar(precio, ahora); // 409 si no está ENVIADA
        if (solicitud.getEstado() != EstadoSolicitud.NEGOCIACION_EN_CURSO) {
            solicitud.iniciarNegociacion();
            solicitud.setFechaActualizacion(ahora);
            solicitudes.save(solicitud);
        }
        propuesta = propuestas.save(propuesta);
        auditoria.registrarAccion(estudianteId, "PROPUESTA_CONTRAOFERTADA", entidad(propuesta),
                detalle("solicitudId", solicitud.getId(), "precioAnteriorCOP", texto(anterior),
                        "contraofertaCOP", texto(precio)));
        String sugerencia = limpiar(nota) == null ? "" : " — «" + limpiar(nota) + "»";
        avisar(propuesta.getTutorId(), TipoNotificacion.CONTRAOFERTA, "Contraoferta recibida",
                "El estudiante propone " + pesos(precio) + " en lugar de " + pesos(anterior) + " para «"
                        + solicitud.getTema() + "»" + sugerencia, solicitud);
        return vista(propuesta, solicitud);
    }

    /**
     * El estudiante selecciona la propuesta: queda ACEPTADA (provisional) y la solicitud pasa a TUTOR_SELECCIONADO,
     * a la espera de la confirmación con el pago. Con una contraoferta pendiente primero debe responder el tutor.
     */
    public PropuestaVistaDTO aceptarPropuesta(String estudianteId, String propuestaId) {
        exigirEstudianteActivo(estudianteId);
        Propuesta propuesta = obtenerPropuesta(propuestaId);
        SolicitudTutoria solicitud = obtenerSolicitudDelDueno(estudianteId, propuesta.getSolicitudId());
        if (propuesta.getEstado() == EstadoPropuesta.CONTRAOFERTA_ESTUDIANTE) {
            throw new ExcepcionNegocio("CONTRAOFERTA_PENDIENTE",
                    "La propuesta tiene una contraoferta pendiente; espere la respuesta del tutor o retire su contraoferta",
                    HttpStatus.CONFLICT);
        }
        Usuario tutor = usuarios.findById(propuesta.getTutorId()).orElse(null);
        if (tutor == null || !tutor.estaActiva()) {
            throw new ExcepcionNegocio("TUTOR_NO_DISPONIBLE", "El tutor ya no está disponible", HttpStatus.CONFLICT);
        }
        Instant ahora = Instant.now();
        solicitud.seleccionarTutor(); // 409 si la solicitud no está abierta o ya tiene tutor
        if (propuesta.getEstado() != EstadoPropuesta.ENVIADA) {
            throw ExcepcionNegocio.transicionInvalida("la propuesta", propuesta.getEstado(), EstadoPropuesta.ACEPTADA);
        }
        propuesta.aceptar(ahora);
        solicitud.setFechaActualizacion(ahora);
        solicitudes.save(solicitud);
        propuesta = propuestas.save(propuesta);
        auditoria.registrarAccion(estudianteId, "PROPUESTA_ACEPTADA", entidad(propuesta),
                detalle("solicitudId", solicitud.getId(), "tutorId", propuesta.getTutorId(),
                        "precioCOP", texto(propuesta.getPrecioCOP())));
        avisar(propuesta.getTutorId(), TipoNotificacion.PROPUESTA_ACEPTADA, "Tu propuesta fue seleccionada",
                "El estudiante seleccionó tu propuesta de " + pesos(propuesta.getPrecioCOP()) + " para «"
                        + solicitud.getTema() + "»; falta que confirme el servicio con el pago", solicitud);
        return PropuestaMapper.aVistaDTO(propuesta, tutor, solicitud);
    }

    /** El estudiante descarta una propuesta que no le interesa (ENVIADA o CONTRAOFERTA_ESTUDIANTE → DESCARTADA). */
    public PropuestaVistaDTO rechazarPropuesta(String estudianteId, String propuestaId, String motivo) {
        exigirEstudianteActivo(estudianteId);
        Propuesta propuesta = obtenerPropuesta(propuestaId);
        SolicitudTutoria solicitud = obtenerSolicitudDelDueno(estudianteId, propuesta.getSolicitudId());
        propuesta.descartar(Instant.now()); // 409 si ya está ACEPTADA, DESCARTADA o RETIRADA
        propuesta = propuestas.save(propuesta);
        auditoria.registrarAccion(estudianteId, "PROPUESTA_RECHAZADA", entidad(propuesta),
                detalle("solicitudId", solicitud.getId(), "motivo", limpiar(motivo)));
        String razon = limpiar(motivo) == null ? "" : " Motivo: " + limpiar(motivo);
        avisar(propuesta.getTutorId(), TipoNotificacion.PROPUESTA_DESCARTADA, "Propuesta rechazada",
                "El estudiante rechazó tu propuesta para «" + solicitud.getTema() + "»." + razon, solicitud);
        return vista(propuesta, solicitud);
    }

    // ---- Sistema ----

    /** Descarta una propuesta activa por una causa ajena al tutor (otro tutor confirmado, solicitud cancelada…). */
    public void descartarPropuesta(Propuesta propuesta, String motivo) {
        propuesta.descartar(Instant.now());
        propuestas.save(propuesta);
        auditoria.registrarAccion(null, "PROPUESTA_DESCARTADA", entidad(propuesta),
                detalle("solicitudId", propuesta.getSolicitudId(), "motivo", motivo));
        SolicitudTutoria solicitud = solicitudes.findById(propuesta.getSolicitudId()).orElse(null);
        avisar(propuesta.getTutorId(), TipoNotificacion.PROPUESTA_DESCARTADA, "Propuesta descartada",
                motivo + (solicitud == null ? "" : " («" + solicitud.getTema() + "»)"), solicitud);
    }

    /**
     * Descarta las propuestas abiertas de una solicitud salvo la indicada (la confirmación del servicio lo usa
     * para quedarse solo con la elegida). Devuelve cuántas descartó.
     */
    public int descartarPropuestasDeSolicitud(String solicitudId, String excepto, String motivo) {
        int total = 0;
        for (Propuesta p : propuestas.findBySolicitudIdAndActivaTrue(solicitudId)) {
            boolean descartable = p.getEstado() == EstadoPropuesta.ENVIADA
                    || p.getEstado() == EstadoPropuesta.CONTRAOFERTA_ESTUDIANTE;
            if (descartable && !Objects.equals(p.getId(), excepto)) {
                descartarPropuesta(p, motivo);
                total++;
            }
        }
        return total;
    }

    // ---- Consultas ----

    /** El dueño (y el administrador) ven todas las propuestas; un tutor solo las suyas. */
    public List<PropuestaVistaDTO> listarPropuestas(UsuarioAutenticado usuario, String solicitudId) {
        SolicitudTutoria solicitud = obtenerSolicitud(solicitudId);
        List<Propuesta> lista;
        if (solicitud.getEstudianteId().equals(usuario.id()) || usuario.tieneAlgunRol(Rol.ADMINISTRADOR)) {
            lista = propuestas.findBySolicitudIdOrderByFechaCreacionDesc(solicitudId);
        } else if (usuario.tieneAlgunRol(Rol.TUTOR)) {
            lista = propuestas.findBySolicitudIdAndTutorIdOrderByFechaCreacionDesc(solicitudId, usuario.id());
        } else {
            throw new ExcepcionAutorizacion("No tiene acceso a las propuestas de esta solicitud");
        }
        return vistas(lista, Map.of(solicitud.getId(), solicitud));
    }

    /** Propuestas del tutor autenticado en todas las solicitudes, con el tema y el estado de cada una. */
    public List<PropuestaVistaDTO> listarMisPropuestas(String tutorId) {
        List<Propuesta> lista = propuestas.findByTutorIdOrderByFechaCreacionDesc(tutorId);
        Map<String, SolicitudTutoria> porId = new HashMap<>();
        solicitudes.findAllById(lista.stream().map(Propuesta::getSolicitudId).collect(Collectors.toSet()))
                .forEach(s -> porId.put(s.getId(), s));
        return vistas(lista, porId);
    }

    // ---- Internos ----

    /** Precio, duración y modalidad efectivos según el tipo (docs/arquitectura/diseno-api.md, sección 9). */
    private record Condiciones(BigDecimal precio, Duracion duracion, Modalidad modalidad) {
    }

    private Condiciones condiciones(PropuestaEntradaDTO dto, SolicitudTutoria s) {
        return switch (dto.tipo()) {
            case ACEPTA_PRECIO -> new Condiciones(s.getPrecioInicialCOP(), s.getDuracion(), s.getModalidad());
            case CONTRAPROPUESTA_PRECIO -> {
                if (dto.precioCOP() == null) {
                    throw datosInvalidos("La contrapropuesta debe indicar el precio");
                }
                BigDecimal precio = validarPrecio(dto.precioCOP(), "de la propuesta");
                if (precio.compareTo(s.getPrecioInicialCOP()) == 0) {
                    throw datosInvalidos("El precio coincide con el de la solicitud; use el tipo ACEPTA_PRECIO");
                }
                yield new Condiciones(precio, s.getDuracion(), s.getModalidad());
            }
            case ALTERNATIVA_CONDICIONES -> {
                BigDecimal precio = dto.precioCOP() == null ? s.getPrecioInicialCOP()
                        : validarPrecio(dto.precioCOP(), "de la propuesta");
                Duracion duracion = dto.duracion() == null ? s.getDuracion() : dto.duracion();
                Modalidad modalidad = dto.modalidad() == null ? s.getModalidad() : dto.modalidad();
                if (duracion == s.getDuracion() && modalidad == s.getModalidad()) {
                    throw datosInvalidos("Una alternativa debe cambiar la duración o la modalidad");
                }
                yield new Condiciones(precio, duracion, modalidad);
            }
        };
    }

    private static BigDecimal validarPrecio(BigDecimal precio, String de) {
        if (precio == null || precio.signum() <= 0) {
            throw datosInvalidos("El precio " + de + " debe ser mayor que cero");
        }
        BigDecimal ajustado = precio.setScale(2, RoundingMode.HALF_UP);
        if (ajustado.compareTo(PRECIO_MAXIMO_COP) > 0) {
            throw datosInvalidos("El precio " + de + " no puede superar " + pesos(PRECIO_MAXIMO_COP));
        }
        return ajustado;
    }

    private void guardarSolicitudOCompensar(SolicitudTutoria solicitud, Propuesta propuesta, Instant ahora) {
        solicitud.setFechaActualizacion(ahora);
        try {
            solicitudes.save(solicitud);
        } catch (RuntimeException e) {
            try {
                propuestas.deleteById(propuesta.getId());
            } catch (RuntimeException ex) {
                log.warn("No se pudo compensar la propuesta {}", propuesta.getId(), ex);
            }
            throw e;
        }
    }

    private Usuario exigirTutorActivo(String id) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> new ExcepcionNoEncontrado("Usuario no encontrado"));
        if (!u.estaActiva()) {
            throw new ExcepcionAutorizacion("Su cuenta no está activa; no puede gestionar propuestas");
        }
        if (!u.tieneRol(Rol.TUTOR)) {
            throw new ExcepcionAutorizacion("Solo los tutores pueden enviar propuestas");
        }
        return u;
    }

    private void exigirEstudianteActivo(String id) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> new ExcepcionNoEncontrado("Usuario no encontrado"));
        if (!u.estaActiva()) {
            throw new ExcepcionAutorizacion("Su cuenta no está activa; no puede gestionar propuestas");
        }
        if (!u.tieneRol(Rol.ESTUDIANTE)) {
            throw new ExcepcionAutorizacion("Solo los estudiantes pueden responder a las propuestas");
        }
    }

    private SolicitudTutoria obtenerSolicitud(String id) {
        return solicitudes.findById(id).orElseThrow(() -> new ExcepcionNoEncontrado("Solicitud no encontrada"));
    }

    private SolicitudTutoria obtenerSolicitudDelDueno(String estudianteId, String solicitudId) {
        SolicitudTutoria s = obtenerSolicitud(solicitudId);
        if (!s.getEstudianteId().equals(estudianteId)) {
            throw new ExcepcionAutorizacion("Solo el dueño de la solicitud puede realizar esta acción");
        }
        return s;
    }

    private Propuesta obtenerPropuesta(String id) {
        return propuestas.findById(id).orElseThrow(() -> new ExcepcionNoEncontrado("Propuesta no encontrada"));
    }

    private Propuesta obtenerPropuestaDelTutor(String tutorId, String propuestaId) {
        Propuesta p = obtenerPropuesta(propuestaId);
        if (!p.getTutorId().equals(tutorId)) {
            throw new ExcepcionAutorizacion("Solo el autor de la propuesta puede realizar esta acción");
        }
        return p;
    }

    private static void exigirSolicitudAbierta(SolicitudTutoria s) {
        if (!s.getEstado().admitePropuestas()) {
            throw new ExcepcionNegocio("SOLICITUD_NO_ADMITE_PROPUESTAS",
                    "La solicitud ya ha sido acordada con otro tutor y no admite nuevas propuestas", HttpStatus.CONFLICT);
        }
    }

    private PropuestaVistaDTO vista(Propuesta p, SolicitudTutoria s) {
        return PropuestaMapper.aVistaDTO(p, usuarios.findById(p.getTutorId()).orElse(null), s);
    }

    private List<PropuestaVistaDTO> vistas(List<Propuesta> lista, Map<String, SolicitudTutoria> solicitudesPorId) {
        Set<String> tutorIds = lista.stream().map(Propuesta::getTutorId).collect(Collectors.toSet());
        Map<String, Usuario> tutores = new HashMap<>();
        if (!tutorIds.isEmpty()) {
            usuarios.findAllById(tutorIds).forEach(u -> tutores.put(u.getId(), u));
        }
        return lista.stream().map(p -> PropuestaMapper.aVistaDTO(p, tutores.get(p.getTutorId()),
                solicitudesPorId.get(p.getSolicitudId()))).toList();
    }

    /** Las notificaciones son secundarias: si fallan, la operación ya hecha no se revierte. */
    private void avisar(String usuarioId, TipoNotificacion tipo, String titulo, String mensaje, SolicitudTutoria s) {
        try {
            notificaciones.notificar(usuarioId, tipo, titulo, mensaje,
                    s == null ? "solicitudes.html" : "solicitudes.html?id=" + s.getId());
        } catch (RuntimeException e) {
            log.warn("No se pudo notificar al usuario {}", usuarioId, e);
        }
    }

    private static Map<String, Object> detalle(Object... pares) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pares.length; i += 2) {
            if (pares[i + 1] != null) {
                mapa.put((String) pares[i], pares[i + 1] instanceof Enum<?> e ? e.name() : pares[i + 1]);
            }
        }
        return mapa;
    }

    private static String entidad(Propuesta p) {
        return "propuesta:" + p.getId();
    }

    private static String texto(BigDecimal valor) {
        return valor == null ? null : valor.toPlainString();
    }

    private static String pesos(BigDecimal valor) {
        return "$" + String.format(java.util.Locale.forLanguageTag("es-CO"), "%,d", valor.setScale(0, RoundingMode.HALF_UP).longValue());
    }

    private static String limpiar(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static ExcepcionNegocio propuestaDuplicada() {
        return new ExcepcionNegocio("DUPLICADO", "Ya tiene una propuesta activa en esta solicitud; modifíquela o retírela",
                HttpStatus.CONFLICT);
    }

    private static ExcepcionNegocio datosInvalidos(String mensaje) {
        return new ExcepcionNegocio("VALIDACION", mensaje, HttpStatus.BAD_REQUEST);
    }
}
