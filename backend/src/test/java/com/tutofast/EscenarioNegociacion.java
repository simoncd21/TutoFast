package com.tutofast;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Pageable;

import com.tutofast.config.GestorTransaccionesNoOp;
import com.tutofast.dto.ComisionDTO;
import com.tutofast.integracion.adaptadores.AdaptadorPasarela;
import com.tutofast.modelo.Calificacion;
import com.tutofast.modelo.EstadoPropuesta;
import com.tutofast.modelo.EstadoSancion;
import com.tutofast.modelo.EstadoServicio;
import com.tutofast.modelo.Mensaje;
import com.tutofast.modelo.Pago;
import com.tutofast.modelo.Propuesta;
import com.tutofast.modelo.RegistroAuditoria;
import com.tutofast.modelo.Reporte;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.Sancion;
import com.tutofast.modelo.ServicioTutoria;
import com.tutofast.modelo.SolicitudTutoria;
import com.tutofast.modelo.TipoCalificacion;
import com.tutofast.modelo.Usuario;
import com.tutofast.repositorio.RepositorioCalificacion;
import com.tutofast.repositorio.RepositorioMensaje;
import com.tutofast.repositorio.RepositorioPago;
import com.tutofast.repositorio.RepositorioPropuesta;
import com.tutofast.repositorio.RepositorioReporte;
import com.tutofast.repositorio.RepositorioSancion;
import com.tutofast.repositorio.RepositorioServicioTutoria;
import com.tutofast.repositorio.RepositorioSolicitudTutoria;
import com.tutofast.repositorio.RepositorioUsuario;
import com.tutofast.servicio.AplicadorSanciones;
import com.tutofast.servicio.ServicioArchivos;
import com.tutofast.servicio.ServicioAuditoria;
import com.tutofast.servicio.ServicioCatalogo;
import com.tutofast.servicio.ServicioMensajeria;
import com.tutofast.servicio.ServicioNegociacion;
import com.tutofast.servicio.ServicioNotificaciones;
import com.tutofast.servicio.ServicioPagos;
import com.tutofast.servicio.ServicioPropuestas;
import com.tutofast.servicio.ServicioReputacion;

/**
 * Servicios de negociación y pagos cableados con repositorios en memoria (sin MongoDB) y la pasarela simulada real.
 * Las pruebas de servicio y de controlador comparten esta preparación; los mapas de usuarios y solicitudes pueden
 * venir de fuera para que otros servicios (identidad) vean los mismos datos.
 */
public class EscenarioNegociacion {

    public final Map<String, Usuario> usuarios;
    public final Map<String, SolicitudTutoria> solicitudes;
    public final Map<String, Propuesta> propuestas = new LinkedHashMap<>();
    public final Map<String, ServicioTutoria> servicios = new LinkedHashMap<>();
    public final Map<String, Pago> pagos = new LinkedHashMap<>();
    public final List<RegistroAuditoria> bitacora = new ArrayList<>();

    public final Map<String, Mensaje> mensajes = new LinkedHashMap<>();
    public final Map<String, Calificacion> calificaciones = new LinkedHashMap<>();
    public final Map<String, Sancion> sanciones = new LinkedHashMap<>();
    public final Map<String, Reporte> reportes = new LinkedHashMap<>();
    /** Servicios con un reporte abierto (lo consulta el cierre automático y la finalización). */
    public final java.util.Set<String> serviciosConReporteAbierto = new java.util.HashSet<>();

    public final RepositorioMensaje repoMensajes = mock(RepositorioMensaje.class);
    public final RepositorioCalificacion repoCalificaciones = mock(RepositorioCalificacion.class);
    public final RepositorioSancion repoSanciones = mock(RepositorioSancion.class);
    public final RepositorioReporte repoReportes = mock(RepositorioReporte.class);
    public final AlmacenamientoEnMemoria almacenamiento = new AlmacenamientoEnMemoria();
    public final RepositorioSolicitudTutoria repoSolicitudes = mock(RepositorioSolicitudTutoria.class);
    public final RepositorioPropuesta repoPropuestas = mock(RepositorioPropuesta.class);
    public final RepositorioServicioTutoria repoServicios = mock(RepositorioServicioTutoria.class);
    public final RepositorioPago repoPagos = mock(RepositorioPago.class);
    public final ServicioNotificaciones notificaciones = mock(ServicioNotificaciones.class);
    public final AdaptadorPasarela pasarela = spy(new AdaptadorPasarela());
    public final ServicioCatalogo catalogo = mock(ServicioCatalogo.class);

    /** Comisión vigente que devuelve el catálogo simulado (cámbiela para simular una nueva configuración). */
    public BigDecimal comisionVigente = new BigDecimal("10");

    public final ServicioPropuestas servicioPropuestas;
    public final ServicioPagos servicioPagos;
    public final RepositorioUsuario repoUsuarios;
    public final ServicioNegociacion servicioNegociacion;
    public final ServicioMensajeria servicioMensajeria;
    public final ServicioReputacion servicioReputacion;
    public final AplicadorSanciones aplicadorSanciones;
    public final ServicioArchivos servicioArchivos;
    public final ServicioAuditoria auditoria;

    public EscenarioNegociacion(RepositorioUsuario repoUsuarios, Map<String, Usuario> usuarios,
                                Map<String, SolicitudTutoria> solicitudes) {
        this.usuarios = usuarios;
        this.solicitudes = solicitudes;
        this.repoUsuarios = repoUsuarios;
        prepararUsuarios(repoUsuarios);
        prepararSolicitudes();
        prepararPropuestas();
        prepararServicios();
        prepararPagos();
        prepararMensajes();
        prepararCalificaciones();
        prepararSancionesYReportes();
        when(catalogo.consultarComision()).thenAnswer(inv ->
                new ComisionDTO("comision-1", comisionVigente, Instant.now(), null));
        when(notificaciones.recordar(any(), any(), any(), any(), any())).thenReturn(true);

        auditoria = RepositorioAuditoriaEnMemoria.servicio(bitacora);
        servicioPropuestas = new ServicioPropuestas(repoPropuestas, repoSolicitudes, repoUsuarios, auditoria, notificaciones);
        servicioPagos = new ServicioPagos(pasarela, repoPagos, repoServicios, auditoria);
        servicioArchivos = new ServicioArchivos(almacenamiento, List.of());
        aplicadorSanciones = new AplicadorSanciones(repoSanciones, repoReportes, repoUsuarios, auditoria, notificaciones);
        servicioMensajeria = new ServicioMensajeria(repoMensajes, repoSolicitudes, repoPropuestas, repoServicios,
                repoUsuarios, servicioArchivos, auditoria, notificaciones, aplicadorSanciones);
        servicioReputacion = new ServicioReputacion(repoCalificaciones, repoServicios, repoUsuarios, auditoria,
                notificaciones, aplicadorSanciones);
        servicioNegociacion = new ServicioNegociacion(repoSolicitudes, repoPropuestas, repoServicios, repoUsuarios,
                servicioPropuestas, servicioPagos, catalogo, auditoria, notificaciones, servicioMensajeria,
                servicioReputacion, repoReportes, new GestorTransaccionesNoOp());
    }

    public EscenarioNegociacion() {
        this(mock(RepositorioUsuario.class), new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    // ---- Fábricas para las pruebas de mensajería, cierre y reputación ----

    public Usuario crearUsuario(String nombre, Rol... roles) {
        Usuario u = new Usuario();
        u.setId(nuevoId());
        u.setNombre(nombre);
        u.setCorreo(u.getId() + "@u.edu.co");
        u.setRoles(new java.util.HashSet<>(java.util.Set.of(roles)));
        u.setEstadoCuenta(com.tutofast.modelo.EstadoCuenta.ACTIVA);
        if (u.getRoles().contains(Rol.TUTOR)) {
            com.tutofast.modelo.PerfilTutor perfil = new com.tutofast.modelo.PerfilTutor();
            perfil.setNivelVerificacion(com.tutofast.modelo.NivelVerificacion.VERIFICADO);
            u.setPerfilTutor(perfil);
        }
        if (u.getRoles().contains(Rol.ESTUDIANTE)) {
            u.setPerfilEstudiante(new com.tutofast.modelo.PerfilEstudiante());
        }
        usuarios.put(u.getId(), u);
        return u;
    }

    public SolicitudTutoria crearSolicitud(Usuario dueno, String precio) {
        SolicitudTutoria s = new SolicitudTutoria();
        s.setId(nuevoId());
        s.setEstudianteId(dueno.getId());
        s.setTema("Ecuaciones diferenciales");
        s.setDescripcion("Requiero explicación del método de separación de variables con ejercicios");
        s.setTipoServicio(com.tutofast.modelo.TipoServicio.EXPLICACION_TEMA);
        s.setFechaHora(Instant.now().plus(3, java.time.temporal.ChronoUnit.DAYS));
        s.setModalidad(com.tutofast.modelo.Modalidad.VIRTUAL);
        s.setDuracion(com.tutofast.modelo.Duracion.MIN_60);
        s.setPrecioInicialCOP(new BigDecimal(precio));
        solicitudes.put(s.getId(), s);
        return s;
    }

    /**
     * Servicio y pago RETENIDO ya creados (sin pasar por la confirmación): comisión del 10 %, 60 minutos, virtual.
     * Para EN_DESARROLLO se fija {@code fechaInicio}; el resto de fechas las ajusta cada prueba.
     */
    public ServicioTutoria crearServicio(Usuario estudiante, Usuario tutor, EstadoServicio estado, Instant fechaHora,
                                         String precio) {
        ServicioTutoria s = new ServicioTutoria();
        s.setId(nuevoId());
        s.setSolicitudId(nuevoId());
        s.setPropuestaId(nuevoId());
        s.setEstudianteId(estudiante.getId());
        s.setTutorId(tutor.getId());
        s.setPrecioFinalCOP(new BigDecimal(precio));
        s.setDuracion(com.tutofast.modelo.Duracion.MIN_60);
        s.setModalidad(com.tutofast.modelo.Modalidad.VIRTUAL);
        s.setFechaHora(fechaHora);
        s.setComisionPct(new BigDecimal("10"));
        s.setFechaConfirmacion(fechaHora.minus(2, java.time.temporal.ChronoUnit.DAYS));
        s.setEstado(estado);
        if (estado != EstadoServicio.CONFIRMADO) {
            s.setFechaInicio(fechaHora);
        }
        servicios.put(s.getId(), s);
        Pago p = Pago.retener(s.getId(), s.getPrecioFinalCOP(), "SIM-" + s.getId(), s.getFechaConfirmacion());
        p.calcularLiquidacion(s.getComisionPct());
        p.setId(nuevoId());
        pagos.put(p.getId(), p);
        return s;
    }

    public static com.tutofast.seguridad.UsuarioAutenticado autenticado(Usuario u) {
        return new com.tutofast.seguridad.UsuarioAutenticado(u.getId(), u.getCorreo(), u.getNombre(), u.getRoles());
    }

    public List<String> acciones() {
        return bitacora.stream().map(RegistroAuditoria::getAccion).toList();
    }

    public static String nuevoId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    private void prepararUsuarios(RepositorioUsuario repo) {
        // Con el repositorio simulado vacío de las pruebas de servicio solo hace falta lectura por id.
        when(repo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(usuarios.get(inv.<String>getArgument(0))));
        when(repo.findAllById(any())).thenAnswer(inv -> {
            List<Usuario> lista = new ArrayList<>();
            for (Object id : (Iterable<?>) inv.getArgument(0)) {
                if (usuarios.containsKey(id)) {
                    lista.add(usuarios.get(id));
                }
            }
            return lista;
        });
        when(repo.findByRolesContaining(any(Rol.class))).thenAnswer(inv -> usuarios.values().stream()
                .filter(u -> u.getRoles().contains(inv.<Rol>getArgument(0))).toList());
    }

    private void prepararMensajes() {
        when(repoMensajes.save(any(Mensaje.class))).thenAnswer(inv -> {
            Mensaje m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId(nuevoId());
            }
            mensajes.put(m.getId(), m);
            return m;
        });
        when(repoMensajes.saveAll(any())).thenAnswer(inv -> {
            List<Mensaje> guardados = new ArrayList<>();
            for (Object o : (Iterable<?>) inv.getArgument(0)) {
                Mensaje m = (Mensaje) o;
                mensajes.put(m.getId(), m);
                guardados.add(m);
            }
            return guardados;
        });
        when(repoMensajes.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(mensajes.get(inv.<String>getArgument(0))));
        when(repoMensajes.findBySolicitudIdAndTutorIdOrderByMarcaTemporalDesc(anyString(), anyString(), any(Pageable.class)))
                .thenAnswer(inv -> hilo(inv.getArgument(0), inv.getArgument(1)).stream()
                        .sorted(Comparator.comparing(Mensaje::getMarcaTemporal).reversed())
                        .limit(inv.<Pageable>getArgument(2).getPageSize()).toList());
        when(repoMensajes.findBySolicitudIdAndTutorIdAndMarcaTemporalAfterOrderByMarcaTemporalAsc(
                anyString(), anyString(), any(Instant.class), any(Pageable.class)))
                .thenAnswer(inv -> hilo(inv.getArgument(0), inv.getArgument(1)).stream()
                        .filter(m -> m.getMarcaTemporal().isAfter(inv.<Instant>getArgument(2)))
                        .sorted(Comparator.comparing(Mensaje::getMarcaTemporal))
                        .limit(inv.<Pageable>getArgument(3).getPageSize()).toList());
        when(repoMensajes.countBySolicitudIdAndTutorIdAndEmisorIdNotAndLeidoFalse(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> hilo(inv.getArgument(0), inv.getArgument(1)).stream()
                        .filter(m -> !m.isLeido() && !inv.getArgument(2).equals(m.getEmisorId())).count());
        when(repoMensajes.findByArchivoGridFsId(anyString())).thenAnswer(inv -> mensajes.values().stream()
                .filter(m -> m.getArchivo() != null && inv.getArgument(0).equals(m.getArchivo().getGridFsId())).findFirst());
        when(repoMensajes.findByReportadoTrueOrderByMarcaTemporalDesc()).thenAnswer(inv -> mensajes.values().stream()
                .filter(Mensaje::isReportado).sorted(Comparator.comparing(Mensaje::getMarcaTemporal).reversed()).toList());
        when(repoMensajes.findByReportadoTrueAndModeracionIsNullOrderByMarcaTemporalDesc()).thenAnswer(inv ->
                mensajes.values().stream().filter(m -> m.isReportado() && m.getModeracion() == null)
                        .sorted(Comparator.comparing(Mensaje::getMarcaTemporal).reversed()).toList());
    }

    private List<Mensaje> hilo(String solicitudId, String tutorId) {
        return mensajes.values().stream().filter(m -> m.getSolicitudId().equals(solicitudId)
                && m.getTutorId().equals(tutorId)).toList();
    }

    private void prepararCalificaciones() {
        when(repoCalificaciones.save(any(Calificacion.class))).thenAnswer(inv -> {
            Calificacion c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(nuevoId());
            }
            // Simula el índice único (servicioId, tipo).
            boolean duplicada = calificaciones.values().stream().anyMatch(o -> !o.getId().equals(c.getId())
                    && o.getServicioId().equals(c.getServicioId()) && o.getTipo() == c.getTipo());
            if (duplicada) {
                throw new DuplicateKeyException("uk_calificacion_servicio_tipo");
            }
            calificaciones.put(c.getId(), c);
            return c;
        });
        when(repoCalificaciones.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(calificaciones.get(inv.<String>getArgument(0))));
        when(repoCalificaciones.existsByServicioIdAndTipo(anyString(), any(TipoCalificacion.class))).thenAnswer(inv ->
                calificaciones.values().stream().anyMatch(c -> c.getServicioId().equals(inv.getArgument(0))
                        && c.getTipo() == inv.<TipoCalificacion>getArgument(1)));
        when(repoCalificaciones.findByServicioIdAndTipo(anyString(), any(TipoCalificacion.class))).thenAnswer(inv ->
                calificaciones.values().stream().filter(c -> c.getServicioId().equals(inv.getArgument(0))
                        && c.getTipo() == inv.<TipoCalificacion>getArgument(1)).findFirst());
        when(repoCalificaciones.findByDestinatarioIdAndVisibleTrueOrderByFechaEmisionDesc(anyString())).thenAnswer(inv ->
                calificaciones.values().stream().filter(c -> c.isVisible() && c.getDestinatarioId().equals(inv.getArgument(0)))
                        .sorted(Comparator.comparing(Calificacion::getFechaEmision).reversed()).toList());
        when(repoCalificaciones.findByReportadaTrueAndModeracionIsNullOrderByFechaReporteDesc()).thenAnswer(inv ->
                calificaciones.values().stream().filter(c -> c.isReportada() && c.getModeracion() == null).toList());
        when(repoCalificaciones.findByVisibleFalseAndOcultaPorModeracionFalseAndFechaLimiteLessThanEqual(any(Instant.class)))
                .thenAnswer(inv -> calificaciones.values().stream().filter(c -> !c.isVisible() && !c.isOcultaPorModeracion()
                        && !c.getFechaLimite().isAfter(inv.<Instant>getArgument(0))).toList());
    }

    private void prepararSancionesYReportes() {
        when(repoSanciones.save(any(Sancion.class))).thenAnswer(inv -> {
            Sancion s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(nuevoId());
            }
            sanciones.put(s.getId(), s);
            return s;
        });
        doAnswer(inv -> {
            sanciones.remove(inv.<String>getArgument(0));
            return null;
        }).when(repoSanciones).deleteById(anyString());
        when(repoSanciones.findByUsuarioIdAndEstadoIn(anyString(), anyCollection())).thenAnswer(inv ->
                sanciones.values().stream().filter(s -> s.getUsuarioId().equals(inv.getArgument(0))
                        && inv.<java.util.Collection<EstadoSancion>>getArgument(1).contains(s.getEstado())).toList());
        when(repoSanciones.findByEstadoInAndVigenciaFinLessThanEqual(anyCollection(), any(Instant.class))).thenAnswer(inv ->
                sanciones.values().stream().filter(s -> inv.<java.util.Collection<EstadoSancion>>getArgument(0).contains(s.getEstado())
                        && s.getVigencia() != null && s.getVigencia().getFin() != null
                        && !s.getVigencia().getFin().isAfter(inv.<Instant>getArgument(1))).toList());
        when(repoReportes.save(any(Reporte.class))).thenAnswer(inv -> {
            Reporte r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(nuevoId());
            }
            reportes.put(r.getId(), r);
            return r;
        });
        doAnswer(inv -> {
            reportes.remove(inv.<String>getArgument(0));
            return null;
        }).when(repoReportes).deleteById(anyString());
        when(repoReportes.existsByServicioIdAndEstadoIn(anyString(), anyCollection())).thenAnswer(inv ->
                serviciosConReporteAbierto.contains(inv.<String>getArgument(0)));
    }

    private void prepararSolicitudes() {
        when(repoSolicitudes.save(any(SolicitudTutoria.class))).thenAnswer(inv -> {
            SolicitudTutoria s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(nuevoId());
            }
            solicitudes.put(s.getId(), s);
            return s;
        });
        when(repoSolicitudes.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(solicitudes.get(inv.<String>getArgument(0))));
        when(repoSolicitudes.findAllById(any())).thenAnswer(inv -> {
            List<SolicitudTutoria> lista = new ArrayList<>();
            for (Object id : (Iterable<?>) inv.getArgument(0)) {
                if (solicitudes.containsKey(id)) {
                    lista.add(solicitudes.get(id));
                }
            }
            return lista;
        });
    }

    private void prepararPropuestas() {
        when(repoPropuestas.save(any(Propuesta.class))).thenAnswer(inv -> {
            Propuesta p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(nuevoId());
            }
            propuestas.put(p.getId(), p);
            return p;
        });
        when(repoPropuestas.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(propuestas.get(inv.<String>getArgument(0))));
        when(repoPropuestas.existsBySolicitudIdAndTutorIdAndActivaTrue(anyString(), anyString())).thenAnswer(inv ->
                propuestas.values().stream().anyMatch(p -> p.isActiva() && p.getSolicitudId().equals(inv.getArgument(0))
                        && p.getTutorId().equals(inv.getArgument(1))));
        when(repoPropuestas.findBySolicitudIdAndActivaTrue(anyString())).thenAnswer(inv -> propuestas.values().stream()
                .filter(p -> p.isActiva() && p.getSolicitudId().equals(inv.getArgument(0))).toList());
        when(repoPropuestas.findFirstBySolicitudIdAndEstado(anyString(), any(EstadoPropuesta.class))).thenAnswer(inv ->
                propuestas.values().stream().filter(p -> p.getSolicitudId().equals(inv.getArgument(0))
                        && p.getEstado() == inv.<EstadoPropuesta>getArgument(1)).findFirst());
        when(repoPropuestas.findBySolicitudIdOrderByFechaCreacionDesc(anyString())).thenAnswer(inv -> propuestas.values()
                .stream().filter(p -> p.getSolicitudId().equals(inv.getArgument(0))).toList());
        when(repoPropuestas.findBySolicitudIdAndTutorIdOrderByFechaCreacionDesc(anyString(), anyString())).thenAnswer(inv ->
                propuestas.values().stream().filter(p -> p.getSolicitudId().equals(inv.getArgument(0))
                        && p.getTutorId().equals(inv.getArgument(1))).toList());
        when(repoPropuestas.findByTutorIdOrderByFechaCreacionDesc(anyString())).thenAnswer(inv -> propuestas.values()
                .stream().filter(p -> p.getTutorId().equals(inv.getArgument(0))).toList());
    }

    private void prepararServicios() {
        when(repoServicios.save(any(ServicioTutoria.class))).thenAnswer(inv -> {
            ServicioTutoria s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(nuevoId());
            }
            // Simula los índices únicos uk_servicio_solicitud y uk_servicio_propuesta.
            boolean duplicado = servicios.values().stream().anyMatch(o -> !o.getId().equals(s.getId())
                    && (o.getSolicitudId().equals(s.getSolicitudId()) || o.getPropuestaId().equals(s.getPropuestaId())));
            if (duplicado) {
                throw new DuplicateKeyException("uk_servicio_solicitud");
            }
            servicios.put(s.getId(), s);
            return s;
        });
        when(repoServicios.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(servicios.get(inv.<String>getArgument(0))));
        when(repoServicios.findByEstado(any(EstadoServicio.class))).thenAnswer(inv -> servicios.values().stream()
                .filter(s -> s.getEstado() == inv.<EstadoServicio>getArgument(0)).toList());
        when(repoServicios.findByEstadoAndFechaHoraLessThanEqual(any(EstadoServicio.class), any(Instant.class))).thenAnswer(inv ->
                servicios.values().stream().filter(s -> s.getEstado() == inv.<EstadoServicio>getArgument(0)
                        && !s.getFechaHora().isAfter(inv.<Instant>getArgument(1))).toList());
        when(repoServicios.findByEstadoAndRecordatorioEnviadoFalseAndFechaHoraBetween(any(EstadoServicio.class),
                any(Instant.class), any(Instant.class))).thenAnswer(inv -> servicios.values().stream()
                .filter(s -> s.getEstado() == inv.<EstadoServicio>getArgument(0) && !s.isRecordatorioEnviado()
                        && s.getFechaHora().isAfter(inv.<Instant>getArgument(1))
                        && s.getFechaHora().isBefore(inv.<Instant>getArgument(2))).toList());
        when(repoServicios.findByEstadoAndRecordatorioCalificacionEnviadoFalseAndFechaFinalizacionBetween(
                any(EstadoServicio.class), any(Instant.class), any(Instant.class))).thenAnswer(inv -> servicios.values().stream()
                .filter(s -> s.getEstado() == inv.<EstadoServicio>getArgument(0) && !s.isRecordatorioCalificacionEnviado()
                        && s.getFechaFinalizacion() != null
                        && s.getFechaFinalizacion().isAfter(inv.<Instant>getArgument(1))
                        && s.getFechaFinalizacion().isBefore(inv.<Instant>getArgument(2))).toList());
        when(repoServicios.countByTutorIdAndEstado(anyString(), any(EstadoServicio.class))).thenAnswer(inv ->
                servicios.values().stream().filter(s -> s.getTutorId().equals(inv.getArgument(0))
                        && s.getEstado() == inv.<EstadoServicio>getArgument(1)).count());
        when(repoServicios.findBySolicitudId(anyString())).thenAnswer(inv -> servicios.values().stream()
                .filter(s -> s.getSolicitudId().equals(inv.getArgument(0))).findFirst());
        when(repoServicios.findByEstudianteIdOrTutorIdOrderByFechaConfirmacionDesc(anyString(), anyString())).thenAnswer(inv ->
                servicios.values().stream().filter(s -> s.getEstudianteId().equals(inv.getArgument(0))
                                || s.getTutorId().equals(inv.getArgument(1)))
                        .sorted(Comparator.comparing(ServicioTutoria::getFechaConfirmacion).reversed()).toList());
        doAnswer(inv -> {
            servicios.remove(inv.<String>getArgument(0));
            return null;
        }).when(repoServicios).deleteById(anyString());
    }

    private void prepararPagos() {
        when(repoPagos.save(any(Pago.class))).thenAnswer(inv -> {
            Pago p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(nuevoId());
            }
            boolean duplicado = pagos.values().stream().anyMatch(o -> !o.getId().equals(p.getId())
                    && o.getServicioId().equals(p.getServicioId()));
            if (duplicado) {
                throw new DuplicateKeyException("uk_pago_servicio");
            }
            pagos.put(p.getId(), p);
            return p;
        });
        when(repoPagos.findByServicioId(anyString())).thenAnswer(inv -> pagos.values().stream()
                .filter(p -> p.getServicioId().equals(inv.getArgument(0))).findFirst());
        when(repoPagos.findByServicioIdIn(anyCollection())).thenAnswer(inv -> pagos.values().stream()
                .filter(p -> inv.<java.util.Collection<String>>getArgument(0).contains(p.getServicioId())).toList());
        doAnswer(inv -> {
            pagos.remove(inv.<String>getArgument(0));
            return null;
        }).when(repoPagos).deleteById(anyString());
    }
}
