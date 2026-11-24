package com.tutofast.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.tutofast.EscenarioNegociacion;
import com.tutofast.dto.EnlaceServicioDTO;
import com.tutofast.dto.PropuestaEntradaDTO;
import com.tutofast.dto.ServicioDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.modelo.Duracion;
import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.EstadoPago;
import com.tutofast.modelo.EstadoPropuesta;
import com.tutofast.modelo.EstadoServicio;
import com.tutofast.modelo.EstadoSolicitud;
import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.NivelVerificacion;
import com.tutofast.modelo.PerfilTutor;
import com.tutofast.modelo.RegistroAuditoria;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.SolicitudTutoria;
import com.tutofast.modelo.TipoPropuesta;
import com.tutofast.modelo.TipoServicio;
import com.tutofast.modelo.Usuario;
import com.tutofast.seguridad.UsuarioAutenticado;

class ServicioNegociacionTest {

    private static final String TOKEN_OK = "tok_aprobar";
    private static final String TOKEN_RECHAZO = "tok_rechazar";

    private EscenarioNegociacion e;
    private ServicioNegociacion servicio;
    private Usuario estudiante;
    private Usuario tutor;
    private Usuario otroTutor;
    private SolicitudTutoria solicitud;
    private String propuestaElegida;
    private String propuestaOtra;

    @BeforeEach
    void preparar() {
        e = new EscenarioNegociacion();
        servicio = e.servicioNegociacion;
        estudiante = usuario("Carlos Gómez", Rol.ESTUDIANTE);
        tutor = usuario("Laura Martínez", Rol.TUTOR);
        otroTutor = usuario("Andrés Pérez", Rol.TUTOR);
        solicitud = solicitud(estudiante, "40000");
        // Laura contrapropone 30.000 y Andrés acepta el precio; el estudiante selecciona a Laura.
        propuestaElegida = e.servicioPropuestas.enviarPropuesta(tutor.getId(), solicitud.getId(),
                new PropuestaEntradaDTO(TipoPropuesta.CONTRAPROPUESTA_PRECIO, new BigDecimal("30000"), null, null, null)).id();
        propuestaOtra = e.servicioPropuestas.enviarPropuesta(otroTutor.getId(), solicitud.getId(),
                new PropuestaEntradaDTO(TipoPropuesta.ACEPTA_PRECIO, null, null, null, null)).id();
        e.servicioPropuestas.aceptarPropuesta(estudiante.getId(), propuestaElegida);
    }

    private static void assertNegocio(String codigo, HttpStatus estado, Runnable accion) {
        ExcepcionNegocio ex = assertThrows(ExcepcionNegocio.class, accion::run);
        assertEquals(codigo, ex.getCodigo());
        assertEquals(estado, ex.getEstado());
    }

    private ServicioDTO confirmar() {
        return servicio.confirmarServicio(estudiante.getId(), solicitud.getId(), TOKEN_OK);
    }

    // ---- Confirmación ----

    @Test
    void confirmarFormalizaLaPropuestaAceptadaYRetieneElPago() {
        ServicioDTO dto = confirmar();

        assertEquals(EstadoServicio.CONFIRMADO, dto.estado());
        assertEquals(solicitud.getId(), dto.solicitudId());
        assertEquals(propuestaElegida, dto.propuestaId());
        assertEquals("Carlos Gómez", dto.estudiante().nombre());
        assertEquals("Laura Martínez", dto.tutor().nombre());
        assertEquals(0, new BigDecimal("30000").compareTo(dto.precioFinalCOP()), "precio de la propuesta, no de la solicitud");
        assertEquals(EstadoPago.RETENIDO, dto.pago().estado());
        assertTrue(dto.pago().referencia().startsWith("SIM-"));
        assertEquals(EstadoSolicitud.FORMALIZADA, solicitud.getEstado());
        assertEquals(1, e.servicios.size());
        assertEquals(1, e.pagos.size());
    }

    @Test
    void calculaLaComisionVigenteYElNetoConBigDecimal() {
        ServicioDTO dto = confirmar();

        assertEquals(0, new BigDecimal("10").compareTo(dto.comisionPct()));
        assertEquals(0, new BigDecimal("3000").compareTo(dto.pago().comisionCOP()));
        assertEquals(0, new BigDecimal("27000").compareTo(dto.pago().montoNetoCOP()));
        assertEquals(0, new BigDecimal("30000").compareTo(dto.pago().montoRetenidoCOP()));
    }

    @Test
    void laComisionConFraccionRedondeaHalfUpAPesosEnteros() {
        e.comisionVigente = new BigDecimal("12.5");
        e.propuestas.get(propuestaElegida).setPrecioCOP(new BigDecimal("33333")); // 4.166,625 → 4.167

        ServicioDTO dto = confirmar();

        assertEquals(0, new BigDecimal("4167").compareTo(dto.pago().comisionCOP()));
        assertEquals(0, new BigDecimal("29166").compareTo(dto.pago().montoNetoCOP()));
    }

    @Test
    void cambiarLaComisionDespuesNoAfectaAlServicioConfirmado() {
        ServicioDTO dto = confirmar();
        e.comisionVigente = new BigDecimal("25");

        ServicioDTO leido = servicio.obtener(autenticado(estudiante), dto.id());

        assertEquals(0, new BigDecimal("10").compareTo(leido.comisionPct()));
        assertEquals(0, new BigDecimal("3000").compareTo(leido.pago().comisionCOP()));
    }

    @Test
    void descartaLasDemasPropuestasYConservaLaElegida() {
        confirmar();

        assertEquals(EstadoPropuesta.ACEPTADA, e.propuestas.get(propuestaElegida).getEstado());
        assertEquals(EstadoPropuesta.DESCARTADA, e.propuestas.get(propuestaOtra).getEstado());
        assertFalse(e.propuestas.get(propuestaOtra).isActiva());
        assertTrue(acciones().contains("PROPUESTA_DESCARTADA"));
    }

    @Test
    void notificaASusDosPartesYRegistraAuditoriaSinElToken() {
        ServicioDTO dto = confirmar();

        verify(e.notificaciones).notificar(eq(tutor.getId()), eq(TipoNotificacion.SERVICIO_CONFIRMADO), anyString(),
                anyString(), eq("negociacion.html?id=" + dto.id()));
        verify(e.notificaciones).notificar(eq(estudiante.getId()), eq(TipoNotificacion.SERVICIO_CONFIRMADO), anyString(),
                anyString(), eq("negociacion.html?id=" + dto.id()));
        assertTrue(acciones().containsAll(List.of("SERVICIO_CONFIRMADO", "PAGO_RETENIDO")));
        e.bitacora.forEach(r -> assertFalse(r.getDetalle().toString().contains(TOKEN_OK), "la auditoría no guarda el token"));
        e.pagos.values().forEach(p -> assertFalse(p.toString().contains(TOKEN_OK)));
    }

    @Test
    void unFalloAlNotificarNoRevierteLaConfirmacion() {
        doThrow(new RuntimeException("sin conexión")).when(e.notificaciones)
                .notificar(anyString(), any(TipoNotificacion.class), anyString(), anyString(), anyString());

        ServicioDTO dto = confirmar();

        assertEquals(EstadoServicio.CONFIRMADO, dto.estado());
        assertEquals(EstadoSolicitud.FORMALIZADA, solicitud.getEstado());
    }

    // ---- Pago rechazado ----

    @Test
    void pagoRechazadoDa422YNoCambiaNingunEstado() {
        assertNegocio("PAGO_RECHAZADO", HttpStatus.UNPROCESSABLE_ENTITY,
                () -> servicio.confirmarServicio(estudiante.getId(), solicitud.getId(), TOKEN_RECHAZO));

        assertEquals(EstadoSolicitud.TUTOR_SELECCIONADO, solicitud.getEstado());
        assertTrue(e.servicios.isEmpty(), "el servicio reservado se compensa");
        assertTrue(e.pagos.isEmpty());
        assertEquals(EstadoPropuesta.ACEPTADA, e.propuestas.get(propuestaElegida).getEstado());
        assertEquals(EstadoPropuesta.ENVIADA, e.propuestas.get(propuestaOtra).getEstado());
        assertFalse(acciones().contains("SERVICIO_CONFIRMADO"));
        verify(e.notificaciones, never()).notificar(anyString(), eq(TipoNotificacion.SERVICIO_CONFIRMADO), anyString(),
                anyString(), anyString());
    }

    @Test
    void despuesDeUnRechazoSePuedeReintentarConUnTokenValido() {
        assertThrows(ExcepcionNegocio.class,
                () -> servicio.confirmarServicio(estudiante.getId(), solicitud.getId(), TOKEN_RECHAZO));

        assertEquals(EstadoServicio.CONFIRMADO, confirmar().estado());
    }

    @Test
    void unTokenVacioTambienLoRechazaLaPasarela() {
        assertNegocio("PAGO_RECHAZADO", HttpStatus.UNPROCESSABLE_ENTITY,
                () -> servicio.confirmarServicio(estudiante.getId(), solicitud.getId(), "  "));
        assertTrue(e.servicios.isEmpty());
    }

    // ---- Idempotencia y estados conflictivos ----

    @Test
    void confirmarDosVecesDa409YNoCobraDeNuevo() {
        confirmar();

        assertNegocio("SERVICIO_YA_CONFIRMADO", HttpStatus.CONFLICT, this::confirmar);

        assertEquals(1, e.servicios.size());
        assertEquals(1, e.pagos.size());
        verify(e.pasarela, times(1)).procesarPago(anyString(), any(BigDecimal.class));
    }

    @Test
    void unaConfirmacionConcurrenteQueLlegaAlIndiceUnicoDa409SinCobrar() {
        // Otra petición reservó el servicio justo después de las comprobaciones: lo detecta uk_servicio_solicitud.
        var ajeno = com.tutofast.modelo.ServicioTutoria.confirmar(solicitudClon(), e.propuestas.get(propuestaElegida),
                new BigDecimal("10"), Instant.now());
        ajeno.setId("ajeno");
        e.servicios.put("ajeno", ajeno);
        // La comprobación previa no ve el servicio ajeno (carrera), pero el guardado choca con el índice único.
        org.mockito.Mockito.when(e.repoServicios.findBySolicitudId(solicitud.getId()))
                .thenReturn(java.util.Optional.empty());

        assertNegocio("SERVICIO_YA_CONFIRMADO", HttpStatus.CONFLICT, this::confirmar);

        verify(e.pasarela, never()).procesarPago(anyString(), any(BigDecimal.class));
        assertEquals(EstadoSolicitud.TUTOR_SELECCIONADO, solicitud.getEstado());
        assertTrue(e.pagos.isEmpty());
    }

    @Test
    void sinTutorSeleccionadoDa409() {
        solicitud.setEstado(EstadoSolicitud.NEGOCIACION_EN_CURSO);
        assertNegocio("SOLICITUD_NO_CONFIRMABLE", HttpStatus.CONFLICT, this::confirmar);

        solicitud.setEstado(EstadoSolicitud.CANCELADA);
        assertNegocio("SOLICITUD_NO_CONFIRMABLE", HttpStatus.CONFLICT, this::confirmar);
        verify(e.pasarela, never()).procesarPago(anyString(), any(BigDecimal.class));
    }

    @Test
    void sinPropuestaAceptadaDa409() {
        e.propuestas.get(propuestaElegida).setEstado(EstadoPropuesta.RETIRADA); // sin ACEPTADA con la solicitud aún TUTOR_SELECCIONADO

        assertNegocio("PROPUESTA_NO_SELECCIONADA", HttpStatus.CONFLICT, this::confirmar);
        verify(e.pasarela, never()).procesarPago(anyString(), any(BigDecimal.class));
    }

    @Test
    void tutorSuspendidoDa409() {
        tutor.setEstadoCuenta(EstadoCuenta.SUSPENDIDA);

        assertNegocio("TUTOR_NO_DISPONIBLE", HttpStatus.CONFLICT, this::confirmar);
        verify(e.pasarela, never()).procesarPago(anyString(), any(BigDecimal.class));
    }

    @Test
    void siFallaElGuardadoDeLaSolicitudSeCompensaElPagoYElServicio() {
        doThrow(new IllegalStateException("Mongo caído")).when(e.repoSolicitudes).save(any(SolicitudTutoria.class));

        assertThrows(IllegalStateException.class, this::confirmar);

        assertTrue(e.servicios.isEmpty());
        assertTrue(e.pagos.isEmpty());
    }

    // ---- Roles y permisos ----

    @Test
    void soloElDuenoDeLaSolicitudPuedeConfirmar() {
        Usuario intruso = usuario("Otro Estudiante", Rol.ESTUDIANTE);

        assertThrows(ExcepcionAutorizacion.class,
                () -> servicio.confirmarServicio(intruso.getId(), solicitud.getId(), TOKEN_OK));
        assertThrows(ExcepcionAutorizacion.class,
                () -> servicio.confirmarServicio(tutor.getId(), solicitud.getId(), TOKEN_OK));
        verify(e.pasarela, never()).procesarPago(anyString(), any(BigDecimal.class));
    }

    @Test
    void cuentaSuspendidaDelEstudianteNoPuedeConfirmar() {
        estudiante.setEstadoCuenta(EstadoCuenta.SUSPENDIDA);
        assertThrows(ExcepcionAutorizacion.class, this::confirmar);
    }

    @Test
    void solicitudInexistenteDa404() {
        assertThrows(ExcepcionNoEncontrado.class,
                () -> servicio.confirmarServicio(estudiante.getId(), "no-existe", TOKEN_OK));
    }

    // ---- Consulta ----

    @Test
    void soloLosParticipantesYElAdministradorVenElServicio() {
        ServicioDTO dto = confirmar();
        Usuario intruso = usuario("Otro", Rol.ESTUDIANTE, Rol.TUTOR);
        Usuario admin = usuario("Admin", Rol.ADMINISTRADOR);

        assertEquals(dto.id(), servicio.obtener(autenticado(estudiante), dto.id()).id());
        assertEquals(dto.id(), servicio.obtener(autenticado(tutor), dto.id()).id());
        assertEquals(dto.id(), servicio.obtener(autenticado(admin), dto.id()).id());
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.obtener(autenticado(intruso), dto.id()));
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.obtener(autenticado(otroTutor), dto.id()));
        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.obtener(autenticado(admin), "no-existe"));
    }

    @Test
    void listarMiosIncluyeAmbosRolesYFiltraPorEstado() {
        ServicioDTO dto = confirmar();

        assertEquals(List.of(dto.id()), servicio.listarMios(estudiante.getId(), null).stream().map(ServicioDTO::id).toList());
        assertEquals(1, servicio.listarMios(tutor.getId(), EstadoServicio.CONFIRMADO).size());
        assertTrue(servicio.listarMios(tutor.getId(), EstadoServicio.FINALIZADO).isEmpty());
        assertTrue(servicio.listarMios(otroTutor.getId(), null).isEmpty());
        assertEquals("Ecuaciones diferenciales", servicio.listarMios(estudiante.getId(), null).get(0).tema());
    }

    // ---- Enlace e inicio ----

    @Test
    void elTutorFijaElEnlaceSoloEnConfirmado() {
        ServicioDTO dto = confirmar();

        ServicioDTO actualizado = servicio.actualizarEnlace(tutor.getId(), dto.id(),
                new EnlaceServicioDTO(" https://meet.example/abc ", null, null, null));
        assertEquals("https://meet.example/abc", actualizado.lugarOEnlace());

        servicio.iniciarSesion(estudiante.getId(), dto.id());
        assertNegocio("SERVICIO_NO_EDITABLE", HttpStatus.CONFLICT, () -> servicio.actualizarEnlace(tutor.getId(), dto.id(),
                new EnlaceServicioDTO("https://otro.example", null, null, null)));
    }

    @Test
    void precioDuracionYModalidadSonInmutables() {
        ServicioDTO dto = confirmar();

        assertNegocio("SERVICIO_INMUTABLE", HttpStatus.CONFLICT, () -> servicio.actualizarEnlace(tutor.getId(), dto.id(),
                new EnlaceServicioDTO("https://meet.example/abc", new BigDecimal("1000"), null, null)));
        assertNegocio("SERVICIO_INMUTABLE", HttpStatus.CONFLICT, () -> servicio.actualizarEnlace(tutor.getId(), dto.id(),
                new EnlaceServicioDTO("https://meet.example/abc", null, Duracion.MIN_30, null)));
        assertNegocio("SERVICIO_INMUTABLE", HttpStatus.CONFLICT, () -> servicio.actualizarEnlace(tutor.getId(), dto.id(),
                new EnlaceServicioDTO("https://meet.example/abc", null, null, Modalidad.PRESENCIAL)));
        assertEquals(0, new BigDecimal("30000").compareTo(e.servicios.get(dto.id()).getPrecioFinalCOP()));
    }

    @Test
    void soloElTutorDelServicioCambiaElEnlace() {
        ServicioDTO dto = confirmar();
        EnlaceServicioDTO enlace = new EnlaceServicioDTO("https://meet.example/abc", null, null, null);

        assertThrows(ExcepcionAutorizacion.class, () -> servicio.actualizarEnlace(estudiante.getId(), dto.id(), enlace));
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.actualizarEnlace(otroTutor.getId(), dto.id(), enlace));
        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.actualizarEnlace(tutor.getId(), "no-existe", enlace));
    }

    @Test
    void iniciarPasaAEnDesarrolloUnaSolaVezYSoloParticipantes() {
        ServicioDTO dto = confirmar();
        Usuario intruso = usuario("Otro", Rol.ESTUDIANTE);

        assertThrows(ExcepcionAutorizacion.class, () -> servicio.iniciarSesion(intruso.getId(), dto.id()));
        ServicioDTO iniciado = servicio.iniciarSesion(tutor.getId(), dto.id());
        assertEquals(EstadoServicio.EN_DESARROLLO, iniciado.estado());
        assertNotNull(iniciado.fechaInicio());
        assertNegocio("TRANSICION_INVALIDA", HttpStatus.CONFLICT, () -> servicio.iniciarSesion(estudiante.getId(), dto.id()));
        assertTrue(acciones().contains("SERVICIO_INICIADO"));
    }

    @Test
    void elServicioSinPagoMuestraPagoNulo() {
        ServicioDTO dto = confirmar();
        e.pagos.clear();

        assertNull(servicio.obtener(autenticado(estudiante), dto.id()).pago());
    }

    // ---- Datos ----

    private List<String> acciones() {
        return e.bitacora.stream().map(RegistroAuditoria::getAccion).toList();
    }

    private static UsuarioAutenticado autenticado(Usuario u) {
        return new UsuarioAutenticado(u.getId(), u.getCorreo(), u.getNombre(), u.getRoles());
    }

    private Usuario usuario(String nombre, Rol... roles) {
        Usuario u = new Usuario();
        u.setId(EscenarioNegociacion.nuevoId());
        u.setNombre(nombre);
        u.setCorreo(u.getId() + "@u.edu.co");
        u.setRoles(new HashSet<>(Set.of(roles)));
        u.setEstadoCuenta(EstadoCuenta.ACTIVA);
        if (u.getRoles().contains(Rol.TUTOR)) {
            PerfilTutor perfil = new PerfilTutor();
            perfil.setNivelVerificacion(NivelVerificacion.VERIFICADO);
            u.setPerfilTutor(perfil);
        }
        e.usuarios.put(u.getId(), u);
        return u;
    }

    private SolicitudTutoria solicitud(Usuario dueno, String precio) {
        SolicitudTutoria s = new SolicitudTutoria();
        s.setId(EscenarioNegociacion.nuevoId());
        s.setEstudianteId(dueno.getId());
        s.setTema("Ecuaciones diferenciales");
        s.setDescripcion("Requiero explicación del método de separación de variables con ejercicios");
        s.setTipoServicio(TipoServicio.EXPLICACION_TEMA);
        s.setFechaHora(Instant.now().plus(3, ChronoUnit.DAYS));
        s.setModalidad(Modalidad.VIRTUAL);
        s.setDuracion(Duracion.MIN_60);
        s.setPrecioInicialCOP(new BigDecimal(precio));
        e.solicitudes.put(s.getId(), s);
        return s;
    }

    /** Copia de la solicitud ya seleccionada, para fabricar un servicio «ajeno» sin tocar el estado real. */
    private SolicitudTutoria solicitudClon() {
        SolicitudTutoria c = new SolicitudTutoria();
        c.setId(solicitud.getId());
        c.setEstudianteId(solicitud.getEstudianteId());
        c.setFechaHora(solicitud.getFechaHora());
        c.setEstado(EstadoSolicitud.TUTOR_SELECCIONADO);
        return c;
    }
}
