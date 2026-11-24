package com.tutofast.controlador;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tutofast.AlmacenamientoEnMemoria;
import com.tutofast.ArchivosDePrueba;
import com.tutofast.RepositorioAuditoriaEnMemoria;
import com.tutofast.RepositorioUsuarioEnMemoria;
import com.tutofast.excepcion.ManejadorGlobalExcepciones;
import com.tutofast.integracion.adaptadores.AdaptadorNotificaciones;
import com.tutofast.integracion.adaptadores.DocumentoNotificacion;
import com.tutofast.modelo.Usuario;
import com.tutofast.repositorio.RepositorioNotificacion;
import com.tutofast.seguridad.FiltroAutenticacionJwt;
import com.tutofast.seguridad.InterceptorRoles;
import com.tutofast.seguridad.JwtUtil;
import com.tutofast.seguridad.ResolvedorUsuarioAutenticado;
import com.tutofast.servicio.ContextoSoportesVerificacion;
import com.tutofast.servicio.ServicioArchivos;
import com.tutofast.servicio.ServicioAuditoria;
import com.tutofast.servicio.ServicioIdentidad;
import com.tutofast.servicio.ServicioNotificaciones;

/** Soportes, descarga de archivos y notificaciones a través de filtro JWT, roles y manejador de errores. */
class ArchivosYNotificacionesControladorTest {

    private final Map<String, Usuario> usuarios = new LinkedHashMap<>();
    private final Map<String, DocumentoNotificacion> notificaciones = new LinkedHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private ServicioIdentidad identidad;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        var repoUsuarios = RepositorioUsuarioEnMemoria.crear(usuarios);
        var archivos = new ServicioArchivos(new AlmacenamientoEnMemoria(),
                List.of(new ContextoSoportesVerificacion(repoUsuarios)));
        RepositorioNotificacion repoNot = mock(RepositorioNotificacion.class);
        when(repoNot.save(any(DocumentoNotificacion.class))).thenAnswer(inv -> {
            DocumentoNotificacion n = inv.getArgument(0);
            if (n.getId() == null) {
                n.setId("n" + (notificaciones.size() + 1));
            }
            notificaciones.put(n.getId(), n);
            return n;
        });
        when(repoNot.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(notificaciones.get(inv.<String>getArgument(0))));
        when(repoNot.countByUsuarioIdAndLeidaFalse(anyString())).thenAnswer(inv -> notificaciones.values().stream()
                .filter(n -> n.getUsuarioId().equals(inv.getArgument(0)) && !n.isLeida()).count());
        when(repoNot.findByUsuarioIdOrderByFechaDesc(anyString(), any(Pageable.class))).thenAnswer(inv -> {
            List<DocumentoNotificacion> propias = notificaciones.values().stream()
                    .filter(n -> n.getUsuarioId().equals(inv.getArgument(0))).toList();
            return new PageImpl<>(propias, inv.getArgument(1), propias.size());
        });
        var servicioNot = new ServicioNotificaciones(new AdaptadorNotificaciones(repoNot), repoNot);
        identidad = new ServicioIdentidad(repoUsuarios, new BCryptPasswordEncoder(4),
                new JwtUtil("secreto-de-prueba-con-mas-de-32-caracteres-ok", Duration.ofHours(8)),
                com.tutofast.RepositorioAuditoriaEnMemoria.servicio(new ArrayList<>()), archivos,
                servicioNot, mock(com.tutofast.repositorio.RepositorioAsignatura.class),
                mock(com.tutofast.repositorio.RepositorioCalificacion.class));


        mvc = MockMvcBuilders
                .standaloneSetup(new UsuarioControlador(identidad), new ArchivoControlador(archivos),
                        new NotificacionControlador(servicioNot))
                .setControllerAdvice(new ManejadorGlobalExcepciones())
                .setMessageConverters(new org.springframework.http.converter.ResourceHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(mapper))
                .setCustomArgumentResolvers(new ResolvedorUsuarioAutenticado())
                .addInterceptors(new InterceptorRoles())
                .addFilters(new FiltroAutenticacionJwt(identidad, mapper))
                .build();
        this.servicioNot = servicioNot;
    }

    private ServicioNotificaciones servicioNot;

    private String tokenDe(String correo, String documento, String rol) {
        identidad.registrarUsuario(new com.tutofast.dto.RegistroDTO("Tutora Prueba", correo, documento,
                "Segura#2026", com.tutofast.modelo.Rol.valueOf(rol), true, null, 4));
        return identidad.iniciarSesion(new com.tutofast.dto.InicioSesionDTO(correo, "Segura#2026")).token();
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder subida(
            String token, String tipo, MockMultipartFile archivo) {
        var peticion = multipart("/api/usuarios/yo/soportes").file(archivo)
                .file(new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE,
                        ("{\"tipo\":\"" + tipo + "\"}").getBytes()));
        return (org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder)
                peticion.header("Authorization", "Bearer " + token);
    }

    @Test
    void tutorSubeSoporteLoListaYLoDescarga() throws Exception {
        String token = tokenDe("t@u.edu.co", "1012345678", "TUTOR");

        String cuerpo = mvc.perform(subida(token, "CERTIFICADO_MONITORIA", ArchivosDePrueba.pdf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"))
                .andExpect(jsonPath("$.nombreArchivo").value("certificado.pdf"))
                .andReturn().getResponse().getContentAsString();
        String archivoId = mapper.readTree(cuerpo).get("archivoId").asText();

        mvc.perform(get("/api/usuarios/yo/soportes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].archivoId").value(archivoId));
        mvc.perform(get("/api/usuarios/yo").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.perfilTutor.nivelVerificacion").value("EN_REVISION"));
        mvc.perform(get("/api/archivos/" + archivoId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("certificado.pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void ejecutableSeRechazaCon415YArchivoGrandeCon413() throws Exception {
        String token = tokenDe("t@u.edu.co", "1012345678", "TUTOR");

        mvc.perform(subida(token, "OTRO", ArchivosDePrueba.archivo("a.exe", "application/octet-stream", new byte[] {1, 2})))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("TIPO_ARCHIVO_NO_PERMITIDO"));
        mvc.perform(subida(token, "OTRO", ArchivosDePrueba.pdfDeTamano(11L * 1024 * 1024)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_DEMASIADO_GRANDE"));
    }

    @Test
    void tipoDeSoporteInvalidoDa400() throws Exception {
        String token = tokenDe("t@u.edu.co", "1012345678", "TUTOR");
        mvc.perform(subida(token, "INVENTADO", ArchivosDePrueba.pdf())).andExpect(status().isBadRequest());
    }

    @Test
    void estudianteNoPuedeSubirSoportesYSinTokenDa401() throws Exception {
        String token = tokenDe("e@u.edu.co", "1098765432", "ESTUDIANTE");

        mvc.perform(subida(token, "OTRO", ArchivosDePrueba.pdf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios/yo/soportes").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios/yo/soportes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/archivos/0123456789abcdef01234567")).andExpect(status().isUnauthorized());
    }

    @Test
    void otroUsuarioNoDescargaElSoporteAjeno() throws Exception {
        String dueno = tokenDe("t@u.edu.co", "1012345678", "TUTOR");
        String otro = tokenDe("t2@u.edu.co", "1012345679", "TUTOR");
        String archivoId = mapper.readTree(mvc.perform(subida(dueno, "OTRO", ArchivosDePrueba.png()))
                .andReturn().getResponse().getContentAsString()).get("archivoId").asText();

        mvc.perform(get("/api/archivos/" + archivoId).header("Authorization", "Bearer " + otro))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/archivos/0123456789abcdef01234567").header("Authorization", "Bearer " + dueno))
                .andExpect(status().isNotFound());
    }

    @Test
    void notificacionesSeConsultanContabilizanYSeMarcanLeidas() throws Exception {
        String token = tokenDe("t@u.edu.co", "1012345678", "TUTOR");
        String usuarioId = usuarios.values().iterator().next().getId();
        servicioNot.notificar(usuarioId, com.tutofast.integracion.puertos.TipoNotificacion.SOPORTE_APROBADO,
                "Soporte aprobado", "Tu certificado fue aprobado", "perfil.html");
        String auth = "Bearer " + token;

        mvc.perform(get("/api/notificaciones/conteo-no-leidas").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.noLeidas").value(1));
        mvc.perform(get("/api/notificaciones").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].titulo").value("Soporte aprobado"))
                .andExpect(jsonPath("$.totalElementos").value(1));
        String id = notificaciones.keySet().iterator().next();
        mvc.perform(patch("/api/notificaciones/" + id + "/leida").header("Authorization", auth))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notificaciones/conteo-no-leidas").header("Authorization", auth))
                .andExpect(jsonPath("$.noLeidas").value(0));
        mvc.perform(patch("/api/notificaciones/leer-todas").header("Authorization", auth))
                .andExpect(status().isNoContent());
        mvc.perform(patch("/api/notificaciones/inexistente/leida").header("Authorization", auth))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/notificaciones")).andExpect(status().isUnauthorized());
    }
}
