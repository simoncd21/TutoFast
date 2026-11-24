package com.tutofast.controlador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.tutofast.RepositorioAuditoriaEnMemoria;
import com.tutofast.RepositorioUsuarioEnMemoria;
import com.tutofast.dto.InicioSesionDTO;
import com.tutofast.dto.RegistroDTO;
import com.tutofast.excepcion.ManejadorGlobalExcepciones;
import com.tutofast.modelo.Duracion;
import com.tutofast.modelo.EstadoSolicitud;
import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.Propuesta;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.SolicitudTutoria;
import com.tutofast.modelo.TipoServicio;
import com.tutofast.modelo.Usuario;
import com.tutofast.repositorio.RepositorioPropuesta;
import com.tutofast.repositorio.RepositorioSolicitudTutoria;
import com.tutofast.seguridad.FiltroAutenticacionJwt;
import com.tutofast.seguridad.InterceptorRoles;
import com.tutofast.seguridad.JwtUtil;
import com.tutofast.seguridad.ResolvedorUsuarioAutenticado;
import com.tutofast.servicio.ServicioArchivos;
import com.tutofast.servicio.ServicioIdentidad;
import com.tutofast.servicio.ServicioNotificaciones;
import com.tutofast.servicio.ServicioPropuestas;

/** Endpoints de propuestas y contraofertas a través del filtro JWT, los roles y el manejador global de errores. */
class PropuestaControladorTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Map<String, Usuario> usuarios = new LinkedHashMap<>();
    private final Map<String, SolicitudTutoria> solicitudes = new LinkedHashMap<>();
    private final Map<String, Propuesta> propuestas = new LinkedHashMap<>();
    private ServicioIdentidad identidad;
    private MockMvc mvc;
    private String tokenEstudiante;
    private String tokenTutor;
    private String solicitudId;

    @BeforeEach
    void preparar() {
        var repoUsuarios = RepositorioUsuarioEnMemoria.crear(usuarios);
        when(repoUsuarios.findAllById(any())).thenAnswer(inv -> {
            List<Usuario> lista = new ArrayList<>();
            for (Object id : (Iterable<?>) inv.getArgument(0)) {
                if (usuarios.containsKey(id)) {
                    lista.add(usuarios.get(id));
                }
            }
            return lista;
        });

        RepositorioSolicitudTutoria repoSolicitudes = mock(RepositorioSolicitudTutoria.class);
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

        RepositorioPropuesta repoPropuestas = mock(RepositorioPropuesta.class);
        when(repoPropuestas.save(any(Propuesta.class))).thenAnswer(inv -> {
            Propuesta p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 24));
            }
            propuestas.put(p.getId(), p);
            return p;
        });
        when(repoPropuestas.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(propuestas.get(inv.<String>getArgument(0))));
        when(repoPropuestas.existsBySolicitudIdAndTutorIdAndActivaTrue(anyString(), anyString())).thenAnswer(inv ->
                propuestas.values().stream().anyMatch(p -> p.isActiva() && p.getSolicitudId().equals(inv.getArgument(0))
                        && p.getTutorId().equals(inv.getArgument(1))));
        when(repoPropuestas.findBySolicitudIdOrderByFechaCreacionDesc(anyString())).thenAnswer(inv -> propuestas.values()
                .stream().filter(p -> p.getSolicitudId().equals(inv.getArgument(0))).toList());
        when(repoPropuestas.findBySolicitudIdAndTutorIdOrderByFechaCreacionDesc(anyString(), anyString())).thenAnswer(inv ->
                propuestas.values().stream().filter(p -> p.getSolicitudId().equals(inv.getArgument(0))
                        && p.getTutorId().equals(inv.getArgument(1))).toList());
        when(repoPropuestas.findByTutorIdOrderByFechaCreacionDesc(anyString())).thenAnswer(inv -> propuestas.values()
                .stream().filter(p -> p.getTutorId().equals(inv.getArgument(0))).toList());

        var auditoria = RepositorioAuditoriaEnMemoria.servicio(new ArrayList<>());
        identidad = new ServicioIdentidad(repoUsuarios, new BCryptPasswordEncoder(4),
                new JwtUtil("secreto-de-prueba-con-mas-de-32-caracteres-ok", Duration.ofHours(8)), auditoria,
                new ServicioArchivos(new AlmacenamientoEnMemoria(), List.of()), mock(ServicioNotificaciones.class),
                mock(com.tutofast.repositorio.RepositorioAsignatura.class),
                mock(com.tutofast.repositorio.RepositorioCalificacion.class));
        var servicio = new ServicioPropuestas(repoPropuestas, repoSolicitudes, repoUsuarios, auditoria,
                mock(ServicioNotificaciones.class));

        mvc = MockMvcBuilders.standaloneSetup(new PropuestaControlador(servicio))
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
        s.setPrecioInicialCOP(new BigDecimal("25000"));
        solicitudes.put(s.getId(), s);
        solicitudId = s.getId();
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

    private String enviar(String token, String json) throws Exception {
        String cuerpo = mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/propuestas"), token, json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(cuerpo).get("id").asText();
    }

    private static final String CONTRAPROPUESTA = "{\"tipo\":\"CONTRAPROPUESTA_PRECIO\",\"precioCOP\":30000,\"nota\":\"Incluye material\"}";

    @Test
    void flujoCompletoEnvioContraofertaRespuestaYSeleccion() throws Exception {
        String id = enviar(tokenTutor, CONTRAPROPUESTA);
        assertEquals(EstadoSolicitud.NEGOCIACION_EN_CURSO, solicitudes.get(solicitudId).getEstado());

        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/propuestas"), tokenEstudiante, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].tutorNombre").value("Persona Prueba"))
                .andExpect(jsonPath("$[0].estado").value("ENVIADA"))
                .andExpect(jsonPath("$[0].precioCOP").value(30000.0));

        mvc.perform(con(post("/api/propuestas/" + id + "/contraofertar"), tokenEstudiante,
                        "{\"precioCOP\":27000,\"nota\":\"¿Podría ser 27.000?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONTRAOFERTA_ESTUDIANTE"))
                .andExpect(jsonPath("$.contraofertaCOP").value(27000.0));

        mvc.perform(con(post("/api/propuestas/" + id + "/seleccionar"), tokenEstudiante, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CONTRAOFERTA_PENDIENTE"));

        mvc.perform(con(put("/api/propuestas/" + id), tokenTutor,
                        "{\"tipo\":\"CONTRAPROPUESTA_PRECIO\",\"precioCOP\":28000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENVIADA"))
                .andExpect(jsonPath("$.contraofertaCOP").doesNotExist());

        mvc.perform(con(post("/api/propuestas/" + id + "/seleccionar"), tokenEstudiante, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ACEPTADA"));
        assertEquals(EstadoSolicitud.TUTOR_SELECCIONADO, solicitudes.get(solicitudId).getEstado());

        mvc.perform(con(get("/api/propuestas/mias"), tokenTutor, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].solicitudTema").value("Derivadas de funciones compuestas"))
                .andExpect(jsonPath("$[0].solicitudEstado").value("TUTOR_SELECCIONADO"));
    }

    @Test
    void sinTokenDa401YLosRolesIncorrectosDan403() throws Exception {
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/propuestas"), null, CONTRAPROPUESTA))
                .andExpect(status().isUnauthorized());
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/propuestas"), tokenEstudiante, CONTRAPROPUESTA))
                .andExpect(status().isForbidden());

        String id = enviar(tokenTutor, CONTRAPROPUESTA);
        mvc.perform(con(post("/api/propuestas/" + id + "/contraofertar"), tokenTutor, "{\"precioCOP\":1000}"))
                .andExpect(status().isForbidden());
        mvc.perform(con(post("/api/propuestas/" + id + "/seleccionar"), tokenTutor, null)).andExpect(status().isForbidden());
        mvc.perform(con(post("/api/propuestas/" + id + "/retirar"), tokenEstudiante, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/propuestas/mias"), tokenEstudiante, null)).andExpect(status().isForbidden());
    }

    @Test
    void tutorDuenoDeLaSolicitudDa403() throws Exception {
        String tokenAmbos = tokenDe("a@u.edu.co", "1011111111", Rol.TUTOR);
        solicitudes.get(solicitudId).setEstudianteId(idDe("a@u.edu.co"));
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/propuestas"), tokenAmbos, CONTRAPROPUESTA))
                .andExpect(status().isForbidden());
    }

    @Test
    void validacionesDeEntrada() throws Exception {
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/propuestas"), tokenTutor, "{\"precioCOP\":30000}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/propuestas"), tokenTutor,
                        "{\"tipo\":\"CONTRAPROPUESTA_PRECIO\",\"precioCOP\":-100}"))
                .andExpect(status().isBadRequest());
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/propuestas"), tokenTutor,
                        "{\"tipo\":\"CONTRAPROPUESTA_PRECIO\",\"precioCOP\":30000,\"duracion\":\"MIN_999\"}"))
                .andExpect(status().isBadRequest());

        String id = enviar(tokenTutor, CONTRAPROPUESTA);
        mvc.perform(con(post("/api/propuestas/" + id + "/contraofertar"), tokenEstudiante, "{\"precioCOP\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(con(post("/api/propuestas/" + id + "/contraofertar"), tokenEstudiante, "{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void segundaPropuestaActivaDa409YSolicitudCerradaDa409() throws Exception {
        enviar(tokenTutor, CONTRAPROPUESTA);
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/propuestas"), tokenTutor, CONTRAPROPUESTA))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("DUPLICADO"));

        String otroTutor = tokenDe("t2@u.edu.co", "1022222222", Rol.TUTOR);
        solicitudes.get(solicitudId).setEstado(EstadoSolicitud.FORMALIZADA);
        mvc.perform(con(post("/api/solicitudes/" + solicitudId + "/propuestas"), otroTutor, CONTRAPROPUESTA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La solicitud ya ha sido acordada con otro tutor y no admite nuevas propuestas"));
    }

    @Test
    void autorizacionPorRecursoYNoEncontrados() throws Exception {
        String id = enviar(tokenTutor, CONTRAPROPUESTA);
        String otroTutor = tokenDe("t2@u.edu.co", "1022222222", Rol.TUTOR);
        String otroEstudiante = tokenDe("e2@u.edu.co", "1033333333", Rol.ESTUDIANTE);

        mvc.perform(con(put("/api/propuestas/" + id), otroTutor, CONTRAPROPUESTA)).andExpect(status().isForbidden());
        mvc.perform(con(post("/api/propuestas/" + id + "/retirar"), otroTutor, null)).andExpect(status().isForbidden());
        mvc.perform(con(post("/api/propuestas/" + id + "/seleccionar"), otroEstudiante, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/propuestas"), otroEstudiante, null))
                .andExpect(status().isForbidden());
        mvc.perform(con(get("/api/solicitudes/" + solicitudId + "/propuestas"), otroTutor, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(con(post("/api/propuestas/no-existe/seleccionar"), tokenEstudiante, null)).andExpect(status().isNotFound());
        mvc.perform(con(get("/api/solicitudes/no-existe/propuestas"), tokenEstudiante, null)).andExpect(status().isNotFound());
    }

    @Test
    void retirarYRechazarSinCuerpo() throws Exception {
        String a = enviar(tokenTutor, CONTRAPROPUESTA);
        mvc.perform(con(post("/api/propuestas/" + a + "/rechazar"), tokenEstudiante, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("DESCARTADA"));
        mvc.perform(con(post("/api/propuestas/" + a + "/rechazar"), tokenEstudiante, "{\"motivo\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));

        String b = enviar(tokenTutor, CONTRAPROPUESTA);
        mvc.perform(con(post("/api/propuestas/" + b + "/retirar"), tokenTutor, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RETIRADA"));
        enviar(tokenTutor, CONTRAPROPUESTA);
    }
}
