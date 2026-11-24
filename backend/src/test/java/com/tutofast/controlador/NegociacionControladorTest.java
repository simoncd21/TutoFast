package com.tutofast.controlador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tutofast.AlmacenamientoEnMemoria;
import com.tutofast.EscenarioNegociacion;
import com.tutofast.RepositorioUsuarioEnMemoria;
import com.tutofast.dto.InicioSesionDTO;
import com.tutofast.dto.RegistroDTO;
import com.tutofast.excepcion.ManejadorGlobalExcepciones;
import com.tutofast.modelo.Duracion;
import com.tutofast.modelo.EstadoSolicitud;
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

/** Confirmación, servicios y pagos a través del filtro JWT, los roles y el manejador global de errores. */
class NegociacionControladorTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Map<String, Usuario> usuarios = new LinkedHashMap<>();
    private final Map<String, SolicitudTutoria> solicitudes = new LinkedHashMap<>();
    private EscenarioNegociacion escenario;
    private ServicioIdentidad identidad;
    private MockMvc mvc;
    private String tokenEstudiante;
    private String tokenTutor;
    private String solicitudId;
    private String propuestaId;

    @BeforeEach
    void preparar() throws Exception {
        var repoUsuarios = RepositorioUsuarioEnMemoria.crear(usuarios);
        escenario = new EscenarioNegociacion(repoUsuarios, usuarios, solicitudes);
        identidad = new ServicioIdentidad(repoUsuarios, new BCryptPasswordEncoder(4),
                new JwtUtil("secreto-de-prueba-con-mas-de-32-caracteres-ok", Duration.ofHours(8)), escenario.auditoria,
                new ServicioArchivos(new AlmacenamientoEnMemoria(), List.of()), mock(ServicioNotificaciones.class),
                mock(com.tutofast.repositorio.RepositorioAsignatura.class),
                mock(com.tutofast.repositorio.RepositorioCalificacion.class));

        mvc = MockMvcBuilders.standaloneSetup(new NegociacionControlador(escenario.servicioNegociacion),
                        new PagoControlador(escenario.servicioPagos), new PropuestaControlador(escenario.servicioPropuestas))
                .setControllerAdvice(new ManejadorGlobalExcepciones())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setCustomArgumentResolvers(new ResolvedorUsuarioAutenticado())
                .addInterceptors(new InterceptorRoles())
                .addFilters(new FiltroAutenticacionJwt(identidad, mapper))
                .build();

        tokenEstudiante = tokenDe("e@u.edu.co", "1098765432", Rol.ESTUDIANTE);
        tokenTutor = tokenDe("t@u.edu.co", "1012345678", Rol.TUTOR);
        SolicitudTutoria s = new SolicitudTutoria();
        s.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        s.setEstudianteId(idDe("e@u.edu.co"));
        s.setTema("Derivadas de funciones compuestas");
        s.setDescripcion("Quiero entender la regla de la cadena con ejemplos paso a paso para el parcial");
        s.setTipoServicio(TipoServicio.EXPLICACION_TEMA);
        s.setFechaHora(Instant.now().plus(3, ChronoUnit.DAYS));
        s.setModalidad(Modalidad.VIRTUAL);
        s.setDuracion(Duracion.MIN_60);
        s.setPrecioInicialCOP(new BigDecimal("40000"));
        solicitudes.put(s.getId(), s);
        solicitudId = s.getId();

        // El tutor propone 36.000 y el estudiante lo selecciona: queda TUTOR_SELECCIONADO.
        String cuerpo = mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/propuestas"), tokenTutor,
                        "{\"tipo\":\"CONTRAPROPUESTA_PRECIO\",\"precioCOP\":36000}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        propuestaId = mapper.readTree(cuerpo).get("id").asText();
        mvc.perform(con(post("/api/propuestas/" + propuestaId + "/seleccionar"), tokenEstudiante, null))
                .andExpect(status().isOk());
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

    private static final String PAGO_OK = "{\"tokenPagoSimulado\":\"tok_aprobar\"}";
    private static final String PAGO_RECHAZADO = "{\"tokenPagoSimulado\":\"tok_rechazar\"}";

    private String confirmar() throws Exception {
        String cuerpo = mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/confirmar"), tokenEstudiante, PAGO_OK))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(cuerpo).get("id").asText();
    }

    @Test
    void confirmarDevuelveElServicioConElPagoRetenidoYLaComisionCongelada() throws Exception {
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/confirmar"), tokenEstudiante, PAGO_OK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$.tema").value("Derivadas de funciones compuestas"))
                .andExpect(jsonPath("$.precioFinalCOP").value(36000.0))
                .andExpect(jsonPath("$.comisionPct").value(10.0))
                .andExpect(jsonPath("$.estudiante.nombre").value("Persona Prueba"))
                .andExpect(jsonPath("$.pago.estado").value("RETENIDO"))
                .andExpect(jsonPath("$.pago.montoRetenidoCOP").value(36000.0))
                .andExpect(jsonPath("$.pago.comisionCOP").value(3600.0))
                .andExpect(jsonPath("$.pago.montoNetoCOP").value(32400.0))
                .andExpect(jsonPath("$.pago.referencia").value(org.hamcrest.Matchers.startsWith("SIM-")));
        assertEquals(EstadoSolicitud.FORMALIZADA, solicitudes.get(solicitudId).getEstado());
    }

    @Test
    void pagoRechazadoDa422YLaSolicitudSigueSeleccionada() throws Exception {
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/confirmar"), tokenEstudiante, PAGO_RECHAZADO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PAGO_RECHAZADO"));
        assertEquals(EstadoSolicitud.TUTOR_SELECCIONADO, solicitudes.get(solicitudId).getEstado());
        assertEquals(0, escenario.servicios.size());
        assertEquals(0, escenario.pagos.size());
    }

    @Test
    void confirmarDosVecesDa409() throws Exception {
        confirmar();
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/confirmar"), tokenEstudiante, PAGO_OK))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SERVICIO_YA_CONFIRMADO"));
        assertEquals(1, escenario.pagos.size());
    }

    @Test
    void confirmarExigeSesionRolYTokenDePago() throws Exception {
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/confirmar"), null, PAGO_OK))
                .andExpect(status().isUnauthorized());
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/confirmar"), tokenTutor, PAGO_OK))
                .andExpect(status().isForbidden());
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/confirmar"), tokenEstudiante, "{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/confirmar"), tokenEstudiante, "{\"tokenPagoSimulado\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(con(post("/api/solicitudes/no-existe/confirmar"), tokenEstudiante, PAGO_OK))
                .andExpect(status().isNotFound());

        String otroEstudiante = tokenDe("e2@u.edu.co", "1033333333", Rol.ESTUDIANTE);
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/confirmar"), otroEstudiante, PAGO_OK))
                .andExpect(status().isForbidden());
        assertEquals(0, escenario.servicios.size());
    }

    @Test
    void consultaDeServiciosPorParticipantesYRestriccionesDeAcceso() throws Exception {
        String id = confirmar();
        String otroEstudiante = tokenDe("e2@u.edu.co", "1033333333", Rol.ESTUDIANTE);
        String otroTutor = tokenDe("t2@u.edu.co", "1022222222", Rol.TUTOR);

        mvc.perform(con(get("/api/servicios/mios"), tokenEstudiante, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(con(get("/api/servicios/mios?estado=CONFIRMADO"), tokenTutor, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(con(get("/api/servicios/mios?estado=FINALIZADO"), tokenTutor, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(con(get("/api/servicios/mios?estado=INVENTADO"), tokenTutor, null)).andExpect(status().isBadRequest());
        mvc.perform(con(get("/api/servicios/mios"), null, null)).andExpect(status().isUnauthorized());
        mvc.perform(con(get("/api/servicios/mios"), otroEstudiante, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mvc.perform(con(get("/api/servicios/" + id), tokenEstudiante, null)).andExpect(status().isOk());
        mvc.perform(con(get("/api/servicios/" + id), tokenTutor, null)).andExpect(status().isOk());
        mvc.perform(con(get("/api/servicios/" + id), otroEstudiante, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/servicios/" + id), otroTutor, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/servicios/no-existe"), tokenEstudiante, null)).andExpect(status().isNotFound());
    }

    @Test
    void enlaceEIniciodelServicio() throws Exception {
        String id = confirmar();

        mvc.perform(con(patch("/api/servicios/" + id + "/enlace"), tokenEstudiante, "{\"lugarOEnlace\":\"https://x.example\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(con(patch("/api/servicios/" + id + "/enlace"), tokenTutor, "{\"lugarOEnlace\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(con(patch("/api/servicios/" + id + "/enlace"), tokenTutor,
                        "{\"lugarOEnlace\":\"https://x.example\",\"precioFinalCOP\":1000}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SERVICIO_INMUTABLE"));
        mvc.perform(con(patch("/api/servicios/" + id + "/enlace"), tokenTutor, "{\"lugarOEnlace\":\"https://x.example\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lugarOEnlace").value("https://x.example"));

        mvc.perform(con(post("/api/servicios/" + id + "/iniciar"), tokenEstudiante, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EN_DESARROLLO"));
        mvc.perform(con(post("/api/servicios/" + id + "/iniciar"), tokenTutor, null))
                .andExpect(status().isConflict());
        mvc.perform(con(patch("/api/servicios/" + id + "/enlace"), tokenTutor, "{\"lugarOEnlace\":\"https://y.example\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SERVICIO_NO_EDITABLE"));
        mvc.perform(con(post("/api/servicios/" + id + "/iniciar"), null, null)).andExpect(status().isUnauthorized());
    }

    @Test
    void consultaDePagosPorParticipantes() throws Exception {
        String id = confirmar();
        String otroEstudiante = tokenDe("e2@u.edu.co", "1033333333", Rol.ESTUDIANTE);

        mvc.perform(con(get("/api/pagos/servicio/" + id), tokenEstudiante, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.servicioId").value(id))
                .andExpect(jsonPath("$.estado").value("RETENIDO"))
                .andExpect(jsonPath("$.precioFinalCOP").value(36000.0))
                .andExpect(jsonPath("$.comisionCOP").value(3600.0))
                .andExpect(jsonPath("$.montoNetoCOP").value(32400.0));
        mvc.perform(con(get("/api/pagos/servicio/" + id), tokenTutor, null)).andExpect(status().isOk());
        mvc.perform(con(get("/api/pagos/servicio/" + id), otroEstudiante, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/pagos/servicio/no-existe"), tokenTutor, null)).andExpect(status().isNotFound());
        mvc.perform(con(get("/api/pagos/servicio/" + id), null, null)).andExpect(status().isUnauthorized());

        mvc.perform(con(get("/api/pagos/mios"), tokenEstudiante, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].servicioId").value(id));
        mvc.perform(con(get("/api/pagos/mios"), tokenTutor, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].montoNetoCOP").value(32400.0));
        mvc.perform(con(get("/api/pagos/mios"), otroEstudiante, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void niLaRespuestaNiElPagoGuardadoContienenElTokenDeLaPasarela() throws Exception {
        String cuerpo = mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/confirmar"), tokenEstudiante, PAGO_OK))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(false, cuerpo.contains("tok_aprobar"));
        assertEquals(false, escenario.pagos.values().stream().anyMatch(p -> p.toString().contains("tok_aprobar")));
    }
}
