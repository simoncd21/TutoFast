package com.tutofast.controlador;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tutofast.AlmacenamientoEnMemoria;
import com.tutofast.RepositorioAuditoriaEnMemoria;
import com.tutofast.RepositorioUsuarioEnMemoria;
import com.tutofast.dto.ComisionDTO;
import com.tutofast.dto.UniversidadDTO;
import com.tutofast.excepcion.ManejadorGlobalExcepciones;
import com.tutofast.integracion.puertos.INotificador;
import com.tutofast.modelo.TipoCatalogo;
import com.tutofast.modelo.Usuario;
import com.tutofast.repositorio.RepositorioNotificacion;
import com.tutofast.seguridad.FiltroAutenticacionJwt;
import com.tutofast.seguridad.InterceptorRoles;
import com.tutofast.seguridad.JwtUtil;
import com.tutofast.seguridad.ResolvedorUsuarioAutenticado;
import com.tutofast.servicio.ServicioArchivos;
import com.tutofast.servicio.ServicioCatalogo;
import com.tutofast.servicio.ServicioIdentidad;
import com.tutofast.servicio.ServicioNotificaciones;

/** Rutas, roles y acceso público del catálogo, la comisión y el panel de administración. */
class AdministracionControladorTest {

    private final Map<String, Usuario> usuarios = new LinkedHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private ServicioIdentidad identidad;
    private ServicioCatalogo catalogo;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        var repoUsuarios = RepositorioUsuarioEnMemoria.crear(usuarios);
        var auditoria = RepositorioAuditoriaEnMemoria.servicio(new ArrayList<>());
        identidad = new ServicioIdentidad(repoUsuarios, new BCryptPasswordEncoder(4),
                new JwtUtil("secreto-de-prueba-con-mas-de-32-caracteres-ok", Duration.ofHours(8)), auditoria,
                new ServicioArchivos(new AlmacenamientoEnMemoria(), List.of()),
                new ServicioNotificaciones(mock(INotificador.class), mock(RepositorioNotificacion.class)),
                mock(com.tutofast.repositorio.RepositorioAsignatura.class), mock(com.tutofast.repositorio.RepositorioCalificacion.class));
        catalogo = mock(ServicioCatalogo.class);

        mvc = MockMvcBuilders
                .standaloneSetup(new CatalogoControlador(catalogo), new ComisionControlador(catalogo),
                        new AdministracionControlador(identidad, auditoria,
                                mock(com.tutofast.servicio.ServicioMensajeria.class),
                                mock(com.tutofast.servicio.ServicioReputacion.class)))
                .setControllerAdvice(new ManejadorGlobalExcepciones())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setCustomArgumentResolvers(new ResolvedorUsuarioAutenticado())
                .addInterceptors(new InterceptorRoles())
                .addFilters(new FiltroAutenticacionJwt(identidad, mapper))
                .build();
    }

    private String tokenAdmin() {
        identidad.crearAdministradorSiNoExiste("admin@tutofast.co", "Admin#2026");
        return identidad.iniciarSesion(new com.tutofast.dto.InicioSesionDTO("admin@tutofast.co", "Admin#2026")).token();
    }

    private String tokenEstudiante() {
        identidad.registrarUsuario(new com.tutofast.dto.RegistroDTO("Est Uno", "est@u.edu.co", "1098765432",
                "Segura#2026", com.tutofast.modelo.Rol.ESTUDIANTE, true, null, 3));
        return identidad.iniciarSesion(new com.tutofast.dto.InicioSesionDTO("est@u.edu.co", "Segura#2026")).token();
    }

    @Test
    void elCatalogoSeConsultaSinSesion() throws Exception {
        when(catalogo.consultarCatalogo(TipoCatalogo.UNIVERSIDADES, null, true))
                .thenAnswer(inv -> List.of(new UniversidadDTO("u1", "Universidad Demo", true)));

        mvc.perform(get("/api/catalogos/universidades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Universidad Demo"));
    }

    @Test
    void gestionarElCatalogoExigeSerAdministrador() throws Exception {
        String cuerpo = "{\"nombre\":\"Universidad Nueva\"}";

        mvc.perform(post("/api/catalogos/universidades").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/catalogos/universidades").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header("Authorization", "Bearer " + tokenEstudiante()))
                .andExpect(status().isForbidden());
    }

    @Test
    void elAdministradorCreaUnElementoDelCatalogo() throws Exception {
        when(catalogo.gestionarCatalogo(any(), eq(TipoCatalogo.UNIVERSIDADES), eq(null), any()))
                .thenReturn(new UniversidadDTO("u2", "Universidad Nueva", true));

        mvc.perform(post("/api/catalogos/universidades").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Universidad Nueva\"}")
                        .header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("u2"));
        mvc.perform(post("/api/catalogos/universidades").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\" \"}")
                        .header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void desactivarUnElementoUsaElBorradoLogico() throws Exception {
        mvc.perform(patch("/api/catalogos/asignaturas/abc/activa").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":false}").header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isOk());

        verify(catalogo).cambiarEstadoCatalogo(any(), eq(TipoCatalogo.ASIGNATURAS), eq("abc"), anyBoolean());
    }

    @Test
    void laComisionVigenteLaVeCualquierUsuarioYSoloElAdminLaCambia() throws Exception {
        when(catalogo.consultarComision()).thenReturn(new ComisionDTO("c1", new BigDecimal("10"), null, null));
        when(catalogo.configurarComision(any(), any())).thenReturn(new ComisionDTO("c2", new BigDecimal("12"), null, null));
        String estudiante = tokenEstudiante();

        mvc.perform(get("/api/comision/vigente").header("Authorization", "Bearer " + estudiante))
                .andExpect(status().isOk()).andExpect(jsonPath("$.porcentaje").value(10));
        mvc.perform(get("/api/comision/vigente")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/comision").contentType(MediaType.APPLICATION_JSON).content("{\"porcentaje\":12}")
                        .header("Authorization", "Bearer " + estudiante))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/comision/historial").header("Authorization", "Bearer " + estudiante))
                .andExpect(status().isForbidden());

        String admin = tokenAdmin();
        mvc.perform(put("/api/comision").contentType(MediaType.APPLICATION_JSON).content("{\"porcentaje\":12}")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("c2"));
        mvc.perform(put("/api/comision").contentType(MediaType.APPLICATION_JSON).content("{\"porcentaje\":60}")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elPanelDeAdministracionEsSoloParaAdministradores() throws Exception {
        String estudiante = tokenEstudiante();

        mvc.perform(get("/api/admin/soportes").header("Authorization", "Bearer " + estudiante))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/usuarios").header("Authorization", "Bearer " + estudiante))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/auditoria").header("Authorization", "Bearer " + estudiante))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/soportes").header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rechazarUnSoporteSinObservacionDa400() throws Exception {
        mvc.perform(post("/api/admin/usuarios/u1/soportes/s1/rechazar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacion\":\"\"}").header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aprobarUnSoporteDeUnUsuarioInexistenteDa404() throws Exception {
        mvc.perform(post("/api/admin/usuarios/u1/soportes/s1/aprobar")
                        .header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cambiarElEstadoDeUnaCuentaNecesitaElEstado() throws Exception {
        mvc.perform(patch("/api/admin/usuarios/u1/estado").contentType(MediaType.APPLICATION_JSON)
                        .content("{}").header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isBadRequest());
    }
}
