package com.tutofast.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.tutofast.RepositorioAuditoriaEnMemoria;
import com.tutofast.RepositorioUsuarioEnMemoria;
import com.tutofast.dto.InicioSesionDTO;
import com.tutofast.dto.PerfilEstudianteDTO;
import com.tutofast.dto.PerfilTutorDTO;
import com.tutofast.dto.RegistroDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoAutenticado;
import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.NivelVerificacion;
import com.tutofast.modelo.RegistroAuditoria;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.TipoTutor;
import com.tutofast.modelo.Usuario;
import com.tutofast.seguridad.JwtUtil;

class ServicioIdentidadTest {

    private final Map<String, Usuario> almacen = new LinkedHashMap<>();
    private final BCryptPasswordEncoder codificador = new BCryptPasswordEncoder(4);
    private final List<RegistroAuditoria> bitacora = new ArrayList<>();
    private ServicioIdentidad servicio;

    @BeforeEach
    void preparar() {
        servicio = new ServicioIdentidad(RepositorioUsuarioEnMemoria.crear(almacen), codificador,
                new JwtUtil("secreto-de-prueba-con-mas-de-32-caracteres-ok", Duration.ofHours(8)),
                new ServicioAuditoria(RepositorioAuditoriaEnMemoria.crear(bitacora)));
    }

    private List<String> acciones() {
        return bitacora.stream().map(RegistroAuditoria::getAccion).toList();
    }

    private RegistroDTO registro(String correo, String documento, Rol rol, Boolean acepta) {
        return new RegistroDTO("Carlos Gómez", correo, documento, "Segura#2026", rol, acepta, null, 4);
    }

    @Test
    void registraEstudianteConClaveEncriptadaYFechaDeConsentimiento() {
        var respuesta = servicio.registrarUsuario(
                registro("Estudiante@Universidad.edu.co", "1098765432", Rol.ESTUDIANTE, true));

        assertEquals("Registro exitoso en TutoFast", respuesta.mensaje());
        Usuario guardado = almacen.get(respuesta.usuarioId());
        assertEquals("estudiante@universidad.edu.co", guardado.getCorreo());
        assertNotEquals("Segura#2026", guardado.getClaveHash());
        assertTrue(guardado.getClaveHash().startsWith("$2"));
        assertTrue(codificador.matches("Segura#2026", guardado.getClaveHash()));
        assertNotNull(guardado.getFechaAutorizacionDatos());
        assertEquals(EstadoCuenta.ACTIVA, guardado.getEstadoCuenta());
        assertEquals(4, guardado.getPerfilEstudiante().getSemestre());
    }

    @Test
    void registraTutorComoNoVerificado() {
        var respuesta = servicio.registrarUsuario(registro("t@u.edu.co", "1012345678", Rol.TUTOR, true));

        Usuario tutor = almacen.get(respuesta.usuarioId());
        assertEquals(NivelVerificacion.NO_VERIFICADO, tutor.getPerfilTutor().getNivelVerificacion());
    }

    @Test
    void rechazaRegistroSinConsentimiento() {
        ExcepcionNegocio e = assertThrows(ExcepcionNegocio.class,
                () -> servicio.registrarUsuario(registro("a@b.co", "1098765432", Rol.ESTUDIANTE, false)));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getEstado());
        assertEquals(ServicioIdentidad.MENSAJE_CONSENTIMIENTO, e.getMessage());
        assertTrue(almacen.isEmpty());
    }

    @Test
    void rechazaCorreoDuplicadoSinImportarMayusculas() {
        servicio.registrarUsuario(registro("a@b.co", "1111111111", Rol.ESTUDIANTE, true));

        ExcepcionNegocio e = assertThrows(ExcepcionNegocio.class,
                () -> servicio.registrarUsuario(registro("A@B.CO", "2222222222", Rol.ESTUDIANTE, true)));

        assertEquals(HttpStatus.CONFLICT, e.getEstado());
        assertEquals("DUPLICADO", e.getCodigo());
    }

    @Test
    void rechazaDocumentoDuplicado() {
        servicio.registrarUsuario(registro("a@b.co", "1111111111", Rol.ESTUDIANTE, true));

        ExcepcionNegocio e = assertThrows(ExcepcionNegocio.class,
                () -> servicio.registrarUsuario(registro("c@d.co", "1111111111", Rol.TUTOR, true)));

        assertEquals("DUPLICADO", e.getCodigo());
    }

    @Test
    void noPermiteRegistrarseComoAdministrador() {
        assertThrows(ExcepcionNegocio.class,
                () -> servicio.registrarUsuario(registro("a@b.co", "1111111111", Rol.ADMINISTRADOR, true)));
    }

    @Test
    void iniciaSesionYDevuelveTokenValido() {
        var id = servicio.registrarUsuario(registro("a@b.co", "1111111111", Rol.ESTUDIANTE, true)).usuarioId();

        var sesion = servicio.iniciarSesion(new InicioSesionDTO(" A@b.co ", "Segura#2026"));

        assertEquals(id, sesion.usuario().id());
        assertEquals(List.of(Rol.ESTUDIANTE), sesion.usuario().roles());
        assertEquals(id, servicio.autenticarToken(sesion.token()).id());
    }

    @Test
    void usaMensajeGenericoParaClaveIncorrectaOCorreoInexistente() {
        servicio.registrarUsuario(registro("a@b.co", "1111111111", Rol.ESTUDIANTE, true));

        var claveMala = assertThrows(ExcepcionNoAutenticado.class,
                () -> servicio.iniciarSesion(new InicioSesionDTO("a@b.co", "Otra#2026")));
        var sinCuenta = assertThrows(ExcepcionNoAutenticado.class,
                () -> servicio.iniciarSesion(new InicioSesionDTO("nadie@b.co", "Segura#2026")));

        assertEquals("Correo o contraseña incorrectos", claveMala.getMessage());
        assertEquals(claveMala.getMessage(), sinCuenta.getMessage());
    }

    @Test
    void impideAccesoACuentasSuspendidasOBloqueadas() {
        var id = servicio.registrarUsuario(registro("a@b.co", "1111111111", Rol.ESTUDIANTE, true)).usuarioId();
        String token = servicio.iniciarSesion(new InicioSesionDTO("a@b.co", "Segura#2026")).token();

        almacen.get(id).setEstadoCuenta(EstadoCuenta.SUSPENDIDA);

        assertThrows(ExcepcionAutorizacion.class,
                () -> servicio.iniciarSesion(new InicioSesionDTO("a@b.co", "Segura#2026")));
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.autenticarToken(token));

        almacen.get(id).setEstadoCuenta(EstadoCuenta.BLOQUEADA);
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.autenticarToken(token));
    }

    @Test
    void rechazaTokenDeUsuarioQueYaNoExiste() {
        var id = servicio.registrarUsuario(registro("a@b.co", "1111111111", Rol.ESTUDIANTE, true)).usuarioId();
        String token = servicio.iniciarSesion(new InicioSesionDTO("a@b.co", "Segura#2026")).token();
        almacen.remove(id);

        assertThrows(ExcepcionNoAutenticado.class, () -> servicio.autenticarToken(token));
    }

    @Test
    void actualizaPerfilDeEstudiante() {
        var id = servicio.registrarUsuario(registro("a@b.co", "1111111111", Rol.ESTUDIANTE, true)).usuarioId();

        var perfil = servicio.actualizarPerfilEstudiante(id,
                new PerfilEstudianteDTO("6741a0c1e4b0a1b2c3d4e103", 6));

        assertEquals(6, perfil.perfilEstudiante().semestre());
        assertEquals("6741a0c1e4b0a1b2c3d4e103", perfil.perfilEstudiante().programaId());
    }

    @Test
    void actualizaPerfilDeTutorSinAlterarVerificacion() {
        var id = servicio.registrarUsuario(registro("t@b.co", "1111111111", Rol.TUTOR, true)).usuarioId();
        var dto = new PerfilTutorDTO("Monitora de cálculo", List.of("Cálculo"), "Sistemas",
                TipoTutor.MONITOR_CERTIFICADO, "4 semestres", List.of(Modalidad.VIRTUAL), "L-J 14-18",
                List.of(), "Bancolombia ****4821");

        var perfil = servicio.actualizarPerfilTutor(id, dto);

        assertEquals("Monitora de cálculo", perfil.perfilTutor().presentacion());
        assertEquals(NivelVerificacion.NO_VERIFICADO, perfil.perfilTutor().nivelVerificacion());
        assertEquals("Tutor Registrado (No Verificado)", perfil.perfilTutor().etiquetaVerificacion());
    }

    @Test
    void soloElRolCorrespondienteEditaSuPerfil() {
        var id = servicio.registrarUsuario(registro("a@b.co", "1111111111", Rol.ESTUDIANTE, true)).usuarioId();
        var dto = new PerfilTutorDTO("x", null, null, TipoTutor.PAR, null, List.of(Modalidad.VIRTUAL),
                null, null, null);

        assertThrows(ExcepcionAutorizacion.class, () -> servicio.actualizarPerfilTutor(id, dto));
    }

    @Test
    void activaRolAdicionalYRechazaRepetidoOAdministrador() {
        var id = servicio.registrarUsuario(registro("a@b.co", "1111111111", Rol.ESTUDIANTE, true)).usuarioId();

        var usuario = servicio.activarRol(id, Rol.TUTOR);

        assertTrue(usuario.roles().containsAll(List.of(Rol.ESTUDIANTE, Rol.TUTOR)));
        assertNotNull(almacen.get(id).getPerfilTutor());
        ExcepcionNegocio repetido = assertThrows(ExcepcionNegocio.class, () -> servicio.activarRol(id, Rol.TUTOR));
        assertEquals(HttpStatus.CONFLICT, repetido.getEstado());
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.activarRol(id, Rol.ADMINISTRADOR));
    }

    @Test
    void seedCreaAdministradorUnaSolaVez() {
        assertTrue(servicio.crearAdministradorSiNoExiste("Admin@TutoFast.local", "Cambiame#2026"));
        assertFalse(servicio.crearAdministradorSiNoExiste("otro@tutofast.local", "Cambiame#2026"));

        var sesion = servicio.iniciarSesion(new InicioSesionDTO("admin@tutofast.local", "Cambiame#2026"));
        assertEquals(List.of(Rol.ADMINISTRADOR), sesion.usuario().roles());
    }

    @Test
    void auditaRegistroYConsentimientoLey1581SinGuardarLaClave() {
        var id = servicio.registrarUsuario(registro("a@b.co", "1111111111", Rol.ESTUDIANTE, true)).usuarioId();

        assertEquals(List.of("REGISTRO_USUARIO", "CONSENTIMIENTO_DATOS"), acciones());
        RegistroAuditoria consentimiento = bitacora.get(1);
        assertEquals(id, consentimiento.getUsuarioId());
        assertEquals("Ley 1581 de 2012", consentimiento.getDetalle().get("norma"));
        assertNotNull(consentimiento.getFecha());
        bitacora.forEach(r -> assertFalse(r.getDetalle().toString().contains("Segura#2026")));
    }

    @Test
    void noAuditaRegistrosRechazados() {
        assertThrows(ExcepcionNegocio.class,
                () -> servicio.registrarUsuario(registro("a@b.co", "1098765432", Rol.ESTUDIANTE, false)));

        assertTrue(bitacora.isEmpty());
    }

    @Test
    void auditaInicioDeSesionExitosoYFallido() {
        servicio.registrarUsuario(registro("a@b.co", "1111111111", Rol.ESTUDIANTE, true));
        bitacora.clear();

        servicio.iniciarSesion(new InicioSesionDTO("a@b.co", "Segura#2026"));
        assertThrows(ExcepcionNoAutenticado.class,
                () -> servicio.iniciarSesion(new InicioSesionDTO("a@b.co", "Otra#2026")));
        assertThrows(ExcepcionNoAutenticado.class,
                () -> servicio.iniciarSesion(new InicioSesionDTO("nadie@b.co", "Otra#2026")));

        assertEquals(List.of("INICIO_SESION", "INICIO_SESION_FALLIDO"), acciones());
    }

    @Test
    void auditaActivacionDeRolYActualizacionDePerfil() {
        var id = servicio.registrarUsuario(registro("a@b.co", "1111111111", Rol.ESTUDIANTE, true)).usuarioId();
        bitacora.clear();

        servicio.activarRol(id, Rol.TUTOR);
        servicio.actualizarPerfilEstudiante(id, new PerfilEstudianteDTO(null, 5));

        assertEquals(List.of("ACTIVAR_ROL", "ACTUALIZAR_PERFIL_ESTUDIANTE"), acciones());
        assertEquals("TUTOR", bitacora.get(0).getDetalle().get("rol"));
    }

    @Test
    void noAuditaUnRolRepetido() {
        var id = servicio.registrarUsuario(registro("a@b.co", "1111111111", Rol.ESTUDIANTE, true)).usuarioId();
        bitacora.clear();

        assertThrows(ExcepcionNegocio.class, () -> servicio.activarRol(id, Rol.ESTUDIANTE));

        assertTrue(bitacora.isEmpty());
    }

    @Test
    void auditaElSeedDelAdministradorComoAccionDelSistema() {
        servicio.crearAdministradorSiNoExiste("admin@tutofast.local", "Cambiame#2026");

        assertEquals(List.of("SEED_ADMINISTRADOR"), acciones());
        assertEquals(null, bitacora.get(0).getUsuarioId());
    }
}
