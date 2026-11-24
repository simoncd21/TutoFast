package com.tutofast.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import com.tutofast.RepositorioAuditoriaEnMemoria;
import com.tutofast.dto.PropuestaEntradaDTO;
import com.tutofast.dto.PropuestaVistaDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.modelo.Duracion;
import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.EstadoPropuesta;
import com.tutofast.modelo.EstadoSolicitud;
import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.NivelVerificacion;
import com.tutofast.modelo.PerfilTutor;
import com.tutofast.modelo.Propuesta;
import com.tutofast.modelo.RegistroAuditoria;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.SolicitudTutoria;
import com.tutofast.modelo.TipoPropuesta;
import com.tutofast.modelo.TipoServicio;
import com.tutofast.modelo.Usuario;
import com.tutofast.repositorio.RepositorioPropuesta;
import com.tutofast.repositorio.RepositorioSolicitudTutoria;
import com.tutofast.repositorio.RepositorioUsuario;
import com.tutofast.seguridad.UsuarioAutenticado;

class ServicioPropuestasTest {

    private final Map<String, Usuario> usuarios = new LinkedHashMap<>();
    private final Map<String, SolicitudTutoria> solicitudes = new LinkedHashMap<>();
    private final Map<String, Propuesta> propuestas = new LinkedHashMap<>();
    private final List<RegistroAuditoria> bitacora = new ArrayList<>();
    private RepositorioPropuesta repoPropuestas;
    private RepositorioSolicitudTutoria repoSolicitudes;
    private ServicioNotificaciones notificaciones;
    private ServicioPropuestas servicio;
    private Usuario estudiante;
    private Usuario tutor;
    private Usuario otroTutor;
    private SolicitudTutoria solicitud;

    private static String nuevoId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    @BeforeEach
    void preparar() {
        RepositorioUsuario repoUsuarios = mock(RepositorioUsuario.class);
        when(repoUsuarios.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(usuarios.get(inv.<String>getArgument(0))));
        when(repoUsuarios.findAllById(any())).thenAnswer(inv -> {
            List<Usuario> lista = new ArrayList<>();
            for (Object id : (Iterable<?>) inv.getArgument(0)) {
                if (usuarios.containsKey(id)) {
                    lista.add(usuarios.get(id));
                }
            }
            return lista;
        });

        repoSolicitudes = mock(RepositorioSolicitudTutoria.class);
        when(repoSolicitudes.save(any(SolicitudTutoria.class))).thenAnswer(inv -> {
            SolicitudTutoria s = inv.getArgument(0);
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

        repoPropuestas = mock(RepositorioPropuesta.class);
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
        when(repoPropuestas.findBySolicitudIdOrderByFechaCreacionDesc(anyString())).thenAnswer(inv -> propuestas.values().stream()
                .filter(p -> p.getSolicitudId().equals(inv.getArgument(0))).toList());
        when(repoPropuestas.findBySolicitudIdAndTutorIdOrderByFechaCreacionDesc(anyString(), anyString())).thenAnswer(inv ->
                propuestas.values().stream().filter(p -> p.getSolicitudId().equals(inv.getArgument(0))
                        && p.getTutorId().equals(inv.getArgument(1))).toList());
        when(repoPropuestas.findByTutorIdOrderByFechaCreacionDesc(anyString())).thenAnswer(inv -> propuestas.values().stream()
                .filter(p -> p.getTutorId().equals(inv.getArgument(0))).toList());

        notificaciones = mock(ServicioNotificaciones.class);
        servicio = new ServicioPropuestas(repoPropuestas, repoSolicitudes, repoUsuarios,
                RepositorioAuditoriaEnMemoria.servicio(bitacora), notificaciones);

        estudiante = usuario("Estudiante Uno", EstadoCuenta.ACTIVA, Rol.ESTUDIANTE);
        tutor = tutor("Laura Martínez");
        otroTutor = tutor("Carlos Pérez");
        solicitud = solicitud(estudiante);
    }

    // ---- Datos ----

    private Usuario usuario(String nombre, EstadoCuenta estado, Rol... roles) {
        Usuario u = new Usuario();
        u.setId(nuevoId());
        u.setNombre(nombre);
        u.setCorreo(u.getId() + "@u.edu.co");
        u.setRoles(new HashSet<>(Set.of(roles)));
        u.setEstadoCuenta(estado);
        usuarios.put(u.getId(), u);
        return u;
    }

    private Usuario tutor(String nombre) {
        Usuario u = usuario(nombre, EstadoCuenta.ACTIVA, Rol.TUTOR);
        PerfilTutor perfil = new PerfilTutor();
        perfil.setNivelVerificacion(NivelVerificacion.VERIFICADO);
        perfil.setPromedioCalificacion(new BigDecimal("4.75"));
        perfil.setServiciosCompletados(12);
        u.setPerfilTutor(perfil);
        return u;
    }

    private SolicitudTutoria solicitud(Usuario dueno) {
        SolicitudTutoria s = new SolicitudTutoria();
        s.setId(nuevoId());
        s.setEstudianteId(dueno.getId());
        s.setTema("Ecuaciones diferenciales");
        s.setDescripcion("Requiero explicación del método de separación de variables con ejercicios");
        s.setTipoServicio(TipoServicio.EXPLICACION_TEMA);
        s.setFechaHora(Instant.now().plus(3, ChronoUnit.DAYS));
        s.setModalidad(Modalidad.VIRTUAL);
        s.setDuracion(Duracion.MIN_60);
        s.setPrecioInicialCOP(new BigDecimal("25000.00"));
        solicitudes.put(s.getId(), s);
        return s;
    }

    private static PropuestaEntradaDTO aceptaPrecio() {
        return new PropuestaEntradaDTO(TipoPropuesta.ACEPTA_PRECIO, null, null, null, "Con gusto");
    }

    private static PropuestaEntradaDTO contrapropuesta(String precio) {
        return new PropuestaEntradaDTO(TipoPropuesta.CONTRAPROPUESTA_PRECIO, new BigDecimal(precio), null, null, null);
    }

    private PropuestaVistaDTO proponer(Usuario t, PropuestaEntradaDTO dto) {
        return servicio.enviarPropuesta(t.getId(), solicitud.getId(), dto);
    }

    private static void assertNegocio(String codigo, HttpStatus estado, Runnable accion) {
        ExcepcionNegocio e = assertThrows(ExcepcionNegocio.class, accion::run);
        assertEquals(codigo, e.getCodigo());
        assertEquals(estado, e.getEstado());
    }

    private List<String> acciones() {
        return bitacora.stream().map(RegistroAuditoria::getAccion).toList();
    }

    // ---- enviarPropuesta ----

    @Test
    void aceptaPrecioCopiaLasCondicionesDeLaSolicitudYPasaAPropuestasRecibidas() {
        PropuestaVistaDTO p = servicio.enviarPropuesta(tutor.getId(), solicitud.getId(),
                new PropuestaEntradaDTO(TipoPropuesta.ACEPTA_PRECIO, new BigDecimal("99999"), Duracion.MIN_120,
                        Modalidad.PRESENCIAL, "  Con gusto  "));

        assertEquals(0, new BigDecimal("25000").compareTo(p.precioCOP()), "los datos enviados se ignoran");
        assertEquals(Duracion.MIN_60, p.duracion());
        assertEquals(Modalidad.VIRTUAL, p.modalidad());
        assertEquals("Con gusto", p.nota());
        assertEquals(EstadoPropuesta.ENVIADA, p.estado());
        assertEquals("Laura Martínez", p.tutorNombre());
        assertTrue(p.tutorVerificado());
        assertEquals(EstadoSolicitud.PROPUESTAS_RECIBIDAS, solicitud.getEstado());
        assertEquals(1, solicitud.getTotalPropuestas());
        assertTrue(acciones().contains("PROPUESTA_ENVIADA"));
        verify(notificaciones).notificar(anyString(), any(TipoNotificacion.class), anyString(), anyString(), anyString());
    }

    @Test
    void contrapropuestaLlevaLaSolicitudANegociacionYNotificaAlEstudiante() {
        proponer(tutor, contrapropuesta("30000"));

        assertEquals(EstadoSolicitud.NEGOCIACION_EN_CURSO, solicitud.getEstado());
        ArgumentCaptor<String> destinatario = ArgumentCaptor.forClass(String.class);
        verify(notificaciones).notificar(destinatario.capture(), any(TipoNotificacion.class), anyString(), anyString(),
                anyString());
        assertEquals(estudiante.getId(), destinatario.getValue());
    }

    @Test
    void alternativaPuedeCambiarDuracionOModalidadPeroDebeCambiarAlgo() {
        PropuestaVistaDTO p = proponer(tutor, new PropuestaEntradaDTO(TipoPropuesta.ALTERNATIVA_CONDICIONES,
                new BigDecimal("30000"), Duracion.MIN_90, null, null));
        assertEquals(Duracion.MIN_90, p.duracion());
        assertEquals(Modalidad.VIRTUAL, p.modalidad());

        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> proponer(otroTutor,
                new PropuestaEntradaDTO(TipoPropuesta.ALTERNATIVA_CONDICIONES, new BigDecimal("30000"),
                        Duracion.MIN_60, Modalidad.VIRTUAL, null)));
    }

    @Test
    void validaElMonto() {
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> proponer(tutor, contrapropuesta("0")));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> proponer(tutor, contrapropuesta("-5000")));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> proponer(tutor, contrapropuesta("5000001")));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> proponer(tutor,
                new PropuestaEntradaDTO(TipoPropuesta.CONTRAPROPUESTA_PRECIO, null, null, null, null)));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> proponer(tutor, contrapropuesta("25000")));
        assertTrue(propuestas.isEmpty());
        assertEquals(EstadoSolicitud.PUBLICADA, solicitud.getEstado());
    }

    @Test
    void segundaPropuestaActivaDelMismoTutorDa409() {
        proponer(tutor, aceptaPrecio());
        assertNegocio("DUPLICADO", HttpStatus.CONFLICT, () -> proponer(tutor, contrapropuesta("30000")));
        assertEquals(1, propuestas.size());
        assertEquals(1, solicitud.getTotalPropuestas());
    }

    @Test
    void elIndiceUnicoDeMongoTambienSeTraduceA409() {
        when(repoPropuestas.save(any(Propuesta.class))).thenThrow(new DuplicateKeyException("uk_propuesta_activa"));
        assertNegocio("DUPLICADO", HttpStatus.CONFLICT, () -> proponer(tutor, aceptaPrecio()));
        verify(repoSolicitudes, never()).save(any(SolicitudTutoria.class));
    }

    @Test
    void elTutorPuedeVolverAProponerTrasRetirar() {
        PropuestaVistaDTO primera = proponer(tutor, aceptaPrecio());
        servicio.retirarPropuesta(tutor.getId(), primera.id());
        PropuestaVistaDTO segunda = proponer(tutor, contrapropuesta("30000"));
        assertEquals(EstadoPropuesta.ENVIADA, segunda.estado());
    }

    @Test
    void propuestaSobreSolicitudAcordadaOCanceladaDa409() {
        for (EstadoSolicitud estado : List.of(EstadoSolicitud.TUTOR_SELECCIONADO, EstadoSolicitud.FORMALIZADA,
                EstadoSolicitud.CANCELADA)) {
            solicitud.setEstado(estado);
            assertNegocio("SOLICITUD_NO_ADMITE_PROPUESTAS", HttpStatus.CONFLICT, () -> proponer(tutor, aceptaPrecio()));
        }
        assertTrue(propuestas.isEmpty());
    }

    @Test
    void elDuenoDeLaSolicitudNoPuedeProponer() {
        estudiante.getRoles().add(Rol.TUTOR);
        estudiante.setPerfilTutor(new PerfilTutor());
        assertThrows(ExcepcionAutorizacion.class, () -> proponer(estudiante, aceptaPrecio()));
    }

    @Test
    void soloTutoresActivosPuedenProponer() {
        assertThrows(ExcepcionAutorizacion.class, () -> proponer(usuario("Solo estudiante", EstadoCuenta.ACTIVA, Rol.ESTUDIANTE),
                aceptaPrecio()));
        Usuario suspendido = usuario("Suspendido", EstadoCuenta.SUSPENDIDA, Rol.TUTOR);
        assertThrows(ExcepcionAutorizacion.class, () -> proponer(suspendido, aceptaPrecio()));
        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.enviarPropuesta(tutor.getId(), "no-existe", aceptaPrecio()));
    }

    @Test
    void siFallaGuardarLaSolicitudSeCompensaLaPropuesta() {
        when(repoSolicitudes.save(any(SolicitudTutoria.class))).thenThrow(new IllegalStateException("fallo simulado"));
        assertThrows(IllegalStateException.class, () -> proponer(tutor, aceptaPrecio()));
        verify(repoPropuestas).deleteById(anyString());
    }

    @Test
    void unaNotificacionQueFallaNoRevierteLaPropuesta() {
        doThrow(new IllegalStateException("sin notificador")).when(notificaciones)
                .notificar(anyString(), any(TipoNotificacion.class), anyString(), anyString(), anyString());
        PropuestaVistaDTO p = proponer(tutor, aceptaPrecio());
        assertEquals(EstadoPropuesta.ENVIADA, p.estado());
    }

    // ---- contraofertar ----

    @Test
    void contraofertarGuardaElPrecioNotificaAlTutorYAuditaElAnterior() {
        PropuestaVistaDTO p = proponer(tutor, contrapropuesta("30000"));

        PropuestaVistaDTO c = servicio.contraofertar(estudiante.getId(), p.id(), new BigDecimal("27000"), "¿27.000?");

        assertEquals(EstadoPropuesta.CONTRAOFERTA_ESTUDIANTE, c.estado());
        assertEquals(0, new BigDecimal("27000").compareTo(c.contraofertaCOP()));
        assertEquals(0, new BigDecimal("30000").compareTo(c.precioCOP()), "el precio del tutor no cambia");
        RegistroAuditoria registro = bitacora.stream().filter(r -> "PROPUESTA_CONTRAOFERTADA".equals(r.getAccion()))
                .findFirst().orElseThrow();
        assertEquals("30000.00", registro.getDetalle().get("precioAnteriorCOP"));
        assertEquals("27000.00", registro.getDetalle().get("contraofertaCOP"));
        verify(notificaciones).notificar(org.mockito.ArgumentMatchers.eq(tutor.getId()),
                org.mockito.ArgumentMatchers.eq(TipoNotificacion.CONTRAOFERTA), anyString(), anyString(), anyString());
    }

    @Test
    void contraofertarDesdeAceptaPrecioMueveLaSolicitudANegociacion() {
        PropuestaVistaDTO p = proponer(tutor, aceptaPrecio());
        assertEquals(EstadoSolicitud.PROPUESTAS_RECIBIDAS, solicitud.getEstado());
        servicio.contraofertar(estudiante.getId(), p.id(), new BigDecimal("20000"), null);
        assertEquals(EstadoSolicitud.NEGOCIACION_EN_CURSO, solicitud.getEstado());
    }

    @Test
    void contraofertarExigeDuenoMontoValidoYEstadoEnviada() {
        PropuestaVistaDTO p = proponer(tutor, contrapropuesta("30000"));
        Usuario otro = usuario("Otro estudiante", EstadoCuenta.ACTIVA, Rol.ESTUDIANTE);

        assertThrows(ExcepcionAutorizacion.class, () -> servicio.contraofertar(otro.getId(), p.id(), new BigDecimal("27000"), null));
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.contraofertar(tutor.getId(), p.id(), new BigDecimal("27000"), null));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> servicio.contraofertar(estudiante.getId(), p.id(), BigDecimal.ZERO, null));
        assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY,
                () -> servicio.contraofertar(estudiante.getId(), p.id(), new BigDecimal("30000"), null));

        servicio.contraofertar(estudiante.getId(), p.id(), new BigDecimal("27000"), null);
        assertNegocio("TRANSICION_INVALIDA", HttpStatus.CONFLICT,
                () -> servicio.contraofertar(estudiante.getId(), p.id(), new BigDecimal("26000"), null));
        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.contraofertar(estudiante.getId(), "x", new BigDecimal("1"), null));
    }

    @Test
    void elTutorRespondeALaContraofertaModificandoYVuelveAEnviada() {
        PropuestaVistaDTO p = proponer(tutor, contrapropuesta("30000"));
        servicio.contraofertar(estudiante.getId(), p.id(), new BigDecimal("27000"), null);

        PropuestaVistaDTO m = servicio.modificarPropuesta(tutor.getId(), p.id(), contrapropuesta("28000"));

        assertEquals(EstadoPropuesta.ENVIADA, m.estado());
        assertEquals(0, new BigDecimal("28000").compareTo(m.precioCOP()));
        assertEquals(null, m.contraofertaCOP());
        assertTrue(acciones().contains("PROPUESTA_MODIFICADA"));
    }

    // ---- modificar / retirar ----

    @Test
    void soloElAutorModificaORetira() {
        PropuestaVistaDTO p = proponer(tutor, aceptaPrecio());
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.modificarPropuesta(otroTutor.getId(), p.id(), contrapropuesta("30000")));
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.retirarPropuesta(otroTutor.getId(), p.id()));
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.retirarPropuesta(estudiante.getId(), p.id()));
    }

    @Test
    void retirarLiberaLaPropuestaYNoSePuedeRetirarDosVeces() {
        PropuestaVistaDTO p = proponer(tutor, aceptaPrecio());
        PropuestaVistaDTO r = servicio.retirarPropuesta(tutor.getId(), p.id());
        assertEquals(EstadoPropuesta.RETIRADA, r.estado());
        assertFalse(propuestas.get(p.id()).isActiva());
        assertNegocio("TRANSICION_INVALIDA", HttpStatus.CONFLICT, () -> servicio.retirarPropuesta(tutor.getId(), p.id()));
        assertNegocio("TRANSICION_INVALIDA", HttpStatus.CONFLICT,
                () -> servicio.modificarPropuesta(tutor.getId(), p.id(), contrapropuesta("30000")));
    }

    @Test
    void noSeModificaUnaPropuestaSiLaSolicitudYaNoEstaAbierta() {
        PropuestaVistaDTO p = proponer(tutor, aceptaPrecio());
        servicio.aceptarPropuesta(estudiante.getId(), p.id());
        assertNegocio("SOLICITUD_NO_ADMITE_PROPUESTAS", HttpStatus.CONFLICT,
                () -> servicio.modificarPropuesta(tutor.getId(), p.id(), contrapropuesta("30000")));
        assertNegocio("TRANSICION_INVALIDA", HttpStatus.CONFLICT, () -> servicio.retirarPropuesta(tutor.getId(), p.id()));
    }

    // ---- aceptarPropuesta ----

    @Test
    void aceptarMarcaLaPropuestaYLaSolicitudYNotificaAlTutor() {
        PropuestaVistaDTO p = proponer(tutor, aceptaPrecio());

        PropuestaVistaDTO a = servicio.aceptarPropuesta(estudiante.getId(), p.id());

        assertEquals(EstadoPropuesta.ACEPTADA, a.estado());
        assertEquals(EstadoSolicitud.TUTOR_SELECCIONADO, solicitud.getEstado());
        assertTrue(propuestas.get(p.id()).isActiva(), "una ACEPTADA sigue activa hasta la confirmación");
        assertTrue(acciones().contains("PROPUESTA_ACEPTADA"));
        verify(notificaciones).notificar(org.mockito.ArgumentMatchers.eq(tutor.getId()),
                org.mockito.ArgumentMatchers.eq(TipoNotificacion.PROPUESTA_ACEPTADA), anyString(), anyString(), anyString());
    }

    @Test
    void noSeAceptaConContraofertaPendienteNiDosVeces() {
        PropuestaVistaDTO a = proponer(tutor, contrapropuesta("30000"));
        PropuestaVistaDTO b = proponer(otroTutor, aceptaPrecio());
        servicio.contraofertar(estudiante.getId(), a.id(), new BigDecimal("27000"), null);

        assertNegocio("CONTRAOFERTA_PENDIENTE", HttpStatus.CONFLICT, () -> servicio.aceptarPropuesta(estudiante.getId(), a.id()));

        servicio.aceptarPropuesta(estudiante.getId(), b.id());
        assertNegocio("TRANSICION_INVALIDA", HttpStatus.CONFLICT, () -> servicio.aceptarPropuesta(estudiante.getId(), b.id()));
    }

    @Test
    void soloElDuenoAceptaYElTutorDebeSeguirActivo() {
        PropuestaVistaDTO p = proponer(tutor, aceptaPrecio());
        Usuario otro = usuario("Otro estudiante", EstadoCuenta.ACTIVA, Rol.ESTUDIANTE);
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.aceptarPropuesta(otro.getId(), p.id()));
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.aceptarPropuesta(tutor.getId(), p.id()));

        tutor.setEstadoCuenta(EstadoCuenta.SUSPENDIDA);
        assertNegocio("TUTOR_NO_DISPONIBLE", HttpStatus.CONFLICT, () -> servicio.aceptarPropuesta(estudiante.getId(), p.id()));
        assertEquals(EstadoSolicitud.PROPUESTAS_RECIBIDAS, solicitud.getEstado());
    }

    @Test
    void conTutorSeleccionadoLasDemasPropuestasNoSePuedenAceptar() {
        PropuestaVistaDTO a = proponer(tutor, aceptaPrecio());
        PropuestaVistaDTO b = proponer(otroTutor, aceptaPrecio());
        servicio.aceptarPropuesta(estudiante.getId(), a.id());
        assertNegocio("TRANSICION_INVALIDA", HttpStatus.CONFLICT, () -> servicio.aceptarPropuesta(estudiante.getId(), b.id()));
        assertEquals(EstadoPropuesta.ENVIADA, propuestas.get(b.id()).getEstado(), "no queda a medias");
    }

    // ---- rechazar / descartar ----

    @Test
    void rechazarDescartaLaPropuestaYNotificaAlTutorConElMotivo() {
        PropuestaVistaDTO p = proponer(tutor, aceptaPrecio());

        PropuestaVistaDTO r = servicio.rechazarPropuesta(estudiante.getId(), p.id(), "Prefiero otro horario");

        assertEquals(EstadoPropuesta.DESCARTADA, r.estado());
        assertFalse(propuestas.get(p.id()).isActiva());
        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        verify(notificaciones).notificar(org.mockito.ArgumentMatchers.eq(tutor.getId()),
                org.mockito.ArgumentMatchers.eq(TipoNotificacion.PROPUESTA_DESCARTADA), anyString(), mensaje.capture(), anyString());
        assertTrue(mensaje.getValue().contains("Prefiero otro horario"));
        assertNegocio("TRANSICION_INVALIDA", HttpStatus.CONFLICT, () -> servicio.rechazarPropuesta(estudiante.getId(), p.id(), null));
    }

    @Test
    void noSeRechazaUnaPropuestaYaAceptadaNiAjena() {
        PropuestaVistaDTO p = proponer(tutor, aceptaPrecio());
        Usuario otro = usuario("Otro estudiante", EstadoCuenta.ACTIVA, Rol.ESTUDIANTE);
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.rechazarPropuesta(otro.getId(), p.id(), null));
        servicio.aceptarPropuesta(estudiante.getId(), p.id());
        assertNegocio("TRANSICION_INVALIDA", HttpStatus.CONFLICT, () -> servicio.rechazarPropuesta(estudiante.getId(), p.id(), null));
    }

    @Test
    void descartarPropuestasDeSolicitudConservaLaExceptuadaYLaAceptada() {
        PropuestaVistaDTO elegida = proponer(tutor, aceptaPrecio());
        PropuestaVistaDTO otra = proponer(otroTutor, contrapropuesta("30000"));
        Usuario tercero = tutor("Tercer tutor");
        PropuestaVistaDTO enContraoferta = proponer(tercero, contrapropuesta("32000"));
        servicio.contraofertar(estudiante.getId(), enContraoferta.id(), new BigDecimal("28000"), null);

        int descartadas = servicio.descartarPropuestasDeSolicitud(solicitud.getId(), elegida.id(), "Otro tutor fue confirmado");

        assertEquals(2, descartadas);
        assertEquals(EstadoPropuesta.ENVIADA, propuestas.get(elegida.id()).getEstado());
        assertEquals(EstadoPropuesta.DESCARTADA, propuestas.get(otra.id()).getEstado());
        assertEquals(EstadoPropuesta.DESCARTADA, propuestas.get(enContraoferta.id()).getEstado());
        assertEquals(0, servicio.descartarPropuestasDeSolicitud(solicitud.getId(), elegida.id(), "otra vez"));
    }

    @Test
    void descartarPropuestaAuditaConElSistemaComoActor() {
        PropuestaVistaDTO p = proponer(tutor, aceptaPrecio());
        servicio.descartarPropuesta(propuestas.get(p.id()), "Solicitud cancelada");
        RegistroAuditoria r = bitacora.stream().filter(x -> "PROPUESTA_DESCARTADA".equals(x.getAccion())).findFirst().orElseThrow();
        assertEquals(null, r.getUsuarioId());
        assertEquals(EstadoPropuesta.DESCARTADA, propuestas.get(p.id()).getEstado());
    }

    // ---- listar ----

    @Test
    void elDuenoVeTodasElTutorSoloLaSuyaYUnTerceroNada() {
        proponer(tutor, aceptaPrecio());
        proponer(otroTutor, contrapropuesta("30000"));

        UsuarioAutenticado dueno = autenticado(estudiante);
        assertEquals(2, servicio.listarPropuestas(dueno, solicitud.getId()).size());
        List<PropuestaVistaDTO> propias = servicio.listarPropuestas(autenticado(otroTutor), solicitud.getId());
        assertEquals(1, propias.size());
        assertEquals(otroTutor.getId(), propias.get(0).tutorId());
        assertEquals(2, servicio.listarPropuestas(autenticado(usuario("Admin", EstadoCuenta.ACTIVA, Rol.ADMINISTRADOR)),
                solicitud.getId()).size());

        Usuario otro = usuario("Otro estudiante", EstadoCuenta.ACTIVA, Rol.ESTUDIANTE);
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.listarPropuestas(autenticado(otro), solicitud.getId()));
        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.listarPropuestas(dueno, "no-existe"));
    }

    @Test
    void misPropuestasIncluyeTemaYEstadoDeLaSolicitud() {
        proponer(tutor, aceptaPrecio());
        List<PropuestaVistaDTO> mias = servicio.listarMisPropuestas(tutor.getId());
        assertEquals(1, mias.size());
        assertEquals("Ecuaciones diferenciales", mias.get(0).solicitudTema());
        assertEquals(EstadoSolicitud.PROPUESTAS_RECIBIDAS, mias.get(0).solicitudEstado());
        assertTrue(servicio.listarMisPropuestas(otroTutor.getId()).isEmpty());
    }

    private static UsuarioAutenticado autenticado(Usuario u) {
        return new UsuarioAutenticado(u.getId(), u.getCorreo(), u.getNombre(), new HashSet<>(u.getRoles()));
    }
}
