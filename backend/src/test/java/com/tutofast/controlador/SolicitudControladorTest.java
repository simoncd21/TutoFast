package com.tutofast.controlador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tutofast.AlmacenamientoEnMemoria;
import com.tutofast.ArchivosDePrueba;
import com.tutofast.RepositorioAuditoriaEnMemoria;
import com.tutofast.RepositorioUsuarioEnMemoria;
import com.tutofast.dto.InicioSesionDTO;
import com.tutofast.dto.RegistroDTO;
import com.tutofast.excepcion.ManejadorGlobalExcepciones;
import com.tutofast.modelo.Asignatura;
import com.tutofast.modelo.EstadoSolicitud;
import com.tutofast.modelo.Facultad;
import com.tutofast.modelo.ProgramaAcademico;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.SolicitudTutoria;
import com.tutofast.modelo.Universidad;
import com.tutofast.modelo.Usuario;
import com.tutofast.repositorio.FiltroSolicitudes;
import com.tutofast.repositorio.RepositorioAsignatura;
import com.tutofast.repositorio.RepositorioFacultad;
import com.tutofast.repositorio.RepositorioProgramaAcademico;
import com.tutofast.repositorio.RepositorioPropuesta;
import com.tutofast.repositorio.RepositorioServicioTutoria;
import com.tutofast.repositorio.RepositorioSolicitudTutoria;
import com.tutofast.repositorio.RepositorioUniversidad;
import com.tutofast.seguridad.FiltroAutenticacionJwt;
import com.tutofast.seguridad.InterceptorRoles;
import com.tutofast.seguridad.JwtUtil;
import com.tutofast.seguridad.ResolvedorUsuarioAutenticado;
import com.tutofast.servicio.ServicioArchivos;
import com.tutofast.servicio.ServicioIdentidad;
import com.tutofast.servicio.ServicioNotificaciones;
import com.tutofast.servicio.ServicioSolicitudes;

/** Endpoints de solicitudes a través del filtro JWT, los roles y el manejador global de errores. */
class SolicitudControladorTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Map<String, SolicitudTutoria> solicitudes = new LinkedHashMap<>();
    private final Map<String, Usuario> usuarios = new LinkedHashMap<>();
    private RepositorioSolicitudTutoria repoSolicitudes;
    private ServicioIdentidad identidad;
    private Asignatura asignatura;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        Universidad u = new Universidad();
        u.setId("u1");
        Facultad f = new Facultad();
        f.setId("f1");
        f.setUniversidadId("u1");
        ProgramaAcademico p = new ProgramaAcademico();
        p.setId("p1");
        p.setFacultadId("f1");
        p.setNombre("Sistemas");
        asignatura = new Asignatura();
        asignatura.setId("a1");
        asignatura.setProgramaId("p1");
        asignatura.setNombre("Cálculo");

        RepositorioUniversidad repoU = mock(RepositorioUniversidad.class);
        when(repoU.findById(anyString())).thenReturn(Optional.of(u));
        RepositorioFacultad repoF = mock(RepositorioFacultad.class);
        when(repoF.findById(anyString())).thenReturn(Optional.of(f));
        RepositorioProgramaAcademico repoP = mock(RepositorioProgramaAcademico.class);
        when(repoP.findById(anyString())).thenReturn(Optional.of(p));
        RepositorioAsignatura repoA = mock(RepositorioAsignatura.class);
        when(repoA.findById(anyString())).thenReturn(Optional.of(asignatura));

        repoSolicitudes = mock(RepositorioSolicitudTutoria.class);
        when(repoSolicitudes.save(any(SolicitudTutoria.class))).thenAnswer(inv -> {
            SolicitudTutoria s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 24));
            }
            solicitudes.put(s.getId(), s);
            return s;
        });
        when(repoSolicitudes.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(solicitudes.get(inv.<String>getArgument(0))));
        when(repoSolicitudes.findByEstudianteIdOrderByFechaCreacionDesc(anyString())).thenAnswer(inv -> solicitudes.values()
                .stream().filter(s -> s.getEstudianteId().equals(inv.getArgument(0))).toList());
        when(repoSolicitudes.buscar(any(), any(Pageable.class))).thenAnswer(inv -> new PageImpl<>(
                List.copyOf(solicitudes.values()), inv.getArgument(1), solicitudes.size()));

        var repoUsuarios = RepositorioUsuarioEnMemoria.crear(usuarios);
        var archivos = new ServicioArchivos(new AlmacenamientoEnMemoria(), List.of());
        var auditoria = RepositorioAuditoriaEnMemoria.servicio(new ArrayList<>());
        identidad = new ServicioIdentidad(repoUsuarios, new BCryptPasswordEncoder(4),
                new JwtUtil("secreto-de-prueba-con-mas-de-32-caracteres-ok", Duration.ofHours(8)), auditoria, archivos,
                mock(ServicioNotificaciones.class), mock(com.tutofast.repositorio.RepositorioAsignatura.class),
                mock(com.tutofast.repositorio.RepositorioCalificacion.class));
        var servicio = new ServicioSolicitudes(repoSolicitudes, repoUsuarios, repoA, repoP, repoF, repoU,
                mock(RepositorioPropuesta.class), mock(RepositorioServicioTutoria.class), archivos, auditoria,
                mock(ServicioNotificaciones.class), List.of(), List.of());

        mvc = MockMvcBuilders.standaloneSetup(new SolicitudControlador(servicio))
                .setControllerAdvice(new ManejadorGlobalExcepciones())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setCustomArgumentResolvers(new ResolvedorUsuarioAutenticado())
                .addInterceptors(new InterceptorRoles())
                .addFilters(new FiltroAutenticacionJwt(identidad, mapper))
                .build();
    }

    private String tokenDe(String correo, String documento, Rol rol) {
        identidad.registrarUsuario(new RegistroDTO("Persona Prueba", correo, documento, "Segura#2026", rol, true, null, 4));
        return identidad.iniciarSesion(new InicioSesionDTO(correo, "Segura#2026")).token();
    }

    private String datos(String tema, String descripcion, String duracion) {
        return "{\"asignaturaId\":\"a1\",\"tema\":\"" + tema + "\",\"descripcion\":\"" + descripcion
                + "\",\"tipoServicio\":\"EXPLICACION_TEMA\",\"fechaHora\":\"" + Instant.now().plusSeconds(86400 * 3)
                + "\",\"modalidad\":\"VIRTUAL\",\"duracion\":\"" + duracion + "\",\"precioInicialCOP\":25000}";
    }

    private MockMultipartHttpServletRequestBuilder publicacion(String token, String json) {
        var peticion = multipart("/api/solicitudes")
                .file(new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes()));
        return (MockMultipartHttpServletRequestBuilder) (token == null ? peticion
                : peticion.header("Authorization", "Bearer " + token));
    }

    private static final String TEMA = "Derivadas de funciones compuestas";
    private static final String DESC = "Quiero entender la regla de la cadena con ejemplos paso a paso para el parcial";

    @Test
    void estudiantePublicaConArchivoYLaConsultaEnDetalleYMias() throws Exception {
        String token = tokenDe("e@u.edu.co", "1098765432", Rol.ESTUDIANTE);

        String cuerpo = mvc.perform(publicacion(token, datos(TEMA, DESC, "MIN_60"))
                        .file(new MockMultipartFile("archivos", "guia.pdf", "application/pdf",
                                ArchivosDePrueba.pdf().getBytes())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PUBLICADA"))
                .andExpect(jsonPath("$.archivos[0].nombre").value("guia.pdf"))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(cuerpo).get("id").asText();

        mvc.perform(get("/api/solicitudes/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tema").value(TEMA));
        mvc.perform(get("/api/solicitudes/mias").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void sinTokenDa401YTutorNoPuedePublicarDa403() throws Exception {
        String tutor = tokenDe("t@u.edu.co", "1012345678", Rol.TUTOR);
        mvc.perform(publicacion(null, datos(TEMA, DESC, "MIN_60"))).andExpect(status().isUnauthorized());
        mvc.perform(publicacion(tutor, datos(TEMA, DESC, "MIN_60"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/solicitudes/mias").header("Authorization", "Bearer " + tutor))
                .andExpect(status().isForbidden());
    }

    @Test
    void errores422DeIntegridadYFraude() throws Exception {
        String token = tokenDe("e@u.edu.co", "1098765432", Rol.ESTUDIANTE);
        mvc.perform(publicacion(token, datos("Matemáticas", "necesito ayuda con matemáticas", "MIN_60")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DESCRIPCION_INSUFICIENTE"));
        mvc.perform(publicacion(token, datos("Parcial", "Debo resolver examen parcial en vivo con ayuda de alguien", "MIN_60")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("FRAUDE_ACADEMICO"));
    }

    @Test
    void duracionDe90YCamposFaltantesDan400() throws Exception {
        String token = tokenDe("e@u.edu.co", "1098765432", Rol.ESTUDIANTE);
        mvc.perform(publicacion(token, datos(TEMA, DESC, "MIN_90"))).andExpect(status().isBadRequest());
        mvc.perform(publicacion(token, "{\"asignaturaId\":\"a1\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void cualquierUsuarioBuscaConFiltrosYElOrdenInvalidoDa400() throws Exception {
        String tutor = tokenDe("t@u.edu.co", "1012345678", Rol.TUTOR);
        mvc.perform(get("/api/solicitudes").param("modalidad", "VIRTUAL").param("precioMax", "50000")
                        .param("tema", "cálculo").param("orden", "precioAsc").param("tamano", "5")
                        .header("Authorization", "Bearer " + tutor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isArray())
                .andExpect(jsonPath("$.tamano").value(5));
        ArgumentCaptor<FiltroSolicitudes> captor = ArgumentCaptor.forClass(FiltroSolicitudes.class);
        verify(repoSolicitudes).buscar(captor.capture(), any(Pageable.class));
        assertEquals("cálculo", captor.getValue().tema());
        assertEquals(FiltroSolicitudes.Orden.PRECIO_ASC, captor.getValue().orden());
        assertEquals(0, new java.math.BigDecimal("50000").compareTo(captor.getValue().precioMax()));

        mvc.perform(get("/api/solicitudes").param("orden", "raro").header("Authorization", "Bearer " + tutor))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/solicitudes")).andExpect(status().isUnauthorized());
    }

    @Test
    void editarYCancelarSoloElDuenoYNoTrasFormalizar() throws Exception {
        String dueno = tokenDe("e@u.edu.co", "1098765432", Rol.ESTUDIANTE);
        String otro = tokenDe("o@u.edu.co", "1011111111", Rol.ESTUDIANTE);
        String id = mapper.readTree(mvc.perform(publicacion(dueno, datos(TEMA, DESC, "MIN_60")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();

        mvc.perform(put("/api/solicitudes/" + id).header("Authorization", "Bearer " + otro)
                        .contentType(MediaType.APPLICATION_JSON).content(datos(TEMA, DESC, "MIN_30")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/solicitudes/" + id).header("Authorization", "Bearer " + dueno)
                        .contentType(MediaType.APPLICATION_JSON).content(datos(TEMA, DESC, "MIN_30")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duracion").value("MIN_30"));
        mvc.perform(put("/api/solicitudes/" + id).header("Authorization", "Bearer " + dueno)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tema\":\"solo tema\"}"))
                .andExpect(status().isBadRequest());

        solicitudes.get(id).setEstado(EstadoSolicitud.FORMALIZADA);
        mvc.perform(post("/api/solicitudes/" + id + "/cancelar").header("Authorization", "Bearer " + dueno))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SOLICITUD_FORMALIZADA"));
        mvc.perform(put("/api/solicitudes/" + id).header("Authorization", "Bearer " + dueno)
                        .contentType(MediaType.APPLICATION_JSON).content(datos(TEMA, DESC, "MIN_60")))
                .andExpect(status().isConflict());

        solicitudes.get(id).setEstado(EstadoSolicitud.PUBLICADA);
        mvc.perform(post("/api/solicitudes/" + id + "/cancelar").header("Authorization", "Bearer " + dueno))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));
        mvc.perform(get("/api/solicitudes/" + id).header("Authorization",
                        "Bearer " + tokenDe("t@u.edu.co", "1012345678", Rol.TUTOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adjuntarArchivosPorSeparado() throws Exception {
        String dueno = tokenDe("e@u.edu.co", "1098765432", Rol.ESTUDIANTE);
        String id = mapper.readTree(mvc.perform(publicacion(dueno, datos(TEMA, DESC, "MIN_60")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(multipart("/api/solicitudes/" + id + "/archivos")
                        .file(new MockMultipartFile("archivos", "apoyo.png", "image/png", ArchivosDePrueba.png().getBytes()))
                        .header("Authorization", "Bearer " + dueno))
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].nombre").value("apoyo.png"));
    }
}
