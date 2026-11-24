package com.tutofast.controlador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tutofast.ArchivosDePrueba;
import com.tutofast.EscenarioNegociacion;
import com.tutofast.RepositorioUsuarioEnMemoria;
import com.tutofast.dto.InicioSesionDTO;
import com.tutofast.dto.RegistroDTO;
import com.tutofast.excepcion.ManejadorGlobalExcepciones;
import com.tutofast.modelo.Duracion;
import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.EstadoServicio;
import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.SolicitudTutoria;
import com.tutofast.modelo.TipoServicio;
import com.tutofast.modelo.Usuario;
import com.tutofast.seguridad.FiltroAutenticacionJwt;
import com.tutofast.seguridad.InterceptorRoles;
import com.tutofast.seguridad.JwtUtil;
import com.tutofast.seguridad.ResolvedorUsuarioAutenticado;
import com.tutofast.servicio.ServicioArchivos;
import com.tutofast.servicio.ServicioIdentidad;
import com.tutofast.servicio.ServicioNotificaciones;

/**
 * Mensajería, moderación, finalización, cierre automático y calificaciones a través del filtro JWT, los roles y el
 * manejador global de errores (con repositorios en memoria).
 */
class MensajeriaYCierreControladorTest {

    private static final String SIN_ID = "000000000000000000000000";

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Map<String, Usuario> usuarios = new LinkedHashMap<>();
    private final Map<String, SolicitudTutoria> solicitudes = new LinkedHashMap<>();
    private EscenarioNegociacion escenario;
    private ServicioIdentidad identidad;
    private MockMvc mvc;
    private String tokenEstudiante;
    private String tokenTutor;
    private String tokenOtroTutor;
    private String tokenOtroEstudiante;
    private String tokenAdmin;
    private String estudianteId;
    private String tutorId;
    private String solicitudId;

    @BeforeEach
    void preparar() throws Exception {
        var repoUsuarios = RepositorioUsuarioEnMemoria.crear(usuarios);
        escenario = new EscenarioNegociacion(repoUsuarios, usuarios, solicitudes);
        identidad = new ServicioIdentidad(repoUsuarios, new BCryptPasswordEncoder(4),
                new JwtUtil("secreto-de-prueba-con-mas-de-32-caracteres-ok", Duration.ofHours(8)), escenario.auditoria,
                new ServicioArchivos(new com.tutofast.AlmacenamientoEnMemoria(), List.of()), mock(ServicioNotificaciones.class),
                mock(com.tutofast.repositorio.RepositorioAsignatura.class),
                mock(com.tutofast.repositorio.RepositorioCalificacion.class));

        mvc = MockMvcBuilders.standaloneSetup(new MensajeControlador(escenario.servicioMensajeria),
                        new CalificacionControlador(escenario.servicioReputacion),
                        new NegociacionControlador(escenario.servicioNegociacion),
                        new PagoControlador(escenario.servicioPagos, escenario.servicioNegociacion),
                        new PropuestaControlador(escenario.servicioPropuestas),
                        new AdministracionControlador(identidad, escenario.auditoria, escenario.servicioMensajeria,
                                escenario.servicioReputacion, escenario.servicioReportes),
                        new ReporteControlador(escenario.servicioReportes))
                .setControllerAdvice(new ManejadorGlobalExcepciones())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setCustomArgumentResolvers(new ResolvedorUsuarioAutenticado())
                .addInterceptors(new InterceptorRoles())
                .addFilters(new FiltroAutenticacionJwt(identidad, mapper))
                .build();

        tokenEstudiante = tokenDe("e@u.edu.co", "1098765432", Rol.ESTUDIANTE);
        tokenTutor = tokenDe("t@u.edu.co", "1012345678", Rol.TUTOR);
        tokenOtroTutor = tokenDe("t2@u.edu.co", "1022222222", Rol.TUTOR);
        tokenOtroEstudiante = tokenDe("e2@u.edu.co", "1033333333", Rol.ESTUDIANTE);
        identidad.crearAdministradorSiNoExiste("admin@tutofast.co", "Admin#2026");
        tokenAdmin = identidad.iniciarSesion(new InicioSesionDTO("admin@tutofast.co", "Admin#2026")).token();
        estudianteId = idDe("e@u.edu.co");
        tutorId = idDe("t@u.edu.co");

        SolicitudTutoria s = new SolicitudTutoria();
        s.setId(EscenarioNegociacion.nuevoId());
        s.setEstudianteId(estudianteId);
        s.setTema("Derivadas de funciones compuestas");
        s.setDescripcion("Quiero entender la regla de la cadena con ejemplos paso a paso para el parcial");
        s.setTipoServicio(TipoServicio.EXPLICACION_TEMA);
        s.setFechaHora(Instant.now().plus(3, ChronoUnit.DAYS));
        s.setModalidad(Modalidad.VIRTUAL);
        s.setDuracion(Duracion.MIN_60);
        s.setPrecioInicialCOP(new BigDecimal("40000"));
        solicitudes.put(s.getId(), s);
        solicitudId = s.getId();
        // El tutor propone: ya existe un hilo entre ambos.
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/propuestas"), tokenTutor,
                        "{\"tipo\":\"CONTRAPROPUESTA_PRECIO\",\"precioCOP\":36000}"))
                .andExpect(status().isCreated());
    }

    private String tokenDe(String correo, String documento, Rol rol) {
        identidad.registrarUsuario(new RegistroDTO("Persona Prueba", correo, documento, "Segura#2026", rol, true, null, 4));
        return identidad.iniciarSesion(new InicioSesionDTO(correo, "Segura#2026")).token();
    }

    private String idDe(String correo) {
        return usuarios.values().stream().filter(u -> u.getCorreo().equals(correo)).findFirst().orElseThrow().getId();
    }

    private MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder peticion, String token, String json) {
        MockHttpServletRequestBuilder b = token == null ? peticion : peticion.header("Authorization", "Bearer " + token);
        return json == null ? b : b.contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private String idDe(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private String mensajeDelEstudiante(String texto) throws Exception {
        return idDe(mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/mensajes"), tokenEstudiante,
                        "{\"tutorId\":\"" + tutorId + "\",\"contenido\":\"" + texto + "\"}"))
                .andExpect(status().isCreated()));
    }

    private String mensajeDelTutor(String texto) throws Exception {
        return idDe(mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/mensajes"), tokenTutor,
                        "{\"contenido\":\"" + texto + "\"}")).andExpect(status().isCreated()));
    }

    /** Selecciona, confirma, inicia el servicio y devuelve su id. */
    private String servicioEnDesarrollo() throws Exception {
        String propuestaId = escenario.propuestas.values().iterator().next().getId();
        mvc.perform(con(post("/api/propuestas/" + propuestaId + "/seleccionar"), tokenEstudiante, null)).andExpect(status().isOk());
        String servicioId = idDe(mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/confirmar"), tokenEstudiante,
                "{\"tokenPagoSimulado\":\"tok_aprobar\"}")).andExpect(status().isOk()));
        mvc.perform(con(post("/api/servicios/" + servicioId + "/iniciar"), tokenEstudiante, null)).andExpect(status().isOk());
        return servicioId;
    }

    private String servicioFinalizado() throws Exception {
        String id = servicioEnDesarrollo();
        mvc.perform(con(post("/api/servicios/" + id + "/finalizar"), tokenEstudiante, null)).andExpect(status().isOk());
        return id;
    }

    private static final String CALIFICACION_AL_TUTOR = "{\"claridadPedagogica\":5,\"dominioTematico\":5,\"puntualidad\":5,"
            + "\"cumplimiento\":5,\"trato\":5,\"relacionCalidadPrecio\":4,\"comentario\":\"Excelente explicación\"}";
    private static final String CALIFICACION_AL_ESTUDIANTE = "{\"puntualidad\":5,\"comunicacion\":5,\"cumplimiento\":4,"
            + "\"comentario\":\"Puntual y preparado\"}";

    // ---------------------------------------------------------------- mensajes

    @Test
    void elEstudianteYElTutorConversanPorHttp() throws Exception {
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/mensajes"), tokenEstudiante,
                        "{\"tutorId\":\"" + tutorId + "\",\"contenido\":\"Hola, ¿puedes el jueves?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contenido").value("Hola, ¿puedes el jueves?"))
                .andExpect(jsonPath("$.emisorId").value(estudianteId))
                .andExpect(jsonPath("$.reportado").value(false))
                .andExpect(jsonPath("$.sistema").value(false));
        mensajeDelTutor("Sí, a las 7");

        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/mensajes"), tokenTutor, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soloLectura").value(false))
                .andExpect(jsonPath("$.mensajes.length()").value(2))
                .andExpect(jsonPath("$.mensajes[1].contenido").value("Sí, a las 7"));
        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/mensajes?tutorId=" + tutorId), tokenEstudiante, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mensajes.length()").value(2));
    }

    @Test
    void elPollingConDesdeDevuelveSoloLosNuevos() throws Exception {
        mensajeDelEstudiante("uno");
        Thread.sleep(5);
        Instant corte = Instant.now();
        Thread.sleep(5);
        mensajeDelTutor("dos");

        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/mensajes?desde=" + corte), tokenEstudiante, null)
                        .param("tutorId", tutorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensajes.length()").value(1))
                .andExpect(jsonPath("$.mensajes[0].contenido").value("dos"));
        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/mensajes?desde=no-es-una-fecha"), tokenTutor, null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laMensajeriaExigeSesionYUnTerceroNoLeeNiEscribe() throws Exception {
        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/mensajes"), null, null)).andExpect(status().isUnauthorized());
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/mensajes"), null, "{\"contenido\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/mensajes?tutorId=" + tutorId), tokenOtroEstudiante, null))
                .andExpect(status().isForbidden());
        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/mensajes"), tokenOtroTutor, null))
                .andExpect(status().isForbidden());
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/mensajes"), tokenOtroTutor, "{\"contenido\":\"intruso\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/mensajes"), tokenAdmin,
                        "{\"tutorId\":\"" + tutorId + "\",\"contenido\":\"intervención\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(con(get("/api/solicitudes/" + SIN_ID + "/mensajes?tutorId=" + tutorId), tokenEstudiante, null))
                .andExpect(status().isNotFound());
        assertTrue(escenario.mensajes.isEmpty());
    }

    @Test
    void elAdministradorLeeElHiloEnSoloLectura() throws Exception {
        mensajeDelEstudiante("hola");

        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/mensajes?tutorId=" + tutorId), tokenAdmin, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soloLectura").value(true))
                .andExpect(jsonPath("$.mensajes.length()").value(1));
        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/mensajes"), tokenAdmin, null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void validaElCuerpoDelMensaje() throws Exception {
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/mensajes"), tokenEstudiante,
                        "{\"tutorId\":\"" + tutorId + "\",\"contenido\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/mensajes"), tokenEstudiante,
                        "{\"tutorId\":\"" + tutorId + "\",\"contenido\":\"" + "a".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0].campo").value("contenido"));
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/mensajes"), tokenEstudiante,
                        "{\"tutorId\":\"no-valido\",\"contenido\":\"hola\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0].campo").value("tutorId"));
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/mensajes"), tokenEstudiante, "{\"contenido\":\"hola\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void bloqueaLosContactosExternosConElCodigoEstable() throws Exception {
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/mensajes"), tokenTutor,
                        "{\"contenido\":\"Escríbeme al 300 123 4567 y te cobro menos\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CONTACTO_EXTERNO"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("fuera de TutoFast")));
        assertTrue(escenario.mensajes.isEmpty());
    }

    @Test
    void enviaUnMensajeConAdjuntoMultipartYLoDescargaElParticipante() throws Exception {
        MockMultipartFile datos = new MockMultipartFile("datos", "", "application/json",
                "{\"contenido\":\"Te comparto la guía\"}".getBytes(StandardCharsets.UTF_8));

        String cuerpo = mvc.perform(multipart("/api/solicitudes/" + solicitudId + "/mensajes")
                        .file(datos).file(new MockMultipartFile("archivo", "guia.pdf", "application/pdf",
                                ArchivosDePrueba.pdf().getBytes()))
                        .header("Authorization", "Bearer " + tokenTutor))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.archivo.nombre").value("guia.pdf"))
                .andExpect(jsonPath("$.archivo.tipo").value("PDF"))
                .andReturn().getResponse().getContentAsString();

        String archivoId = mapper.readTree(cuerpo).get("archivo").get("archivoId").asText();
        assertTrue(escenario.almacenamiento.contiene(archivoId));
    }

    @Test
    void rechazaUnAdjuntoNoPermitidoConTipoNoPermitido() throws Exception {
        MockMultipartFile datos = new MockMultipartFile("datos", "", "application/json",
                "{\"contenido\":\"mira\"}".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/solicitudes/" + solicitudId + "/mensajes")
                        .file(datos).file(new MockMultipartFile("archivo", "script.bat", "application/octet-stream",
                                "echo hola".getBytes()))
                        .header("Authorization", "Bearer " + tokenTutor))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("TIPO_ARCHIVO_NO_PERMITIDO"));
        assertEquals(0, escenario.almacenamiento.cantidad());
        assertTrue(escenario.mensajes.isEmpty());
    }

    // ---------------------------------------------------------------- hilos

    @Test
    void listaLosHilosParaElDuenoYElAdministradorPeroNoParaElTutor() throws Exception {
        mensajeDelTutor("¿Te sirve el jueves?");

        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/hilos"), tokenEstudiante, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tutorId").value(tutorId))
                .andExpect(jsonPath("$[0].tutorNombre").value("Persona Prueba"))
                .andExpect(jsonPath("$[0].ultimoMensaje").value("¿Te sirve el jueves?"))
                .andExpect(jsonPath("$[0].noLeidos").value(1))
                .andExpect(jsonPath("$[0].soloLectura").value(false));
        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/hilos"), tokenAdmin, null)).andExpect(status().isOk());
        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/hilos"), tokenTutor, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/hilos"), tokenOtroEstudiante, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/hilos"), null, null)).andExpect(status().isUnauthorized());
    }

    @Test
    void alConfirmarElServicioElHiloDelTutorNoElegidoQuedaDeSoloLectura() throws Exception {
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/propuestas"), tokenOtroTutor, "{\"tipo\":\"ACEPTA_PRECIO\"}"))
                .andExpect(status().isCreated());
        String otroTutorId = idDe("t2@u.edu.co");
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/mensajes"), tokenEstudiante,
                        "{\"tutorId\":\"" + otroTutorId + "\",\"contenido\":\"Hola\"}")).andExpect(status().isCreated());
        servicioEnDesarrollo(); // elige al primer tutor

        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/mensajes"), tokenOtroTutor, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soloLectura").value(true))
                .andExpect(jsonPath("$.mensajes[1].sistema").value(true));
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/mensajes"), tokenOtroTutor, "{\"contenido\":\"¿y yo?\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("NO_AUTORIZADO"));
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/mensajes"), tokenTutor, "{\"contenido\":\"seguimos\"}"))
                .andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------- reportar y moderar

    @Test
    void reportaUnMensajeYLaColaDelAdministradorLoMuestra() throws Exception {
        String id = mensajeDelTutor("Eres un inútil");

        mvc.perform(con(post("/api/mensajes/" + id + "/reportar"), tokenEstudiante,
                        "{\"motivo\":\"CONDUCTA_ABUSIVA\",\"detalle\":\"Me insultó\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reportado").value(true))
                .andExpect(jsonPath("$.contenido").value("Eres un inútil"));

        mvc.perform(con(get("/api/admin/mensajes-reportados"), tokenAdmin, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].mensaje.id").value(id))
                .andExpect(jsonPath("$[0].solicitudId").value(solicitudId))
                .andExpect(jsonPath("$[0].tutorId").value(tutorId))
                .andExpect(jsonPath("$[0].motivoReporte").value("CONDUCTA_ABUSIVA"))
                .andExpect(jsonPath("$[0].detalleReporte").value("Me insultó"))
                .andExpect(jsonPath("$[0].reportante.id").value(estudianteId));
    }

    @Test
    void elReporteValidaMotivoPermisosYDuplicados() throws Exception {
        String id = mensajeDelTutor("hola");

        mvc.perform(con(post("/api/mensajes/" + id + "/reportar"), tokenEstudiante, "{}")).andExpect(status().isBadRequest());
        mvc.perform(con(post("/api/mensajes/" + id + "/reportar"), tokenEstudiante, "{\"motivo\":\"INVENTADO\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(con(post("/api/mensajes/" + id + "/reportar"), null, "{\"motivo\":\"SPAM\"}")).andExpect(status().isUnauthorized());
        mvc.perform(con(post("/api/mensajes/" + id + "/reportar"), tokenTutor, "{\"motivo\":\"SPAM\"}"))
                .andExpect(status().isForbidden()); // su propio mensaje
        mvc.perform(con(post("/api/mensajes/" + id + "/reportar"), tokenOtroEstudiante, "{\"motivo\":\"SPAM\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(con(post("/api/mensajes/" + id + "/reportar"), tokenAdmin, "{\"motivo\":\"SPAM\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(con(post("/api/mensajes/" + SIN_ID + "/reportar"), tokenEstudiante, "{\"motivo\":\"SPAM\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(con(post("/api/mensajes/" + id + "/reportar"), tokenEstudiante, "{\"motivo\":\"SPAM\"}")).andExpect(status().isOk());
        mvc.perform(con(post("/api/mensajes/" + id + "/reportar"), tokenEstudiante, "{\"motivo\":\"SPAM\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("MENSAJE_YA_REPORTADO"));
    }

    @Test
    void soloElAdministradorVeLaColaYModera() throws Exception {
        String id = mensajeDelTutor("hola");
        mvc.perform(con(post("/api/mensajes/" + id + "/reportar"), tokenEstudiante, "{\"motivo\":\"OTRO\"}")).andExpect(status().isOk());

        for (String token : List.of(tokenEstudiante, tokenTutor)) {
            mvc.perform(con(get("/api/admin/mensajes-reportados"), token, null)).andExpect(status().isForbidden());
            mvc.perform(con(post("/api/admin/mensajes-reportados/" + id + "/moderar"), token,
                    "{\"decision\":\"DESESTIMAR\"}")).andExpect(status().isForbidden());
        }
        mvc.perform(con(get("/api/admin/mensajes-reportados"), null, null)).andExpect(status().isUnauthorized());
    }

    @Test
    void moderaUnMensajeConfirmandoLaInfraccionYSancionando() throws Exception {
        String id = mensajeDelTutor("Mensaje ofensivo");
        mvc.perform(con(post("/api/mensajes/" + id + "/reportar"), tokenEstudiante, "{\"motivo\":\"ACOSO\"}")).andExpect(status().isOk());
        String fin = Instant.now().plus(Duration.ofDays(10)).toString();

        mvc.perform(con(post("/api/admin/mensajes-reportados/" + id + "/moderar"), tokenAdmin,
                        "{\"decision\":\"CONFIRMAR_INFRACCION\",\"observacion\":\"Acoso comprobado\","
                                + "\"sancion\":{\"tipo\":\"SUSPENSION_TEMPORAL\",\"fin\":\"" + fin + "\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderacion.decision").value("CONFIRMAR_INFRACCION"))
                .andExpect(jsonPath("$.moderacion.sancionId").isNotEmpty());

        assertEquals(EstadoCuenta.SUSPENDIDA, usuarios.get(tutorId).getEstadoCuenta());
        mvc.perform(con(get("/api/admin/mensajes-reportados"), tokenAdmin, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(con(get("/api/admin/mensajes-reportados?incluirModerados=true"), tokenAdmin, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(con(post("/api/admin/mensajes-reportados/" + id + "/moderar"), tokenAdmin, "{\"decision\":\"DESESTIMAR\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("MENSAJE_YA_MODERADO"));
        // La cuenta suspendida ya no puede usar su token (403) y el estudiante ve el mensaje retirado.
        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/mensajes"), tokenTutor, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/mensajes?tutorId=" + tutorId), tokenEstudiante, null))
                .andExpect(jsonPath("$.mensajes[0].retirado").value(true))
                .andExpect(jsonPath("$.mensajes[0].contenido").value(org.hamcrest.Matchers.containsString("retirado")));
    }

    @Test
    void laModeracionValidaElCuerpo() throws Exception {
        String id = mensajeDelTutor("hola");
        mvc.perform(con(post("/api/mensajes/" + id + "/reportar"), tokenEstudiante, "{\"motivo\":\"OTRO\"}")).andExpect(status().isOk());

        mvc.perform(con(post("/api/admin/mensajes-reportados/" + id + "/moderar"), tokenAdmin, "{}")).andExpect(status().isBadRequest());
        mvc.perform(con(post("/api/admin/mensajes-reportados/" + id + "/moderar"), tokenAdmin,
                "{\"decision\":\"CONFIRMAR_INFRACCION\",\"sancion\":{}}")).andExpect(status().isBadRequest());
        mvc.perform(con(post("/api/admin/mensajes-reportados/" + id + "/moderar"), tokenAdmin,
                "{\"decision\":\"DESESTIMAR\",\"sancion\":{\"tipo\":\"ADVERTENCIA\"}}")).andExpect(status().isBadRequest());
        mvc.perform(con(post("/api/admin/mensajes-reportados/" + id + "/moderar"), tokenAdmin,
                "{\"decision\":\"CONFIRMAR_INFRACCION\",\"sancion\":{\"tipo\":\"SUSPENSION_TEMPORAL\"}}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(con(post("/api/admin/mensajes-reportados/" + SIN_ID + "/moderar"), tokenAdmin, "{\"decision\":\"DESESTIMAR\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(con(post("/api/admin/mensajes-reportados/" + id + "/moderar"), tokenAdmin,
                "{\"decision\":\"DESESTIMAR\",\"observacion\":\"" + "x".repeat(501) + "\"}")).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- finalizar y cierre automático

    @Test
    void finalizaElServicioYDevuelveLaLiquidacion() throws Exception {
        String id = servicioEnDesarrollo();

        mvc.perform(con(post("/api/servicios/" + id + "/finalizar"), tokenEstudiante, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicioId").value(id))
                .andExpect(jsonPath("$.estado").value("LIBERADO"))
                .andExpect(jsonPath("$.precioFinalCOP").value(36000.0))
                .andExpect(jsonPath("$.comisionPct").value(10.0))
                .andExpect(jsonPath("$.comisionCOP").value(3600.0))
                .andExpect(jsonPath("$.montoNetoCOP").value(32400.0))
                .andExpect(jsonPath("$.fechaLiquidacion").isNotEmpty());
        mvc.perform(con(get("/api/servicios/" + id), tokenTutor, null))
                .andExpect(jsonPath("$.estado").value("FINALIZADO"))
                .andExpect(jsonPath("$.pago.estado").value("LIBERADO"));
        assertEquals(1, usuarios.get(tutorId).getPerfilTutor().getServiciosCompletados());
    }

    @Test
    void laDobleFinalizacionDa409() throws Exception {
        String id = servicioFinalizado();

        mvc.perform(con(post("/api/servicios/" + id + "/finalizar"), tokenEstudiante, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SERVICIO_YA_FINALIZADO"))
                .andExpect(jsonPath("$.mensaje").value("El servicio ya se encuentra finalizado y no requiere nuevas acciones"));
    }

    @Test
    void soloElEstudianteDelServicioFinalizaYSoloSiEstaEnDesarrollo() throws Exception {
        String propuestaId = escenario.propuestas.values().iterator().next().getId();
        mvc.perform(con(post("/api/propuestas/" + propuestaId + "/seleccionar"), tokenEstudiante, null)).andExpect(status().isOk());
        String id = idDe(mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/confirmar"), tokenEstudiante,
                "{\"tokenPagoSimulado\":\"tok_aprobar\"}")).andExpect(status().isOk()));

        mvc.perform(con(post("/api/servicios/" + id + "/finalizar"), tokenEstudiante, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
        mvc.perform(con(post("/api/servicios/" + id + "/iniciar"), tokenTutor, null)).andExpect(status().isOk());
        mvc.perform(con(post("/api/servicios/" + id + "/finalizar"), null, null)).andExpect(status().isUnauthorized());
        mvc.perform(con(post("/api/servicios/" + id + "/finalizar"), tokenTutor, null)).andExpect(status().isForbidden());
        mvc.perform(con(post("/api/servicios/" + id + "/finalizar"), tokenAdmin, null)).andExpect(status().isForbidden());
        mvc.perform(con(post("/api/servicios/" + id + "/finalizar"), tokenOtroEstudiante, null)).andExpect(status().isForbidden());
        mvc.perform(con(post("/api/servicios/" + SIN_ID + "/finalizar"), tokenEstudiante, null)).andExpect(status().isNotFound());
        assertEquals(EstadoServicio.EN_DESARROLLO, escenario.servicios.get(id).getEstado());
    }

    @Test
    void simularElCierreAutomaticoEsSoloParaElAdministrador() throws Exception {
        String id = servicioEnDesarrollo(); // empezó hace segundos: todavía no cumple las 24 h

        mvc.perform(con(post("/api/pagos/simular-cierre-automatico"), tokenTutor, null)).andExpect(status().isForbidden());
        mvc.perform(con(post("/api/pagos/simular-cierre-automatico"), tokenEstudiante, null)).andExpect(status().isForbidden());
        mvc.perform(con(post("/api/pagos/simular-cierre-automatico"), null, null)).andExpect(status().isUnauthorized());
        assertEquals(EstadoServicio.EN_DESARROLLO, escenario.servicios.get(id).getEstado());

        mvc.perform(con(post("/api/pagos/simular-cierre-automatico"), tokenAdmin, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisados").value(0 + 1))
                .andExpect(jsonPath("$.cerrados").value(0))
                .andExpect(jsonPath("$.omitidosPorPlazo").value(1))
                .andExpect(jsonPath("$.serviciosCerrados").isEmpty());
        assertEquals(EstadoServicio.EN_DESARROLLO, escenario.servicios.get(id).getEstado());

        mvc.perform(con(post("/api/pagos/simular-cierre-automatico?ignorarPlazo=true"), tokenAdmin, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cerrados").value(1))
                .andExpect(jsonPath("$.serviciosCerrados[0]").value(id));
        assertEquals(EstadoServicio.FINALIZADO, escenario.servicios.get(id).getEstado());
        mvc.perform(con(post("/api/pagos/simular-cierre-automatico?ignorarPlazo=talvez"), tokenAdmin, null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elCierreAutomaticoOmiteLosServiciosConReporteAbierto() throws Exception {
        String id = servicioEnDesarrollo();
        escenario.serviciosConReporteAbierto.add(id);

        mvc.perform(con(post("/api/pagos/simular-cierre-automatico?ignorarPlazo=true"), tokenAdmin, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cerrados").value(0))
                .andExpect(jsonPath("$.omitidosPorReporte").value(1));
        assertEquals(EstadoServicio.EN_DESARROLLO, escenario.servicios.get(id).getEstado());
    }

    // ---------------------------------------------------------------- calificaciones

    @Test
    void ambasPartesCalificanYLasReseñasSeRevelanJuntas() throws Exception {
        String id = servicioFinalizado();

        mvc.perform(con(post("/api/servicios/" + id + "/calificaciones"), tokenEstudiante, CALIFICACION_AL_TUTOR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("AL_TUTOR"))
                .andExpect(jsonPath("$.promedio").value(4.83))
                .andExpect(jsonPath("$.visible").value(false))
                .andExpect(jsonPath("$.criterios.relacionCalidadPrecio").value(4));
        mvc.perform(con(get("/api/servicios/" + id + "/calificaciones"), tokenEstudiante, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mia.tipo").value("AL_TUTOR"))
                .andExpect(jsonPath("$.contraparte").doesNotExist())
                .andExpect(jsonPath("$.puedeCalificar").value(false))
                .andExpect(jsonPath("$.fechaLimite").isNotEmpty());
        mvc.perform(con(get("/api/servicios/" + id + "/calificaciones"), tokenTutor, null))
                .andExpect(jsonPath("$.mia").doesNotExist())
                .andExpect(jsonPath("$.puedeCalificar").value(true));

        mvc.perform(con(post("/api/servicios/" + id + "/calificaciones"), tokenTutor, CALIFICACION_AL_ESTUDIANTE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("AL_ESTUDIANTE"))
                .andExpect(jsonPath("$.promedio").value(4.67))
                .andExpect(jsonPath("$.visible").value(true));
        mvc.perform(con(get("/api/servicios/" + id + "/calificaciones"), tokenEstudiante, null))
                .andExpect(jsonPath("$.contraparte.tipo").value("AL_ESTUDIANTE"))
                .andExpect(jsonPath("$.contraparte.visible").value(true));
        assertEquals(0, new BigDecimal("4.83").compareTo(usuarios.get(tutorId).getPerfilTutor().getPromedioCalificacion()));
    }

    @Test
    void validaLaCalificacionYSusReglas() throws Exception {
        String enDesarrollo = servicioEnDesarrollo();
        mvc.perform(con(post("/api/servicios/" + enDesarrollo + "/calificaciones"), tokenEstudiante, CALIFICACION_AL_TUTOR))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("(R13)")));
        mvc.perform(con(post("/api/servicios/" + enDesarrollo + "/finalizar"), tokenEstudiante, null)).andExpect(status().isOk());

        mvc.perform(con(post("/api/servicios/" + enDesarrollo + "/calificaciones"), tokenEstudiante,
                        CALIFICACION_AL_TUTOR.replace("\"trato\":5", "\"trato\":6")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detalles[0].campo").value("trato"));
        mvc.perform(con(post("/api/servicios/" + enDesarrollo + "/calificaciones"), tokenEstudiante,
                        CALIFICACION_AL_TUTOR.replace("\"puntualidad\":5", "\"puntualidad\":0")))
                .andExpect(status().isBadRequest());
        mvc.perform(con(post("/api/servicios/" + enDesarrollo + "/calificaciones"), tokenEstudiante, "{\"trato\":5}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
        mvc.perform(con(post("/api/servicios/" + enDesarrollo + "/calificaciones"), tokenEstudiante,
                        CALIFICACION_AL_TUTOR.replace("Excelente explicación", "x".repeat(501))))
                .andExpect(status().isBadRequest());
        mvc.perform(con(post("/api/servicios/" + enDesarrollo + "/calificaciones"), tokenEstudiante, CALIFICACION_AL_TUTOR))
                .andExpect(status().isCreated());
        mvc.perform(con(post("/api/servicios/" + enDesarrollo + "/calificaciones"), tokenEstudiante, CALIFICACION_AL_TUTOR))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("DUPLICADO"));
    }

    @Test
    void laCalificacionExigeSesionRolYParticipacion() throws Exception {
        String id = servicioFinalizado();

        mvc.perform(con(post("/api/servicios/" + id + "/calificaciones"), null, CALIFICACION_AL_TUTOR)).andExpect(status().isUnauthorized());
        mvc.perform(con(post("/api/servicios/" + id + "/calificaciones"), tokenAdmin, CALIFICACION_AL_TUTOR)).andExpect(status().isForbidden());
        mvc.perform(con(post("/api/servicios/" + id + "/calificaciones"), tokenOtroEstudiante, CALIFICACION_AL_TUTOR))
                .andExpect(status().isForbidden());
        mvc.perform(con(post("/api/servicios/" + id + "/calificaciones"), tokenOtroTutor, CALIFICACION_AL_ESTUDIANTE))
                .andExpect(status().isForbidden());
        mvc.perform(con(post("/api/servicios/" + SIN_ID + "/calificaciones"), tokenEstudiante, CALIFICACION_AL_TUTOR))
                .andExpect(status().isNotFound());
        mvc.perform(con(get("/api/servicios/" + id + "/calificaciones"), tokenOtroEstudiante, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/servicios/" + id + "/calificaciones"), tokenAdmin, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/servicios/" + id + "/calificaciones"), null, null)).andExpect(status().isUnauthorized());
    }

    @Test
    void elTutorReseñadoReportaYElAdministradorOcultaLaReseña() throws Exception {
        String id = servicioFinalizado();
        String calificacionId = idDe(mvc.perform(con(post("/api/servicios/" + id + "/calificaciones"), tokenEstudiante,
                CALIFICACION_AL_TUTOR)).andExpect(status().isCreated()));
        mvc.perform(con(post("/api/servicios/" + id + "/calificaciones"), tokenTutor, CALIFICACION_AL_ESTUDIANTE))
                .andExpect(status().isCreated());

        mvc.perform(con(post("/api/calificaciones/" + calificacionId + "/reportar"), tokenEstudiante, "{\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(con(post("/api/calificaciones/" + calificacionId + "/reportar"), tokenTutor, "{\"motivo\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(con(post("/api/calificaciones/" + calificacionId + "/reportar"), tokenTutor, "{\"motivo\":\"Falsedades\"}"))
                .andExpect(status().isOk());
        mvc.perform(con(post("/api/calificaciones/" + calificacionId + "/reportar"), tokenTutor, "{\"motivo\":\"Falsedades\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("RESENA_YA_REPORTADA"));

        mvc.perform(con(get("/api/admin/resenas-reportadas"), tokenTutor, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/admin/resenas-reportadas"), tokenAdmin, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].calificacion.id").value(calificacionId))
                .andExpect(jsonPath("$[0].motivoReporte").value("Falsedades"))
                .andExpect(jsonPath("$[0].autor.id").value(estudianteId));
        mvc.perform(con(post("/api/admin/resenas-reportadas/" + calificacionId + "/moderar"), tokenAdmin,
                        "{\"decision\":\"CONFIRMAR_INFRACCION\",\"observacion\":\"Falsedades comprobadas\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderacion.decision").value("CONFIRMAR_INFRACCION"))
                .andExpect(jsonPath("$.calificacion.visible").value(false));
        mvc.perform(con(post("/api/admin/resenas-reportadas/" + calificacionId + "/moderar"), tokenAdmin,
                        "{\"decision\":\"DESESTIMAR\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("RESENA_YA_MODERADA"));
        assertEquals(null, usuarios.get(tutorId).getPerfilTutor().getPromedioCalificacion());
    }
}
