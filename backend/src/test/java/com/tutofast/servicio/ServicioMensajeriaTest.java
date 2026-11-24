package com.tutofast.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.tutofast.ArchivosDePrueba;
import com.tutofast.EscenarioNegociacion;
import com.tutofast.dto.HiloMensajesDTO;
import com.tutofast.dto.HiloResumenDTO;
import com.tutofast.dto.MensajeDTO;
import com.tutofast.dto.MensajeEntradaDTO;
import com.tutofast.dto.MensajeReportadoDTO;
import com.tutofast.dto.ModeracionEntradaDTO;
import com.tutofast.dto.PropuestaEntradaDTO;
import com.tutofast.dto.ReporteMensajeEntradaDTO;
import com.tutofast.dto.SancionEntradaDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.mapper.MensajeMapper;
import com.tutofast.modelo.DecisionModeracion;
import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.EstadoReporte;
import com.tutofast.modelo.EstadoServicio;
import com.tutofast.modelo.EstadoSolicitud;
import com.tutofast.modelo.Mensaje;
import com.tutofast.modelo.MotivoReporteMensaje;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.SolicitudTutoria;
import com.tutofast.modelo.TipoPropuesta;
import com.tutofast.modelo.TipoReporte;
import com.tutofast.modelo.TipoSancion;
import com.tutofast.modelo.Usuario;
import com.tutofast.seguridad.UsuarioAutenticado;

import static com.tutofast.EscenarioNegociacion.autenticado;

class ServicioMensajeriaTest {

    private EscenarioNegociacion e;
    private ServicioMensajeria mensajeria;
    private Usuario carlos;
    private Usuario laura;
    private Usuario andres;
    private Usuario intruso;
    private Usuario otraEstudiante;
    private Usuario admin;
    private SolicitudTutoria solicitud;
    private String propuestaLaura;
    private String propuestaAndres;

    @BeforeEach
    void preparar() {
        e = new EscenarioNegociacion();
        mensajeria = e.servicioMensajeria;
        carlos = e.crearUsuario("Carlos Gómez", Rol.ESTUDIANTE);
        laura = e.crearUsuario("Laura Martínez", Rol.TUTOR);
        andres = e.crearUsuario("Andrés Pérez", Rol.TUTOR);
        intruso = e.crearUsuario("Tutor Sin Propuesta", Rol.TUTOR);
        otraEstudiante = e.crearUsuario("Otra Estudiante", Rol.ESTUDIANTE);
        admin = e.crearUsuario("Administrador", Rol.ADMINISTRADOR);
        solicitud = e.crearSolicitud(carlos, "40000");
        propuestaLaura = proponer(laura);
        propuestaAndres = proponer(andres);
    }

    private String proponer(Usuario tutor) {
        return e.servicioPropuestas.enviarPropuesta(tutor.getId(), solicitud.getId(),
                new PropuestaEntradaDTO(TipoPropuesta.ACEPTA_PRECIO, null, null, null, null)).id();
    }

    private MensajeDTO deCarlosA(Usuario tutor, String texto) {
        MensajeDTO dto = mensajeria.enviarMensaje(autenticado(carlos), solicitud.getId(),
                new MensajeEntradaDTO(tutor.getId(), texto), null);
        pausa();
        return dto;
    }

    private MensajeDTO deTutor(Usuario tutor, String texto) {
        MensajeDTO dto = mensajeria.enviarMensaje(autenticado(tutor), solicitud.getId(),
                new MensajeEntradaDTO(null, texto), null);
        pausa();
        return dto;
    }

    /** Separa las marcas temporales para que el orden por fecha no dependa de la resolución del reloj. */
    private static void pausa() {
        try {
            Thread.sleep(3);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private HiloMensajesDTO historial(Usuario lector, Usuario tutorDelHilo, Instant desde) {
        return mensajeria.consultarHistorial(autenticado(lector), solicitud.getId(), tutorDelHilo.getId(), desde);
    }

    private static void assertNegocio(String codigo, HttpStatus estado, Runnable accion) {
        ExcepcionNegocio ex = assertThrows(ExcepcionNegocio.class, accion::run);
        assertEquals(codigo, ex.getCodigo());
        assertEquals(estado, ex.getEstado());
    }

    private Mensaje almacenado(String id) {
        return e.mensajes.get(id);
    }

    /** El estudiante selecciona a Laura y confirma con pago aprobado; devuelve el id del servicio. */
    private String confirmarConLaura() {
        e.servicioPropuestas.aceptarPropuesta(carlos.getId(), propuestaLaura);
        return e.servicioNegociacion.confirmarServicio(carlos.getId(), solicitud.getId(), "tok_aprobar").id();
    }

    // ---------------------------------------------------------------- envío y lectura

    @Test
    void elEstudianteYElTutorConversanEnElMismoHilo() {
        MensajeDTO primero = deCarlosA(laura, "Hola Laura, ¿puedes explicarme separación de variables?");
        MensajeDTO respuesta = deTutor(laura, "Claro, ¿te sirve el jueves?");

        assertEquals(carlos.getId(), primero.emisorId());
        assertEquals("Carlos Gómez", primero.emisorNombre());
        assertFalse(primero.reportado());
        assertFalse(primero.sistema());
        assertEquals(laura.getId(), respuesta.emisorId());
        HiloMensajesDTO hilo = historial(carlos, laura, null);
        assertFalse(hilo.soloLectura());
        assertNull(hilo.motivoSoloLectura());
        assertEquals(List.of("Hola Laura, ¿puedes explicarme separación de variables?", "Claro, ¿te sirve el jueves?"),
                hilo.mensajes().stream().map(MensajeDTO::contenido).toList());
        assertEquals(hilo.mensajes().stream().map(MensajeDTO::id).toList(),
                historial(laura, laura, null).mensajes().stream().map(MensajeDTO::id).toList());
    }

    @Test
    void cadaTutorTieneSuPropioHiloAislado() {
        deCarlosA(laura, "Mensaje privado para Laura");
        deCarlosA(andres, "Mensaje privado para Andrés");

        assertEquals(1, historial(carlos, laura, null).mensajes().size());
        assertEquals("Mensaje privado para Andrés", historial(andres, andres, null).mensajes().get(0).contenido());
        // Andrés no puede leer el hilo de Laura aunque conozca su identificador.
        assertThrows(ExcepcionAutorizacion.class, () -> mensajeria.consultarHistorial(autenticado(andres),
                solicitud.getId(), laura.getId(), null));
    }

    @Test
    void unTerceroNoPuedeLeerNiEscribir() {
        // Otra estudiante: ni dueña ni tutora.
        assertThrows(ExcepcionAutorizacion.class, () -> mensajeria.consultarHistorial(autenticado(otraEstudiante),
                solicitud.getId(), laura.getId(), null));
        assertThrows(ExcepcionAutorizacion.class, () -> mensajeria.enviarMensaje(autenticado(otraEstudiante),
                solicitud.getId(), new MensajeEntradaDTO(laura.getId(), "hola"), null));
        // Tutor sin propuesta en la solicitud.
        assertThrows(ExcepcionAutorizacion.class, () -> mensajeria.consultarHistorial(autenticado(intruso),
                solicitud.getId(), null, null));
        assertThrows(ExcepcionAutorizacion.class, () -> mensajeria.enviarMensaje(autenticado(intruso),
                solicitud.getId(), new MensajeEntradaDTO(null, "hola"), null));
        // Un tutor tampoco escribe en el hilo de otro tutor indicando su tutorId.
        assertThrows(ExcepcionAutorizacion.class, () -> mensajeria.enviarMensaje(autenticado(andres),
                solicitud.getId(), new MensajeEntradaDTO(laura.getId(), "hola"), null));
        assertTrue(e.mensajes.isEmpty());
    }

    @Test
    void elAdministradorSoloLee() {
        deCarlosA(laura, "Hola Laura");

        HiloMensajesDTO hilo = historial(admin, laura, null);

        assertTrue(hilo.soloLectura());
        assertEquals(1, hilo.mensajes().size());
        assertThrows(ExcepcionAutorizacion.class, () -> mensajeria.enviarMensaje(autenticado(admin), solicitud.getId(),
                new MensajeEntradaDTO(laura.getId(), "intervención"), null));
        assertFalse(almacenado(hilo.mensajes().get(0).id()).isLeido(), "leer como administrador no marca nada");
    }

    @Test
    void elEstudianteYElAdministradorDebenIndicarElTutor() {
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> mensajeria.consultarHistorial(autenticado(carlos),
                solicitud.getId(), null, null));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> mensajeria.consultarHistorial(autenticado(admin),
                solicitud.getId(), " ", null));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> mensajeria.enviarMensaje(autenticado(carlos),
                solicitud.getId(), new MensajeEntradaDTO(null, "hola"), null));
    }

    @Test
    void unTutorSinHiloEnLaSolicitudDa404ParaElEstudiante() {
        assertThrows(ExcepcionNoEncontrado.class, () -> mensajeria.consultarHistorial(autenticado(carlos),
                solicitud.getId(), intruso.getId(), null));
        assertThrows(ExcepcionNoEncontrado.class, () -> mensajeria.enviarMensaje(autenticado(carlos),
                solicitud.getId(), new MensajeEntradaDTO(intruso.getId(), "hola"), null));
        assertThrows(ExcepcionNoEncontrado.class, () -> mensajeria.consultarHistorial(autenticado(carlos),
                "000000000000000000000000", laura.getId(), null));
    }

    @Test
    void rechazaMensajesVaciosYDemasiadoLargos() {
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> deCarlosA(laura, "   "));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> deCarlosA(laura, null));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST,
                () -> deCarlosA(laura, "a".repeat(ServicioMensajeria.MAXIMO_CARACTERES + 1)));
        assertEquals(ServicioMensajeria.MAXIMO_CARACTERES,
                deCarlosA(laura, "a".repeat(ServicioMensajeria.MAXIMO_CARACTERES)).contenido().length());
    }

    @Test
    void unaCuentaSuspendidaNoPuedeEscribir() {
        carlos.setEstadoCuenta(EstadoCuenta.SUSPENDIDA);

        assertThrows(ExcepcionAutorizacion.class, () -> deCarlosA(laura, "hola"));
    }

    // ---------------------------------------------------------------- filtro de contacto externo

    @Test
    void bloqueaTelefonosCorreosYEvasionDePlataformaConAuditoria() {
        for (String texto : List.of("Mi número es 300 123 4567", "escríbeme a laura@gmail.com",
                "mejor hablemos por WhatsApp", "pagame por fuera de la plataforma")) {
            ExcepcionNegocio ex = assertThrows(ExcepcionNegocio.class, () -> deCarlosA(laura, texto));
            assertEquals("CONTACTO_EXTERNO", ex.getCodigo());
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getEstado());
        }

        assertTrue(e.mensajes.isEmpty(), "ningún mensaje bloqueado se guarda");
        assertEquals(4, e.acciones().stream().filter("MENSAJE_BLOQUEADO"::equals).count());
        e.bitacora.forEach(r -> assertFalse(r.getDetalle().toString().contains("300 123"),
                "la auditoría no copia el contenido bloqueado"));
    }

    @Test
    void permiteElEnlaceDeUnaVideollamada() {
        MensajeDTO dto = deTutor(laura, "Entra a https://meet.google.com/abc-defg-hij a las 7");

        assertNotNull(dto.id());
    }

    // ---------------------------------------------------------------- adjuntos

    @Test
    void adjuntaUnPdfYLoGuardaEnElAlmacenamiento() {
        MensajeDTO dto = mensajeria.enviarMensaje(autenticado(laura), solicitud.getId(),
                new MensajeEntradaDTO(null, "Te comparto la guía"), ArchivosDePrueba.pdf());

        assertNotNull(dto.archivo());
        assertTrue(e.almacenamiento.contiene(dto.archivo().archivoId()));
        assertEquals(dto.archivo().archivoId(), almacenado(dto.id()).getArchivo().getGridFsId());
    }

    @Test
    void unMensajeSoloConArchivoEsValido() {
        MensajeDTO dto = mensajeria.enviarMensaje(autenticado(laura), solicitud.getId(),
                new MensajeEntradaDTO(null, null), ArchivosDePrueba.png());

        assertNull(dto.contenido());
        assertNotNull(dto.archivo());
    }

    @Test
    void rechazaUnArchivoNoPermitidoConTipoNoPermitido() {
        var bat = ArchivosDePrueba.archivo("virus.bat", "application/octet-stream", "echo hola".getBytes());

        ExcepcionNegocio ex = assertThrows(ExcepcionNegocio.class, () -> mensajeria.enviarMensaje(autenticado(laura),
                solicitud.getId(), new MensajeEntradaDTO(null, "mira"), bat));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getEstado());
        assertEquals(0, e.almacenamiento.cantidad());
        assertTrue(e.mensajes.isEmpty());
    }

    @Test
    void rechazaUnArchivoDemasiadoGrande() {
        ExcepcionNegocio ex = assertThrows(ExcepcionNegocio.class, () -> mensajeria.enviarMensaje(autenticado(laura),
                solicitud.getId(), new MensajeEntradaDTO(null, "mira"), ArchivosDePrueba.pdfDeTamano(11L * 1024 * 1024)));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.getEstado());
        assertEquals(0, e.almacenamiento.cantidad());
    }

    @Test
    void siFallaElGuardadoDelMensajeSeBorraElArchivoSubido() {
        when(e.repoMensajes.save(any(Mensaje.class))).thenThrow(new RuntimeException("Mongo caído"));

        assertThrows(RuntimeException.class, () -> mensajeria.enviarMensaje(autenticado(laura), solicitud.getId(),
                new MensajeEntradaDTO(null, "con adjunto"), ArchivosDePrueba.pdf()));

        assertEquals(0, e.almacenamiento.cantidad(), "el archivo huérfano se compensa");
    }

    @Test
    void elFiltroSeAplicaAntesDeSubirElArchivo() {
        assertThrows(ExcepcionNegocio.class, () -> mensajeria.enviarMensaje(autenticado(laura), solicitud.getId(),
                new MensajeEntradaDTO(null, "mi whatsapp es otro"), ArchivosDePrueba.pdf()));

        assertEquals(0, e.almacenamiento.cantidad());
    }

    // ---------------------------------------------------------------- polling

    @Test
    void desdeDevuelveSoloLosMensajesNuevos() {
        MensajeDTO a = deCarlosA(laura, "uno");
        Instant corte = a.marcaTemporal();
        e.mensajes.get(a.id()).setMarcaTemporal(corte.minusSeconds(10));
        MensajeDTO b = deTutor(laura, "dos");
        MensajeDTO c = deCarlosA(laura, "tres");

        List<String> nuevos = historial(laura, laura, corte.minusSeconds(5)).mensajes().stream()
                .map(MensajeDTO::contenido).toList();

        assertEquals(List.of("dos", "tres"), nuevos);
        assertTrue(historial(laura, laura, c.marcaTemporal().plusSeconds(1)).mensajes().isEmpty());
        assertNotNull(b.id());
    }

    @Test
    void laCargaInicialDevuelveLosUltimosCienEnOrdenAscendente() {
        Instant base = Instant.now().minusSeconds(1000);
        for (int i = 1; i <= 105; i++) {
            e.repoMensajes.save(Mensaje.nuevo(solicitud.getId(), laura.getId(), carlos.getId(), "m" + i, null,
                    base.plusSeconds(i)));
        }

        List<MensajeDTO> lista = historial(laura, laura, null).mensajes();

        assertEquals(ServicioMensajeria.MAXIMO_POR_RESPUESTA, lista.size());
        assertEquals("m6", lista.get(0).contenido());
        assertEquals("m105", lista.get(99).contenido());
    }

    @Test
    void elPollingTambienEstaLimitadoACien() {
        Instant base = Instant.now().minusSeconds(1000);
        for (int i = 1; i <= 130; i++) {
            e.repoMensajes.save(Mensaje.nuevo(solicitud.getId(), laura.getId(), carlos.getId(), "m" + i, null,
                    base.plusSeconds(i)));
        }

        List<MensajeDTO> lista = historial(laura, laura, base).mensajes();

        assertEquals(100, lista.size());
        assertEquals("m1", lista.get(0).contenido(), "continúa desde el más antiguo pendiente");
    }

    // ---------------------------------------------------------------- hilos y no leídos

    @Test
    void listaUnHiloPorTutorConSusNoLeidosYElUltimoMensaje() {
        deTutor(laura, "Primera respuesta");
        deTutor(laura, "Segunda respuesta");
        deCarlosA(andres, "Hola Andrés");

        List<HiloResumenDTO> hilos = mensajeria.listarHilos(autenticado(carlos), solicitud.getId());

        assertEquals(2, hilos.size());
        HiloResumenDTO deLaura = hilos.stream().filter(h -> h.tutorId().equals(laura.getId())).findFirst().orElseThrow();
        HiloResumenDTO deAndres = hilos.stream().filter(h -> h.tutorId().equals(andres.getId())).findFirst().orElseThrow();
        assertEquals("Laura Martínez", deLaura.tutorNombre());
        assertEquals(2, deLaura.noLeidos());
        assertEquals("Segunda respuesta", deLaura.ultimoMensaje());
        assertEquals(0, deAndres.noLeidos(), "el último mensaje lo escribió el propio estudiante");
        assertFalse(deLaura.soloLectura());

        historial(carlos, laura, null); // el estudiante abre el hilo de Laura

        assertEquals(0, mensajeria.listarHilos(autenticado(carlos), solicitud.getId()).stream()
                .filter(h -> h.tutorId().equals(laura.getId())).findFirst().orElseThrow().noLeidos());
    }

    @Test
    void unTutorRecibeSusNoLeidosPeroElQueEscribioNo() {
        MensajeDTO enviado = deCarlosA(laura, "¿Puedes el jueves?");

        assertFalse(almacenado(enviado.id()).isLeido());
        historial(carlos, laura, null);
        assertFalse(almacenado(enviado.id()).isLeido(), "leer tus propios mensajes no los marca");
        historial(laura, laura, null);
        assertTrue(almacenado(enviado.id()).isLeido());
    }

    @Test
    void soloElDuenoYElAdministradorListanLosHilos() {
        assertThrows(ExcepcionAutorizacion.class, () -> mensajeria.listarHilos(autenticado(laura), solicitud.getId()));
        assertThrows(ExcepcionAutorizacion.class, () -> mensajeria.listarHilos(autenticado(otraEstudiante), solicitud.getId()));
        assertEquals(2, mensajeria.listarHilos(autenticado(admin), solicitud.getId()).size());
        assertTrue(mensajeria.listarHilos(autenticado(admin), solicitud.getId()).stream().allMatch(HiloResumenDTO::soloLectura));
        assertThrows(ExcepcionNoEncontrado.class, () -> mensajeria.listarHilos(autenticado(carlos), "000000000000000000000000"));
    }

    // ---------------------------------------------------------------- bloqueo de escritura

    @Test
    void unaPropuestaRetiradaDejaElHiloEnSoloLectura() {
        deCarlosA(laura, "Hola");
        e.servicioPropuestas.retirarPropuesta(laura.getId(), propuestaLaura);

        HiloMensajesDTO hilo = historial(carlos, laura, null);

        assertTrue(hilo.soloLectura());
        assertTrue(hilo.motivoSoloLectura().contains("descartada o retirada"));
        assertEquals(1, hilo.mensajes().size(), "el historial sigue visible");
        assertThrows(ExcepcionAutorizacion.class, () -> deCarlosA(laura, "¿sigues ahí?"));
        assertThrows(ExcepcionAutorizacion.class, () -> deTutor(laura, "hola"));
    }

    @Test
    void unaPropuestaDescartadaPorElEstudianteCierraElHilo() {
        e.servicioPropuestas.rechazarPropuesta(carlos.getId(), propuestaAndres, "muy caro");

        assertTrue(historial(carlos, andres, null).soloLectura());
        assertThrows(ExcepcionAutorizacion.class, () -> deCarlosA(andres, "hola"));
        assertFalse(historial(carlos, laura, null).soloLectura());
    }

    @Test
    void siElTutorVuelveAProponerElHiloSeReabre() {
        e.servicioPropuestas.retirarPropuesta(laura.getId(), propuestaLaura);
        assertTrue(historial(carlos, laura, null).soloLectura());

        proponer(laura);

        assertFalse(historial(carlos, laura, null).soloLectura());
        assertNotNull(deCarlosA(laura, "Qué bueno que volviste").id());
    }

    @Test
    void unaSolicitudCanceladaBloqueaTodosLosHilos() {
        deCarlosA(laura, "Hola");
        solicitud.setEstado(EstadoSolicitud.CANCELADA);

        assertTrue(historial(carlos, laura, null).soloLectura());
        assertTrue(historial(laura, laura, null).motivoSoloLectura().contains("cancelada"));
        assertThrows(ExcepcionAutorizacion.class, () -> deCarlosA(laura, "hola"));
        assertThrows(ExcepcionAutorizacion.class, () -> deTutor(laura, "hola"));
    }

    @Test
    void alConfirmarElServicioSeCierranLosHilosDeLosTutoresNoElegidos() {
        deCarlosA(laura, "Hola Laura");
        deCarlosA(andres, "Hola Andrés");
        deTutor(andres, "Hola Carlos");
        Usuario sinChat = e.crearUsuario("Tutor Sin Chat", Rol.TUTOR);
        proponer(sinChat);

        confirmarConLaura();

        HiloMensajesDTO deAndres = historial(carlos, andres, null);
        assertTrue(deAndres.soloLectura());
        assertEquals(3, deAndres.mensajes().size());
        MensajeDTO aviso = deAndres.mensajes().get(2);
        assertTrue(aviso.sistema());
        assertEquals("TutoFast", aviso.emisorNombre());
        assertTrue(aviso.contenido().contains("otro tutor"));
        assertNull(aviso.emisorId());
        assertThrows(ExcepcionAutorizacion.class, () -> deCarlosA(andres, "¿y entonces?"));
        assertThrows(ExcepcionAutorizacion.class, () -> deTutor(andres, "lo siento"));
        // El hilo del tutor elegido sigue abierto y el del tutor sin conversación no recibe aviso.
        assertFalse(historial(carlos, laura, null).soloLectura());
        assertNotNull(deTutor(laura, "Nos vemos en la sesión").id());
        assertTrue(historial(carlos, sinChat, null).mensajes().isEmpty());
        assertEquals(1, e.mensajes.values().stream().filter(Mensaje::esDeSistema).count());
        assertTrue(e.acciones().contains("HILO_CERRADO"));
    }

    @Test
    void unFalloAlCerrarLosHilosNoRevierteLaConfirmacion() {
        deCarlosA(andres, "Hola Andrés");
        when(e.repoMensajes.save(any(Mensaje.class))).thenThrow(new RuntimeException("Mongo caído"));

        String servicioId = confirmarConLaura();

        assertEquals(EstadoServicio.CONFIRMADO, e.servicios.get(servicioId).getEstado());
    }

    @Test
    void cuandoElServicioConcluyeElHiloQuedaDeSoloLectura() {
        String servicioId = confirmarConLaura();
        assertFalse(historial(carlos, laura, null).soloLectura());

        e.servicios.get(servicioId).setEstado(EstadoServicio.FINALIZADO);

        HiloMensajesDTO hilo = historial(carlos, laura, null);
        assertTrue(hilo.soloLectura());
        assertTrue(hilo.motivoSoloLectura().contains("concluyó"));
        assertThrows(ExcepcionAutorizacion.class, () -> deCarlosA(laura, "gracias"));

        e.servicios.get(servicioId).setEstado(EstadoServicio.CANCELADO);
        assertTrue(historial(laura, laura, null).soloLectura());
    }

    @Test
    void mientrasElServicioEstaEnDesarrolloElChatSigueAbierto() {
        String servicioId = confirmarConLaura();
        e.servicios.get(servicioId).setEstado(EstadoServicio.EN_DESARROLLO);

        assertNotNull(deCarlosA(laura, "¿Compartes pantalla?").id());
    }

    // ---------------------------------------------------------------- notificaciones

    @Test
    void avisaAlReceptorSoloEnElPrimerMensajeDeUnaRachaYCuandoCambiaElEmisor() {
        deCarlosA(laura, "uno");
        deCarlosA(laura, "dos");
        deCarlosA(laura, "tres");

        verify(e.notificaciones, times(1)).notificar(eq(laura.getId()), eq(TipoNotificacion.NUEVO_MENSAJE), anyString(),
                contains("Carlos Gómez"), contains("solicitudes.html?id=" + solicitud.getId()));

        deTutor(laura, "respuesta");

        verify(e.notificaciones).notificar(eq(carlos.getId()), eq(TipoNotificacion.NUEVO_MENSAJE), anyString(),
                contains("Laura Martínez"), contains("tutorId=" + laura.getId()));
    }

    @Test
    void despuesDeUnaPausaLargaVuelveAAvisar() {
        MensajeDTO primero = deCarlosA(laura, "uno");
        e.mensajes.get(primero.id()).setMarcaTemporal(Instant.now().minus(Duration.ofMinutes(30)));

        deCarlosA(laura, "dos");

        verify(e.notificaciones, times(2)).notificar(eq(laura.getId()), eq(TipoNotificacion.NUEVO_MENSAJE), anyString(),
                anyString(), anyString());
    }

    @Test
    void unFalloAlNotificarNoImpideElEnvio() {
        doThrow(new RuntimeException("sin conexión")).when(e.notificaciones)
                .notificar(anyString(), any(TipoNotificacion.class), anyString(), anyString(), anyString());

        assertNotNull(deCarlosA(laura, "hola").id());
        assertEquals(1, e.mensajes.size());
    }

    // ---------------------------------------------------------------- reportar mensajes

    @Test
    void elReceptorReportaUnMensajeSinAlterarSuContenido() {
        MensajeDTO ofensivo = deTutor(laura, "Eres un inútil");

        MensajeDTO reportado = mensajeria.reportarMensaje(autenticado(carlos), ofensivo.id(),
                new ReporteMensajeEntradaDTO(MotivoReporteMensaje.CONDUCTA_ABUSIVA, "Me insultó"));

        assertTrue(reportado.reportado());
        assertEquals("Eres un inútil", reportado.contenido());
        Mensaje guardado = almacenado(ofensivo.id());
        assertEquals("CONDUCTA_ABUSIVA", guardado.getMotivoReporte());
        assertEquals("Me insultó", guardado.getDetalleReporte());
        assertEquals(carlos.getId(), guardado.getReportadoPorId());
        assertNotNull(guardado.getFechaReporte());
        assertEquals("Eres un inútil", guardado.getContenido());
        assertTrue(e.acciones().contains("REPORTAR_MENSAJE"));
        verify(e.notificaciones).notificar(eq(admin.getId()), eq(TipoNotificacion.REPORTE_RECIBIDO), anyString(),
                contains("Carlos Gómez"), anyString());
    }

    @Test
    void tambienElTutorPuedeReportarUnMensajeDelEstudiante() {
        MensajeDTO dto = deCarlosA(laura, "Hola, necesito ayuda");

        assertTrue(mensajeria.reportarMensaje(autenticado(laura), dto.id(),
                new ReporteMensajeEntradaDTO(MotivoReporteMensaje.SPAM, null)).reportado());
    }

    @Test
    void noSePuedeReportarUnMensajePropioNiUnoAjeno() {
        MensajeDTO dto = deTutor(laura, "hola");
        var reporte = new ReporteMensajeEntradaDTO(MotivoReporteMensaje.OTRO, null);

        assertThrows(ExcepcionAutorizacion.class, () -> mensajeria.reportarMensaje(autenticado(laura), dto.id(), reporte));
        assertThrows(ExcepcionAutorizacion.class, () -> mensajeria.reportarMensaje(autenticado(andres), dto.id(), reporte));
        assertThrows(ExcepcionAutorizacion.class, () -> mensajeria.reportarMensaje(autenticado(otraEstudiante), dto.id(), reporte));
        assertThrows(ExcepcionAutorizacion.class, () -> mensajeria.reportarMensaje(autenticado(admin), dto.id(), reporte));
        assertFalse(almacenado(dto.id()).isReportado());
    }

    @Test
    void unMensajeSoloSeReportaUnaVezYUnoInexistenteDa404() {
        MensajeDTO dto = deTutor(laura, "hola");
        var reporte = new ReporteMensajeEntradaDTO(MotivoReporteMensaje.ACOSO, null);
        mensajeria.reportarMensaje(autenticado(carlos), dto.id(), reporte);

        assertNegocio("MENSAJE_YA_REPORTADO", HttpStatus.CONFLICT,
                () -> mensajeria.reportarMensaje(autenticado(carlos), dto.id(), reporte));
        assertThrows(ExcepcionNoEncontrado.class,
                () -> mensajeria.reportarMensaje(autenticado(carlos), "000000000000000000000000", reporte));
    }

    @Test
    void losAvisosDelSistemaNoSeReportan() {
        deCarlosA(andres, "Hola Andrés");
        confirmarConLaura();
        Mensaje aviso = e.mensajes.values().stream().filter(Mensaje::esDeSistema).findFirst().orElseThrow();

        assertThrows(ExcepcionNegocio.class, () -> mensajeria.reportarMensaje(autenticado(carlos), aviso.getId(),
                new ReporteMensajeEntradaDTO(MotivoReporteMensaje.OTRO, null)));
    }

    @Test
    void sePuedeReportarEnUnHiloDeSoloLectura() {
        MensajeDTO dto = deTutor(andres, "mensaje previo");
        e.servicioPropuestas.rechazarPropuesta(carlos.getId(), propuestaAndres, null);

        assertTrue(mensajeria.reportarMensaje(autenticado(carlos), dto.id(),
                new ReporteMensajeEntradaDTO(MotivoReporteMensaje.CONTACTO_EXTERNO, null)).reportado());
    }

    // ---------------------------------------------------------------- moderación

    private MensajeDTO reportado(MotivoReporteMensaje motivo) {
        MensajeDTO dto = deTutor(laura, "Contenido discutible");
        mensajeria.reportarMensaje(autenticado(carlos), dto.id(), new ReporteMensajeEntradaDTO(motivo, "detalle"));
        return dto;
    }

    @Test
    void laColaMuestraElMensajeReportadoConSuContexto() {
        MensajeDTO dto = reportado(MotivoReporteMensaje.ACOSO);
        deTutor(laura, "Otro mensaje sin reportar");

        List<MensajeReportadoDTO> cola = mensajeria.listarReportados(false);

        assertEquals(1, cola.size());
        MensajeReportadoDTO item = cola.get(0);
        assertEquals(dto.id(), item.mensaje().id());
        assertEquals(solicitud.getId(), item.solicitudId());
        assertEquals(laura.getId(), item.tutorId());
        assertEquals("Laura Martínez", item.tutorNombre());
        assertEquals("ACOSO", item.motivoReporte());
        assertEquals("detalle", item.detalleReporte());
        assertEquals("Carlos Gómez", item.reportante().nombre());
        assertEquals("Contenido discutible", item.mensaje().contenido());
        assertNull(item.moderacion());
    }

    @Test
    void desestimarDejaElMensajeIntactoYLoSacaDeLaCola() {
        MensajeDTO dto = reportado(MotivoReporteMensaje.OTRO);

        MensajeReportadoDTO resultado = mensajeria.moderarMensaje(admin.getId(), dto.id(),
                new ModeracionEntradaDTO(DecisionModeracion.DESESTIMAR, "No hay infracción", null));

        assertEquals(DecisionModeracion.DESESTIMAR, resultado.moderacion().decision());
        assertEquals(admin.getId(), resultado.moderacion().moderadoPor());
        assertEquals("Contenido discutible", historial(carlos, laura, null).mensajes().get(0).contenido());
        assertTrue(mensajeria.listarReportados(false).isEmpty());
        assertEquals(1, mensajeria.listarReportados(true).size());
        assertTrue(e.sanciones.isEmpty());
        assertTrue(e.reportes.isEmpty());
        assertTrue(e.acciones().contains("MODERAR_MENSAJE"));
        verify(e.notificaciones).notificar(eq(carlos.getId()), eq(TipoNotificacion.REPORTE_RESUELTO), anyString(),
                contains("no encontramos"), anyString());
    }

    @Test
    void confirmarLaInfraccionRetiraElContenidoParaLosParticipantesPeroNoParaElAdministrador() {
        MensajeDTO dto = reportado(MotivoReporteMensaje.CONDUCTA_ABUSIVA);

        mensajeria.moderarMensaje(admin.getId(), dto.id(),
                new ModeracionEntradaDTO(DecisionModeracion.CONFIRMAR_INFRACCION, "Lenguaje ofensivo", null));

        MensajeDTO paraElEstudiante = historial(carlos, laura, null).mensajes().get(0);
        assertEquals(MensajeMapper.TEXTO_RETIRADO, paraElEstudiante.contenido());
        assertTrue(paraElEstudiante.retirado());
        assertEquals(MensajeMapper.TEXTO_RETIRADO, historial(laura, laura, null).mensajes().get(0).contenido());
        assertEquals("Contenido discutible", historial(admin, laura, null).mensajes().get(0).contenido());
        assertEquals("Contenido discutible", almacenado(dto.id()).getContenido(), "el original se conserva");
        assertEquals(MensajeMapper.TEXTO_RETIRADO,
                mensajeria.listarHilos(autenticado(carlos), solicitud.getId()).stream()
                        .filter(h -> h.tutorId().equals(laura.getId())).findFirst().orElseThrow().ultimoMensaje());
        // Sin sanción: se avisa a quien escribió el mensaje.
        verify(e.notificaciones).notificar(eq(laura.getId()), eq(TipoNotificacion.REPORTE_RECIBIDO), anyString(),
                contains("retirado"), anyString());
        assertTrue(e.sanciones.isEmpty());
    }

    @Test
    void unMensajeRetiradoOcultaTambienSuAdjunto() {
        MensajeDTO dto = mensajeria.enviarMensaje(autenticado(laura), solicitud.getId(),
                new MensajeEntradaDTO(null, "mira"), ArchivosDePrueba.pdf());
        mensajeria.reportarMensaje(autenticado(carlos), dto.id(),
                new ReporteMensajeEntradaDTO(MotivoReporteMensaje.FRAUDE_ACADEMICO, null));
        mensajeria.moderarMensaje(admin.getId(), dto.id(),
                new ModeracionEntradaDTO(DecisionModeracion.CONFIRMAR_INFRACCION, null, null));

        assertNull(historial(carlos, laura, null).mensajes().get(0).archivo());
        assertNotNull(historial(admin, laura, null).mensajes().get(0).archivo());
    }

    @Test
    void confirmarConSuspensionRadicaElReporteSancionaYSuspendeLaCuenta() {
        MensajeDTO dto = reportado(MotivoReporteMensaje.CONTACTO_EXTERNO);
        Instant fin = Instant.now().plus(Duration.ofDays(7));

        MensajeReportadoDTO resultado = mensajeria.moderarMensaje(admin.getId(), dto.id(),
                new ModeracionEntradaDTO(DecisionModeracion.CONFIRMAR_INFRACCION, "Pidió pago por fuera",
                        new SancionEntradaDTO(TipoSancion.SUSPENSION_TEMPORAL, null, fin)));

        assertEquals(EstadoCuenta.SUSPENDIDA, laura.getEstadoCuenta());
        assertEquals(1, e.sanciones.size());
        var sancion = e.sanciones.values().iterator().next();
        assertEquals(laura.getId(), sancion.getUsuarioId());
        assertEquals(TipoSancion.SUSPENSION_TEMPORAL, sancion.getTipo());
        assertEquals(fin, sancion.getVigencia().getFin());
        assertEquals(sancion.getId(), resultado.moderacion().sancionId());
        // El reporte que respalda la sanción queda resuelto a favor de quien reportó.
        var reporte = e.reportes.get(sancion.getReporteId());
        assertEquals(EstadoReporte.RESUELTO, reporte.getEstado());
        assertEquals(TipoReporte.COBRO_EXTERNO, reporte.getTipo());
        assertEquals(carlos.getId(), reporte.getReportanteId());
        assertEquals(laura.getId(), reporte.getDenunciadoId());
        assertTrue(e.acciones().containsAll(List.of("MODERAR_MENSAJE", "SANCION_APLICADA")));
        verify(e.notificaciones).notificar(eq(laura.getId()), eq(TipoNotificacion.SANCION_APLICADA), anyString(),
                contains("suspendida hasta"), anyString());
    }

    @Test
    void unaAdvertenciaNoCambiaElEstadoDeLaCuentaYUnBloqueoLaBloquea() {
        MensajeDTO primero = reportado(MotivoReporteMensaje.ACOSO);
        mensajeria.moderarMensaje(admin.getId(), primero.id(), new ModeracionEntradaDTO(
                DecisionModeracion.CONFIRMAR_INFRACCION, null, new SancionEntradaDTO(TipoSancion.ADVERTENCIA, null, null)));
        assertEquals(EstadoCuenta.ACTIVA, laura.getEstadoCuenta());

        MensajeDTO segundo = reportado(MotivoReporteMensaje.ACOSO);
        mensajeria.moderarMensaje(admin.getId(), segundo.id(), new ModeracionEntradaDTO(
                DecisionModeracion.CONFIRMAR_INFRACCION, "Reincidencia", new SancionEntradaDTO(TipoSancion.BLOQUEO_PERMANENTE, null, null)));

        assertEquals(EstadoCuenta.BLOQUEADA, laura.getEstadoCuenta());
        assertEquals(2, e.sanciones.size());
    }

    @Test
    void unReporteSeResuelveUnaSolaVezYSoloSiEstaReportado() {
        MensajeDTO dto = reportado(MotivoReporteMensaje.OTRO);
        var desestimar = new ModeracionEntradaDTO(DecisionModeracion.DESESTIMAR, null, null);
        mensajeria.moderarMensaje(admin.getId(), dto.id(), desestimar);

        assertNegocio("MENSAJE_YA_MODERADO", HttpStatus.CONFLICT, () -> mensajeria.moderarMensaje(admin.getId(), dto.id(), desestimar));
        MensajeDTO sinReporte = deTutor(laura, "otro");
        assertNegocio("MENSAJE_NO_REPORTADO", HttpStatus.CONFLICT, () -> mensajeria.moderarMensaje(admin.getId(), sinReporte.id(), desestimar));
        assertThrows(ExcepcionNoEncontrado.class, () -> mensajeria.moderarMensaje(admin.getId(), "000000000000000000000000", desestimar));
    }

    @Test
    void soloSePuedeSancionarSiSeConfirmaLaInfraccion() {
        MensajeDTO dto = reportado(MotivoReporteMensaje.OTRO);

        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> mensajeria.moderarMensaje(admin.getId(), dto.id(),
                new ModeracionEntradaDTO(DecisionModeracion.DESESTIMAR, null,
                        new SancionEntradaDTO(TipoSancion.ADVERTENCIA, null, null))));

        assertNull(almacenado(dto.id()).getModeracion(), "no queda resuelto a medias");
    }

    @Test
    void unaSancionInvalidaNoDejaElMensajeModeradoNiExpedientesHuerfanos() {
        MensajeDTO dto = reportado(MotivoReporteMensaje.OTRO);

        // Suspensión sin fecha de fin, con fin en el pasado y con inicio futuro.
        for (SancionEntradaDTO invalida : List.of(
                new SancionEntradaDTO(TipoSancion.SUSPENSION_TEMPORAL, null, null),
                new SancionEntradaDTO(TipoSancion.SUSPENSION_TEMPORAL, null, Instant.now().minusSeconds(60)),
                new SancionEntradaDTO(TipoSancion.ADVERTENCIA, Instant.now().plus(Duration.ofDays(2)), null))) {
            assertNegocio("REGLA_NEGOCIO", HttpStatus.UNPROCESSABLE_ENTITY, () -> mensajeria.moderarMensaje(
                    admin.getId(), dto.id(), new ModeracionEntradaDTO(DecisionModeracion.CONFIRMAR_INFRACCION, null, invalida)));
        }

        assertTrue(e.sanciones.isEmpty());
        assertTrue(e.reportes.isEmpty());
        assertEquals(EstadoCuenta.ACTIVA, laura.getEstadoCuenta());
        assertEquals(1, mensajeria.listarReportados(false).size(), "sigue pendiente");
    }

    @Test
    void siFallaElGuardadoDeLaCuentaSeCompensaLaSancionYElReporte() {
        MensajeDTO dto = reportado(MotivoReporteMensaje.OTRO);
        when(e.repoUsuarios.save(any(Usuario.class))).thenThrow(new RuntimeException("Mongo caído"));

        assertThrows(RuntimeException.class, () -> mensajeria.moderarMensaje(admin.getId(), dto.id(),
                new ModeracionEntradaDTO(DecisionModeracion.CONFIRMAR_INFRACCION, null,
                        new SancionEntradaDTO(TipoSancion.BLOQUEO_PERMANENTE, null, null))));

        assertTrue(e.sanciones.isEmpty());
        assertTrue(e.reportes.isEmpty());
        assertNull(almacenado(dto.id()).getModeracion(), "el mensaje no queda resuelto a medias");
        assertEquals(1, mensajeria.listarReportados(false).size());
    }
}
