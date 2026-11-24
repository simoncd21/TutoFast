package com.tutofast.controlador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.test.web.servlet.ResultActions;
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
import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.EstadoPago;
import com.tutofast.modelo.EstadoServicio;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.ServicioTutoria;
import com.tutofast.modelo.SolicitudTutoria;
import com.tutofast.modelo.Usuario;
import com.tutofast.seguridad.FiltroAutenticacionJwt;
import com.tutofast.seguridad.InterceptorRoles;
import com.tutofast.seguridad.JwtUtil;
import com.tutofast.seguridad.ResolvedorUsuarioAutenticado;
import com.tutofast.servicio.ContextoReportes;
import com.tutofast.servicio.ServicioArchivos;
import com.tutofast.servicio.ServicioIdentidad;
import com.tutofast.servicio.ServicioNotificaciones;

/**
 * Reportes, sanciones y apelaciones a través del filtro JWT, los roles, la sesión restringida de las cuentas
 * sancionadas y el manejador global de errores (con repositorios en memoria).
 */
class ReportesControladorTest {

    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Map<String, Usuario> usuarios = new LinkedHashMap<>();
    private final Map<String, SolicitudTutoria> solicitudes = new LinkedHashMap<>();
    private EscenarioNegociacion escenario;
    private ServicioIdentidad identidad;
    private MockMvc mvc;
    private String tokenEstudiante;
    private String tokenTutor;
    private String tokenTercero;
    private String tokenAdmin;
    private String estudianteId;
    private String tutorId;
    private ServicioTutoria servicio;

    @BeforeEach
    void preparar() {
        var repoUsuarios = RepositorioUsuarioEnMemoria.crear(usuarios);
        escenario = new EscenarioNegociacion(repoUsuarios, usuarios, solicitudes);
        identidad = new ServicioIdentidad(repoUsuarios, new BCryptPasswordEncoder(4),
                new JwtUtil("secreto-de-prueba-con-mas-de-32-caracteres-ok", Duration.ofHours(8)), escenario.auditoria,
                new ServicioArchivos(new AlmacenamientoEnMemoria(), List.of()), mock(ServicioNotificaciones.class),
                mock(com.tutofast.repositorio.RepositorioAsignatura.class),
                mock(com.tutofast.repositorio.RepositorioCalificacion.class));
        var archivos = new ServicioArchivos(escenario.almacenamiento,
                List.of(new ContextoReportes(escenario.repoReportes, escenario.repoSanciones)));

        mvc = MockMvcBuilders.standaloneSetup(new ReporteControlador(escenario.servicioReportes),
                        new ArchivoControlador(archivos), new UsuarioControlador(identidad),
                        new AutenticacionControlador(identidad),
                        new NotificacionControlador(escenario.notificaciones),
                        new MensajeControlador(escenario.servicioMensajeria),
                        new AdministracionControlador(identidad, escenario.auditoria, escenario.servicioMensajeria,
                                escenario.servicioReputacion, escenario.servicioReportes))
                .setControllerAdvice(new ManejadorGlobalExcepciones())
                .setMessageConverters(new org.springframework.http.converter.ResourceHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(mapper))
                .setCustomArgumentResolvers(new ResolvedorUsuarioAutenticado())
                .addInterceptors(new InterceptorRoles())
                .addFilters(new FiltroAutenticacionJwt(identidad, mapper))
                .build();

        tokenEstudiante = tokenDe("e@u.edu.co", "1098765432", Rol.ESTUDIANTE);
        tokenTutor = tokenDe("t@u.edu.co", "1012345678", Rol.TUTOR);
        tokenTercero = tokenDe("t2@u.edu.co", "1022222222", Rol.TUTOR);
        identidad.crearAdministradorSiNoExiste("admin@tutofast.co", "Admin#2026");
        tokenAdmin = identidad.iniciarSesion(new InicioSesionDTO("admin@tutofast.co", "Admin#2026")).token();
        estudianteId = idDe("e@u.edu.co");
        tutorId = idDe("t@u.edu.co");
        servicio = escenario.crearServicio(usuarios.get(estudianteId), usuarios.get(tutorId), EstadoServicio.EN_DESARROLLO,
                Instant.now().minus(Duration.ofHours(1)), "40000");
    }

    // ---------------------------------------------------------------- utilidades

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

    private static MockMultipartFile datos(String json) {
        return new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile evidencia(String nombre) {
        return new MockMultipartFile("evidencias", nombre, "application/pdf", PDF);
    }

    private String json(String tipo, String motivo) {
        return "{\"tipo\":\"" + tipo + "\",\"motivo\":\"" + motivo + "\",\"servicioId\":\"" + servicio.getId() + "\"}";
    }

    private ResultActions radicar(String token, String cuerpo, MockMultipartFile... evidencias) throws Exception {
        var peticion = multipart("/api/reportes").file(datos(cuerpo));
        for (MockMultipartFile f : evidencias) {
            peticion.file(f);
        }
        return mvc.perform(con(peticion, token, null));
    }

    private String radicarInasistencia() throws Exception {
        return idDe(radicar(tokenEstudiante, json("INASISTENCIA", "El tutor no se presentó tras 20 minutos"))
                .andExpect(status().isCreated()));
    }

    private String idDe(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private ResultActions resolver(String reporteId, String cuerpo) throws Exception {
        return mvc.perform(con(post("/api/admin/reportes/" + reporteId + "/resolver"), tokenAdmin, cuerpo));
    }

    private static String suspension() {
        return "{\"tipo\":\"SUSPENSION_TEMPORAL\",\"fin\":\"" + Instant.now().plus(Duration.ofDays(14)) + "\"}";
    }

    /** Radica, reembolsa y suspende al tutor; devuelve el id de la sanción. */
    private String tutorSancionado() throws Exception {
        String reporteId = radicarInasistencia();
        resolver(reporteId, "{\"decision\":\"A_FAVOR_REPORTANTE\",\"accionPago\":\"REEMBOLSAR\","
                + "\"observacion\":\"Inasistencia comprobada\",\"sancion\":" + suspension() + "}")
                .andExpect(status().isOk());
        return escenario.sanciones.values().iterator().next().getId();
    }

    private ResultActions apelar(String token, String sancionId, String descargo, MockMultipartFile... evidencias) throws Exception {
        var peticion = multipart("/api/sanciones/" + sancionId + "/apelaciones")
                .file(datos("{\"descargo\":\"" + descargo + "\"}"));
        for (MockMultipartFile f : evidencias) {
            peticion.file(f);
        }
        return mvc.perform(con(peticion, token, null));
    }

    // ---------------------------------------------------------------- radicación

    @Test
    void radicaUnReporteConEvidenciasYCongelaElPago() throws Exception {
        radicar(tokenEstudiante, json("INASISTENCIA", "El tutor no se presentó tras 20 minutos"),
                evidencia("captura1.pdf"), evidencia("captura2.pdf"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"))
                .andExpect(jsonPath("$.tipo").value("INASISTENCIA"))
                .andExpect(jsonPath("$.categoria").value("INCUMPLIMIENTO_SERVICIO"))
                .andExpect(jsonPath("$.reportante.id").value(estudianteId))
                .andExpect(jsonPath("$.denunciado.id").value(tutorId))
                .andExpect(jsonPath("$.servicioId").value(servicio.getId()))
                .andExpect(jsonPath("$.evidencias.length()").value(2));

        assertEquals(EstadoServicio.EN_REVISION, servicio.getEstado());
        assertEquals(EstadoPago.CONGELADO, escenario.pagos.values().iterator().next().getEstado());
    }

    @Test
    void radicarExigeSesionYRolDeEstudianteOTutor() throws Exception {
        String cuerpo = json("INASISTENCIA", "El tutor no se presentó tras 20 minutos");

        radicar(null, cuerpo).andExpect(status().isUnauthorized());
        radicar(tokenAdmin, cuerpo).andExpect(status().isForbidden());
        // un tercero que no participa en el servicio
        radicar(tokenTercero, cuerpo).andExpect(status().isForbidden());
        assertEquals(EstadoPago.RETENIDO, escenario.pagos.values().iterator().next().getEstado());
    }

    @Test
    void validaElCuerpoDelReporte() throws Exception {
        radicar(tokenEstudiante, "{\"motivo\":\"El tutor no se presentó\",\"servicioId\":\"" + servicio.getId() + "\"}")
                .andExpect(status().isBadRequest());
        radicar(tokenEstudiante, json("INASISTENCIA", "corto")).andExpect(status().isBadRequest());
        radicar(tokenEstudiante, json("INASISTENCIA", " ")).andExpect(status().isBadRequest());
        radicar(tokenEstudiante, json("TIPO_INVENTADO", "El tutor no se presentó tras 20 minutos"))
                .andExpect(status().isBadRequest());
        // un tipo que exige servicio no se radica sin él
        radicar(tokenEstudiante, "{\"tipo\":\"INASISTENCIA\",\"motivo\":\"El tutor no se presentó tras 20 minutos\","
                + "\"denunciadoId\":\"" + tutorId + "\"}").andExpect(status().isUnprocessableEntity());
        // autorreporte
        radicar(tokenEstudiante, "{\"tipo\":\"ACOSO\",\"motivo\":\"Me reporto a mí mismo, error\",\"denunciadoId\":\""
                + estudianteId + "\"}").andExpect(status().isForbidden());
        // más de 5 evidencias
        radicar(tokenEstudiante, json("INASISTENCIA", "El tutor no se presentó tras 20 minutos"),
                evidencia("1.pdf"), evidencia("2.pdf"), evidencia("3.pdf"), evidencia("4.pdf"), evidencia("5.pdf"),
                evidencia("6.pdf")).andExpect(status().isBadRequest());
        // formato no permitido
        radicar(tokenEstudiante, json("INASISTENCIA", "El tutor no se presentó tras 20 minutos"),
                new MockMultipartFile("evidencias", "virus.exe", "application/octet-stream", new byte[] {1, 2, 3}))
                .andExpect(status().isUnsupportedMediaType());
        assertEquals(0, escenario.reportes.size());
        assertEquals(0, escenario.almacenamiento.cantidad());
    }

    @Test
    void unReporteDuplicadoDa409() throws Exception {
        radicarInasistencia();

        radicar(tokenEstudiante, json("INASISTENCIA", "El tutor no se presentó tras 20 minutos"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("REPORTE_DUPLICADO"));
    }

    @Test
    void misReportesSeparaRadicadosYRecibidos() throws Exception {
        radicarInasistencia();

        mvc.perform(con(get("/api/reportes/mios"), tokenEstudiante, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.radicados.length()").value(1))
                .andExpect(jsonPath("$.recibidos.length()").value(0))
                .andExpect(jsonPath("$.radicados[0].reportante.nombre").value("Persona Prueba"));
        mvc.perform(con(get("/api/reportes/mios"), tokenTutor, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.radicados.length()").value(0))
                .andExpect(jsonPath("$.recibidos.length()").value(1))
                .andExpect(jsonPath("$.recibidos[0].reportante").doesNotExist())
                .andExpect(jsonPath("$.recibidos[0].evidencias.length()").value(0));
        mvc.perform(con(get("/api/reportes/mios"), tokenAdmin, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/reportes/mios"), null, null)).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- administración

    @Test
    void soloElAdministradorGestionaReportesSancionesYApelaciones() throws Exception {
        String reporteId = radicarInasistencia();
        String cuerpo = "{\"decision\":\"DESESTIMADO\",\"observacion\":\"Sin pruebas\"}";

        for (String token : List.of(tokenEstudiante, tokenTutor, tokenTercero)) {
            mvc.perform(con(get("/api/admin/reportes"), token, null)).andExpect(status().isForbidden());
            mvc.perform(con(get("/api/admin/reportes/" + reporteId), token, null)).andExpect(status().isForbidden());
            mvc.perform(con(post("/api/admin/reportes/" + reporteId + "/resolver"), token, cuerpo)).andExpect(status().isForbidden());
            mvc.perform(con(get("/api/admin/sanciones"), token, null)).andExpect(status().isForbidden());
            mvc.perform(con(post("/api/admin/sanciones/x/apelaciones/y/resolver"), token,
                    "{\"resultado\":\"REVOCADA\",\"observacion\":\"x\"}")).andExpect(status().isForbidden());
        }
        mvc.perform(con(get("/api/admin/reportes"), null, null)).andExpect(status().isUnauthorized());
        assertEquals(EstadoPago.CONGELADO, escenario.pagos.values().iterator().next().getEstado());
    }

    @Test
    void elAdministradorConsultaLaColaYElExpediente() throws Exception {
        String reporteId = radicarInasistencia();

        mvc.perform(con(get("/api/admin/reportes"), tokenAdmin, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(reporteId));
        mvc.perform(con(get("/api/admin/reportes?estado=EN_REVISION"), tokenAdmin, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(con(get("/api/admin/reportes?estado=RESUELTO"), tokenAdmin, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));
        mvc.perform(con(get("/api/admin/reportes?estado=INVENTADO"), tokenAdmin, null)).andExpect(status().isBadRequest());
        mvc.perform(con(get("/api/admin/reportes/" + reporteId), tokenAdmin, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.reporte.id").value(reporteId))
                .andExpect(jsonPath("$.servicio.id").value(servicio.getId()))
                .andExpect(jsonPath("$.pago.estado").value("CONGELADO"))
                .andExpect(jsonPath("$.historialChat").isArray())
                .andExpect(jsonPath("$.sanciones").isEmpty());
        mvc.perform(con(get("/api/admin/reportes/no-existe"), tokenAdmin, null)).andExpect(status().isNotFound());
    }

    @Test
    void resuelveConReembolsoYUnSegundoIntentoDa409() throws Exception {
        String reporteId = radicarInasistencia();

        resolver(reporteId, "{\"decision\":\"A_FAVOR_REPORTANTE\",\"accionPago\":\"REEMBOLSAR\",\"observacion\":\"Comprobado\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RESUELTO"))
                .andExpect(jsonPath("$.resolucion.decision").value("A_FAVOR_REPORTANTE"))
                .andExpect(jsonPath("$.resolucion.accionPago").value("REEMBOLSAR"));
        assertEquals(EstadoPago.REEMBOLSADO, escenario.pagos.values().iterator().next().getEstado());
        assertEquals(EstadoServicio.CANCELADO, servicio.getEstado());

        resolver(reporteId, "{\"decision\":\"DESESTIMADO\",\"observacion\":\"Otra vez\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("REPORTE_YA_RESUELTO"));
    }

    @Test
    void validaElCuerpoDeLaResolucion() throws Exception {
        String reporteId = radicarInasistencia();

        resolver(reporteId, "{}").andExpect(status().isBadRequest());
        resolver(reporteId, "{\"decision\":\"DESESTIMADO\"}").andExpect(status().isBadRequest());
        resolver(reporteId, "{\"decision\":\"DESESTIMADO\",\"observacion\":\"  \"}").andExpect(status().isBadRequest());
        resolver(reporteId, "{\"decision\":\"INVENTADA\",\"observacion\":\"x\"}").andExpect(status().isBadRequest());
        resolver(reporteId, "{\"decision\":\"A_FAVOR_REPORTANTE\",\"observacion\":\"x\",\"sancion\":{}}")
                .andExpect(status().isBadRequest());
        resolver(reporteId, "{\"decision\":\"DESESTIMADO\",\"accionPago\":\"REEMBOLSAR\",\"observacion\":\"x\"}")
                .andExpect(status().isUnprocessableEntity());
        resolver(reporteId, "{\"decision\":\"A_FAVOR_REPORTANTE\",\"observacion\":\"x\","
                + "\"sancion\":{\"tipo\":\"SUSPENSION_TEMPORAL\"}}").andExpect(status().isUnprocessableEntity());
        resolver("no-existe", "{\"decision\":\"DESESTIMADO\",\"observacion\":\"x\"}").andExpect(status().isNotFound());
        assertEquals(EstadoPago.CONGELADO, escenario.pagos.values().iterator().next().getEstado());
    }

    // ---------------------------------------------------------------- sanción, sesión restringida y apelación

    @Test
    void elSancionadoSoloPuedeConsultarYApelarConSuSesionRestringida() throws Exception {
        String sancionId = tutorSancionado();
        assertEquals(EstadoCuenta.SUSPENDIDA, usuarios.get(tutorId).getEstadoCuenta());

        // La plataforma queda cerrada para él...
        mvc.perform(con(get("/api/usuarios/yo"), tokenTutor, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/reportes/mios"), tokenTutor, null)).andExpect(status().isForbidden());
        radicar(tokenTutor, json("DESACUERDO_ACADEMICO", "El estudiante desconoce lo acordado")).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/admin/reportes"), tokenTutor, null)).andExpect(status().isForbidden());
        // ...salvo su estado, sus sanciones, sus avisos y la apelación.
        mvc.perform(con(get("/api/auth/yo"), tokenTutor, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoCuenta").value("SUSPENDIDA"));
        mvc.perform(con(get("/api/notificaciones/conteo-no-leidas"), tokenTutor, null)).andExpect(status().isOk());
        mvc.perform(con(get("/api/sanciones/mias"), tokenTutor, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(sancionId))
                .andExpect(jsonPath("$[0].tipo").value("SUSPENSION_TEMPORAL"))
                .andExpect(jsonPath("$[0].estado").value("VIGENTE"))
                .andExpect(jsonPath("$[0].puedeApelar").value(true))
                .andExpect(jsonPath("$[0].motivo").value("El tutor no se presentó tras 20 minutos"))
                .andExpect(jsonPath("$[0].vigencia.fin").isNotEmpty());

        apelar(tokenTutor, sancionId, "Tuve una emergencia médica y adjunto la incapacidad", evidencia("incapacidad.pdf"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultado").value("PENDIENTE"))
                .andExpect(jsonPath("$.evidencias.length()").value(1));
        mvc.perform(con(get("/api/sanciones/mias"), tokenTutor, null))
                .andExpect(jsonPath("$[0].estado").value("EN_APELACION"))
                .andExpect(jsonPath("$[0].puedeApelar").value(false))
                .andExpect(jsonPath("$[0].apelaciones.length()").value(1));
        assertEquals(EstadoCuenta.SUSPENDIDA, usuarios.get(tutorId).getEstadoCuenta());
    }

    @Test
    void laApelacionExigeSerElSancionadoYUnDescargoValido() throws Exception {
        String sancionId = tutorSancionado();

        apelar(tokenEstudiante, sancionId, "No es mi sanción pero igual apelo").andExpect(status().isForbidden());
        apelar(tokenTercero, sancionId, "No es mi sanción pero igual apelo").andExpect(status().isForbidden());
        apelar(null, sancionId, "Sin sesión no se puede apelar").andExpect(status().isUnauthorized());
        apelar(tokenTutor, sancionId, "corto").andExpect(status().isBadRequest());
        apelar(tokenTutor, sancionId, " ").andExpect(status().isBadRequest());
        apelar(tokenTutor, "no-existe", "Una sanción que no existe en el sistema").andExpect(status().isNotFound());
        mvc.perform(con(multipart("/api/sanciones/" + sancionId + "/apelaciones").file(evidencia("a.pdf")), tokenTutor, null))
                .andExpect(status().isBadRequest()); // falta la parte «datos»

        apelar(tokenTutor, sancionId, "Tuve una emergencia médica y adjunto la incapacidad").andExpect(status().isCreated());
        apelar(tokenTutor, sancionId, "Segundo intento mientras la primera sigue pendiente")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("APELACION_NO_PERMITIDA"));
    }

    @Test
    void elAdministradorRevocaLaApelacionYElTutorRecuperaElAcceso() throws Exception {
        String sancionId = tutorSancionado();
        apelar(tokenTutor, sancionId, "Tuve una emergencia médica y adjunto la incapacidad").andExpect(status().isCreated());

        mvc.perform(con(get("/api/admin/sanciones?estado=EN_APELACION"), tokenAdmin, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(sancionId))
                .andExpect(jsonPath("$.contenido[0].usuario.id").value(tutorId))
                .andExpect(jsonPath("$.contenido[0].apelaciones[0].resultado").value("PENDIENTE"));
        String apelacionId = escenario.sanciones.get(sancionId).getApelaciones().get(0).getId();

        mvc.perform(con(post("/api/admin/sanciones/" + sancionId + "/apelaciones/" + apelacionId + "/resolver"), tokenAdmin,
                        "{\"resultado\":\"REVOCADA\",\"observacion\":\"La incapacidad es válida\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REVOCADA"))
                .andExpect(jsonPath("$.apelaciones[0].resultado").value("REVOCADA"))
                .andExpect(jsonPath("$.apelaciones[0].observacionResolucion").value("La incapacidad es válida"));

        assertEquals(EstadoCuenta.ACTIVA, usuarios.get(tutorId).getEstadoCuenta());
        mvc.perform(con(get("/api/usuarios/yo"), tokenTutor, null)).andExpect(status().isOk());
        mvc.perform(con(post("/api/admin/sanciones/" + sancionId + "/apelaciones/" + apelacionId + "/resolver"), tokenAdmin,
                        "{\"resultado\":\"RATIFICADA\",\"observacion\":\"Otra vez\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("APELACION_YA_RESUELTA"));
    }

    @Test
    void ratificarMantieneLaCuentaSuspendidaYValidaElCuerpo() throws Exception {
        String sancionId = tutorSancionado();
        apelar(tokenTutor, sancionId, "Tuve una emergencia médica y adjunto la incapacidad").andExpect(status().isCreated());
        String apelacionId = escenario.sanciones.get(sancionId).getApelaciones().get(0).getId();
        String ruta = "/api/admin/sanciones/" + sancionId + "/apelaciones/" + apelacionId + "/resolver";

        mvc.perform(con(post(ruta), tokenAdmin, "{}")).andExpect(status().isBadRequest());
        mvc.perform(con(post(ruta), tokenAdmin, "{\"resultado\":\"REVOCADA\"}")).andExpect(status().isBadRequest());
        mvc.perform(con(post(ruta), tokenAdmin, "{\"resultado\":\"PENDIENTE\",\"observacion\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(con(post("/api/admin/sanciones/" + sancionId + "/apelaciones/no-existe/resolver"), tokenAdmin,
                "{\"resultado\":\"REVOCADA\",\"observacion\":\"x\"}")).andExpect(status().isNotFound());
        mvc.perform(con(post(ruta), tokenAdmin, "{\"resultado\":\"RATIFICADA\",\"observacion\":\"Sin soporte suficiente\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RATIFICADA"));

        assertEquals(EstadoCuenta.SUSPENDIDA, usuarios.get(tutorId).getEstadoCuenta());
        mvc.perform(con(get("/api/usuarios/yo"), tokenTutor, null)).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- evidencias

    @Test
    void lasEvidenciasSoloLasDescargaQuienLasSubioYElAdministrador() throws Exception {
        String cuerpo = radicar(tokenEstudiante, json("INASISTENCIA", "El tutor no se presentó tras 20 minutos"),
                evidencia("captura.pdf")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String archivoId = mapper.readTree(cuerpo).get("evidencias").get(0).asText();

        mvc.perform(con(get("/api/archivos/" + archivoId), tokenEstudiante, null)).andExpect(status().isOk());
        mvc.perform(con(get("/api/archivos/" + archivoId), tokenAdmin, null)).andExpect(status().isOk());
        mvc.perform(con(get("/api/archivos/" + archivoId), tokenTutor, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/archivos/" + archivoId), tokenTercero, null)).andExpect(status().isForbidden());
    }

    @Test
    void elSancionadoConSesionRestringidaDescargaLasEvidenciasDeSuApelacion() throws Exception {
        String sancionId = tutorSancionado();
        String cuerpo = apelar(tokenTutor, sancionId, "Tuve una emergencia médica y adjunto la incapacidad",
                evidencia("incapacidad.pdf")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String archivoId = mapper.readTree(cuerpo).get("evidencias").get(0).asText();

        mvc.perform(con(get("/api/archivos/" + archivoId), tokenTutor, null)).andExpect(status().isOk());
        mvc.perform(con(get("/api/archivos/" + archivoId), tokenAdmin, null)).andExpect(status().isOk());
        mvc.perform(con(get("/api/archivos/" + archivoId), tokenEstudiante, null)).andExpect(status().isForbidden());
    }
}
