package com.tutofast.controlador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tutofast.AlmacenamientoEnMemoria;
import com.tutofast.RepositorioAuditoriaEnMemoria;
import com.tutofast.RepositorioUsuarioEnMemoria;
import com.tutofast.dto.InicioSesionDTO;
import com.tutofast.dto.RegistroDTO;
import com.tutofast.excepcion.ManejadorGlobalExcepciones;
import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.NivelVerificacion;
import com.tutofast.modelo.PerfilTutor;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.TipoTutor;
import com.tutofast.modelo.Usuario;
import com.tutofast.repositorio.FiltroTutores;
import com.tutofast.repositorio.RepositorioAsignatura;
import com.tutofast.repositorio.RepositorioCalificacion;
import com.tutofast.repositorio.RepositorioUsuario;
import com.tutofast.seguridad.FiltroAutenticacionJwt;
import com.tutofast.seguridad.InterceptorRoles;
import com.tutofast.seguridad.JwtUtil;
import com.tutofast.seguridad.ResolvedorUsuarioAutenticado;
import com.tutofast.servicio.ServicioArchivos;
import com.tutofast.servicio.ServicioIdentidad;
import com.tutofast.servicio.ServicioNotificaciones;

/** {@code GET /api/tutores}, {@code GET /api/tutores/{id}} y {@code GET /api/usuarios/{id}/resenas}. */
class TutorControladorTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Map<String, Usuario> usuarios = new LinkedHashMap<>();
    private RepositorioUsuario repoUsuarios;
    private ServicioIdentidad identidad;
    private MockMvc mvc;
    private String token;
    private String tutorId;

    @BeforeEach
    void preparar() {
        repoUsuarios = RepositorioUsuarioEnMemoria.crear(usuarios);
        when(repoUsuarios.existsById(anyString())).thenAnswer(inv -> usuarios.containsKey(inv.<String>getArgument(0)));
        when(repoUsuarios.findAllById(any())).thenAnswer(inv -> {
            List<Usuario> lista = new ArrayList<>();
            for (Object id : (Iterable<?>) inv.getArgument(0)) {
                if (usuarios.containsKey(id)) {
                    lista.add(usuarios.get(id));
                }
            }
            return lista;
        });
        when(repoUsuarios.buscarTutores(any(FiltroTutores.class), any(Pageable.class))).thenAnswer(inv -> {
            List<Usuario> tutores = usuarios.values().stream().filter(u -> u.tieneRol(Rol.TUTOR)).toList();
            return new PageImpl<>(tutores, inv.<Pageable>getArgument(1), tutores.size());
        });
        identidad = new ServicioIdentidad(repoUsuarios, new BCryptPasswordEncoder(4),
                new JwtUtil("secreto-de-prueba-con-mas-de-32-caracteres-ok", Duration.ofHours(8)),
                RepositorioAuditoriaEnMemoria.servicio(new ArrayList<>()),
                new ServicioArchivos(new AlmacenamientoEnMemoria(), List.of()), mock(ServicioNotificaciones.class),
                mock(RepositorioAsignatura.class), mock(RepositorioCalificacion.class));
        mvc = MockMvcBuilders.standaloneSetup(new TutorControlador(identidad))
                .setControllerAdvice(new ManejadorGlobalExcepciones())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setCustomArgumentResolvers(new ResolvedorUsuarioAutenticado())
                .addInterceptors(new InterceptorRoles())
                .addFilters(new FiltroAutenticacionJwt(identidad, mapper))
                .build();

        identidad.registrarUsuario(new RegistroDTO("Camila Rojas", "e@u.edu.co", "1098765432", "Segura#2026",
                Rol.ESTUDIANTE, true, null, 4));
        token = identidad.iniciarSesion(new InicioSesionDTO("e@u.edu.co", "Segura#2026")).token();
        identidad.registrarUsuario(new RegistroDTO("Laura Martínez", "t@u.edu.co", "1012345678", "Segura#2026",
                Rol.TUTOR, true, null, null));
        Usuario tutor = usuarios.values().stream().filter(u -> u.tieneRol(Rol.TUTOR)).findFirst().orElseThrow();
        tutorId = tutor.getId();
        PerfilTutor p = tutor.getPerfilTutor();
        p.setPresentacion("Monitora de cálculo");
        p.setTipoTutor(TipoTutor.MONITOR_CERTIFICADO);
        p.setModalidades(new ArrayList<>(List.of(Modalidad.VIRTUAL, Modalidad.PRESENCIAL)));
        p.setNivelVerificacion(NivelVerificacion.VERIFICADO);
        p.setPromedioCalificacion(new BigDecimal("4.75"));
        p.setServiciosCompletados(12);
        p.setCuentaBancariaCobro("Bancolombia ahorros ****4821");
    }

    @Test
    void buscarRequiereSesionYNoExponeDatosPrivados() throws Exception {
        mvc.perform(get("/api/tutores")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/tutores").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(tutorId))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Laura Martínez"))
                .andExpect(jsonPath("$.contenido[0].verificado").value(true))
                .andExpect(jsonPath("$.contenido[0].promedioCalificacion").value(4.75))
                .andExpect(jsonPath("$.contenido[0].serviciosCompletados").value(12))
                .andExpect(jsonPath("$.contenido[0].cuentaBancariaCobro").doesNotExist())
                .andExpect(jsonPath("$.contenido[0].correo").doesNotExist());
    }

    @Test
    void losFiltrosLlegaranAlRepositorio() throws Exception {
        mvc.perform(get("/api/tutores").param("asignaturaId", "asig1").param("modalidad", "PRESENCIAL")
                        .param("disponibilidad", "L-J").param("calificacionMin", "4.5")
                        .param("soloVerificados", "true").param("orden", "servicios").param("tamano", "5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tamano").value(5));

        ArgumentCaptor<FiltroTutores> captor = ArgumentCaptor.forClass(FiltroTutores.class);
        verify(repoUsuarios).buscarTutores(captor.capture(), any(Pageable.class));
        FiltroTutores f = captor.getValue();
        assertEquals(List.of("asig1"), List.copyOf(f.asignaturaIds()));
        assertEquals(Modalidad.PRESENCIAL, f.modalidad());
        assertEquals("L-J", f.disponibilidad());
        assertEquals(0, new BigDecimal("4.5").compareTo(f.calificacionMin()));
        assertTrue(f.soloVerificados());
        assertEquals(FiltroTutores.Orden.SERVICIOS, f.orden());
    }

    @Test
    void parametrosInvalidosDan400() throws Exception {
        mvc.perform(get("/api/tutores").param("orden", "raro").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tutores").param("calificacionMin", "7").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tutores").param("modalidad", "HOLOGRAMA").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void perfilPublicoSinDatosPrivadosYNoEncontrado() throws Exception {
        mvc.perform(get("/api/tutores/" + tutorId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Laura Martínez"))
                .andExpect(jsonPath("$.declarado.presentacion").value("Monitora de cálculo"))
                .andExpect(jsonPath("$.verificado.nivelVerificacion").value("VERIFICADO"))
                .andExpect(jsonPath("$.promedioCalificacion").value(4.75))
                .andExpect(jsonPath("$.resenas").isArray())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("4821"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("1012345678"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("t@u.edu.co"))));

        mvc.perform(get("/api/tutores/no-existe").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/tutores/" + tutorId)).andExpect(status().isUnauthorized());
    }

    @Test
    void resenasDeUnUsuario() throws Exception {
        mvc.perform(get("/api/usuarios/" + tutorId + "/resenas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/usuarios/no-existe/resenas").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/usuarios/" + tutorId + "/resenas")).andExpect(status().isUnauthorized());
    }
}
