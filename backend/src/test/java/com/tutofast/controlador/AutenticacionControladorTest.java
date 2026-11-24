package com.tutofast.controlador;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mongodb.client.MongoDatabase;
import com.tutofast.RepositorioUsuarioEnMemoria;
import com.tutofast.excepcion.ManejadorGlobalExcepciones;
import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.Usuario;
import com.tutofast.seguridad.FiltroAutenticacionJwt;
import com.tutofast.seguridad.InterceptorRoles;
import com.tutofast.seguridad.JwtUtil;
import com.tutofast.seguridad.ResolvedorUsuarioAutenticado;
import com.tutofast.servicio.ServicioIdentidad;

/**
 * Prueba de integración ligera: filtro JWT + interceptor de roles + controladores + manejador de
 * errores + servicio real, con un repositorio en memoria (sin MongoDB).
 */
class AutenticacionControladorTest {

    private static final String REGISTRO_ESTUDIANTE = """
            {"nombre":"Carlos Gómez","correo":"estudiante@universidad.edu.co","documento":"1098765432",
             "clave":"Segura#2026","rol":"ESTUDIANTE","aceptaTratamientoDatos":true,"semestre":4}""";

    private final Map<String, Usuario> almacen = new LinkedHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        var repoUsuarios = RepositorioUsuarioEnMemoria.crear(almacen);
        ServicioIdentidad servicio = new ServicioIdentidad(repoUsuarios,
                new BCryptPasswordEncoder(4),
                new JwtUtil("secreto-de-prueba-con-mas-de-32-caracteres-ok", Duration.ofHours(8)),
                new com.tutofast.servicio.ServicioAuditoria(
                        com.tutofast.RepositorioAuditoriaEnMemoria.crear(new java.util.ArrayList<>())),
                new com.tutofast.servicio.ServicioArchivos(new com.tutofast.AlmacenamientoEnMemoria(), java.util.List.of()));

        MongoTemplate plantilla = mock(MongoTemplate.class);
        MongoDatabase bd = mock(MongoDatabase.class);
        when(plantilla.getDb()).thenReturn(bd);
        when(bd.runCommand(any(org.bson.conversions.Bson.class))).thenReturn(new Document("ok", 1));

        mvc = MockMvcBuilders
                .standaloneSetup(new AutenticacionControlador(servicio), new UsuarioControlador(servicio),
                        new SaludControlador(plantilla))
                .setControllerAdvice(new ManejadorGlobalExcepciones())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setCustomArgumentResolvers(new ResolvedorUsuarioAutenticado())
                .addInterceptors(new InterceptorRoles())
                .addFilters(new FiltroAutenticacionJwt(servicio, mapper))
                .build();
    }

    private ResultActions json(String metodo, String ruta, String cuerpo, String token) throws Exception {
        var peticion = "PUT".equals(metodo) ? put(ruta) : post(ruta);
        peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(peticion);
    }

    private String registrarYLoguear(String registroJson, String correo) throws Exception {
        json("POST", "/api/auth/registro", registroJson, null).andExpect(status().isCreated());
        String respuesta = json("POST", "/api/auth/iniciar-sesion",
                "{\"correo\":\"" + correo + "\",\"clave\":\"Segura#2026\"}", null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(respuesta).get("token").asText();
    }

    @Test
    void saludEsPublico() throws Exception {
        mvc.perform(get("/api/salud")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("UP"));
    }

    @Test
    void registroExitosoDevuelve201ConMensajeDeTutoFast() throws Exception {
        json("POST", "/api/auth/registro", REGISTRO_ESTUDIANTE, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value("Registro exitoso en TutoFast"))
                .andExpect(jsonPath("$.usuarioId").isNotEmpty());
    }

    @Test
    void registroSinConsentimientoDevuelve422() throws Exception {
        json("POST", "/api/auth/registro", REGISTRO_ESTUDIANTE.replace("true", "false"), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"))
                .andExpect(jsonPath("$.mensaje").value(containsString("Ley 1581 de 2012")));
    }

    @Test
    void registroConDatosInvalidosDevuelve400ConDetalles() throws Exception {
        json("POST", "/api/auth/registro",
                "{\"nombre\":\"\",\"correo\":\"no-es-correo\",\"documento\":\"abc\",\"clave\":\"corta\","
                        + "\"rol\":\"ESTUDIANTE\",\"aceptaTratamientoDatos\":true}", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[*].campo").value(hasItem("correo")))
                .andExpect(jsonPath("$.detalles[*].campo").value(hasItem("clave")));
    }

    @Test
    void registroDuplicadoDevuelve409() throws Exception {
        json("POST", "/api/auth/registro", REGISTRO_ESTUDIANTE, null).andExpect(status().isCreated());
        json("POST", "/api/auth/registro", REGISTRO_ESTUDIANTE, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
    }

    @Test
    void loginDevuelveTokenYUsuarioSinDatosSensibles() throws Exception {
        json("POST", "/api/auth/registro", REGISTRO_ESTUDIANTE, null).andExpect(status().isCreated());
        json("POST", "/api/auth/iniciar-sesion",
                "{\"correo\":\"estudiante@universidad.edu.co\",\"clave\":\"Segura#2026\"}", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiraEn").isNotEmpty())
                .andExpect(jsonPath("$.usuario.roles[0]").value("ESTUDIANTE"))
                .andExpect(jsonPath("$.usuario.estadoCuenta").value("ACTIVA"))
                .andExpect(jsonPath("$.usuario.claveHash").doesNotExist())
                .andExpect(jsonPath("$.usuario.documento").doesNotExist());
    }

    @Test
    void loginConClaveIncorrectaDevuelve401() throws Exception {
        json("POST", "/api/auth/registro", REGISTRO_ESTUDIANTE, null).andExpect(status().isCreated());
        json("POST", "/api/auth/iniciar-sesion",
                "{\"correo\":\"estudiante@universidad.edu.co\",\"clave\":\"Mala#2026\"}", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value("Correo o contraseña incorrectos"));
    }

    @Test
    void rutaProtegidaSinTokenDevuelve401() throws Exception {
        mvc.perform(get("/api/auth/yo")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(get("/api/usuarios/yo")).andExpect(status().isUnauthorized());
    }

    @Test
    void rutaProtegidaConTokenInvalidoDevuelve401() throws Exception {
        mvc.perform(get("/api/auth/yo").header("Authorization", "Bearer token.falso.aqui"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/yo").header("Authorization", "Basic abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rutaProtegidaConTokenValidoDevuelveElUsuario() throws Exception {
        String token = registrarYLoguear(REGISTRO_ESTUDIANTE, "estudiante@universidad.edu.co");

        mvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("estudiante@universidad.edu.co"));
        mvc.perform(get("/api/usuarios/yo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documento").value("1098765432"))
                .andExpect(jsonPath("$.perfilEstudiante.semestre").value(4))
                .andExpect(jsonPath("$.claveHash").doesNotExist());
    }

    @Test
    void estudiantePuedeActualizarSuPerfilPeroNoElDeTutor() throws Exception {
        String token = registrarYLoguear(REGISTRO_ESTUDIANTE, "estudiante@universidad.edu.co");

        json("PUT", "/api/usuarios/yo/perfil-estudiante",
                "{\"programaId\":\"6741a0c1e4b0a1b2c3d4e103\",\"semestre\":7}", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfilEstudiante.semestre").value(7));
        json("PUT", "/api/usuarios/yo/perfil-tutor",
                "{\"presentacion\":\"Hola\",\"tipoTutor\":\"PAR\",\"modalidades\":[\"VIRTUAL\"]}", token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("NO_AUTORIZADO"));
    }

    @Test
    void tutorActualizaPerfilYApareceNoVerificado() throws Exception {
        String registro = REGISTRO_ESTUDIANTE.replace("ESTUDIANTE", "TUTOR")
                .replace("estudiante@", "tutor@");
        String token = registrarYLoguear(registro, "tutor@universidad.edu.co");

        json("PUT", "/api/usuarios/yo/perfil-tutor",
                "{\"presentacion\":\"Monitora de cálculo\",\"tipoTutor\":\"MONITOR_CERTIFICADO\","
                        + "\"modalidades\":[\"VIRTUAL\",\"PRESENCIAL\"],\"areasDeInteres\":[\"Cálculo\"]}", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfilTutor.presentacion").value("Monitora de cálculo"))
                .andExpect(jsonPath("$.perfilTutor.etiquetaVerificacion").value("Tutor Registrado (No Verificado)"));
        json("PUT", "/api/usuarios/yo/perfil-tutor", "{\"presentacion\":\"\"}", token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void usuarioActivaRolAdicionalYDuplicadoDa409() throws Exception {
        String token = registrarYLoguear(REGISTRO_ESTUDIANTE, "estudiante@universidad.edu.co");

        json("POST", "/api/usuarios/yo/roles", "{\"rol\":\"TUTOR\"}", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.hasSize(2)));
        json("POST", "/api/usuarios/yo/roles", "{\"rol\":\"TUTOR\"}", token)
                .andExpect(status().isConflict());
        json("POST", "/api/usuarios/yo/roles", "{\"rol\":\"ADMINISTRADOR\"}", token)
                .andExpect(status().isForbidden());
    }

    @Test
    void cuentaSuspendidaPierdeAccesoAunConTokenVigente() throws Exception {
        String token = registrarYLoguear(REGISTRO_ESTUDIANTE, "estudiante@universidad.edu.co");
        almacen.values().forEach(u -> u.setEstadoCuenta(EstadoCuenta.SUSPENDIDA));

        mvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("NO_AUTORIZADO"));
    }
}
