package com.tutofast.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.tutofast.AlmacenamientoEnMemoria;
import com.tutofast.ArchivosDePrueba;
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
import com.tutofast.modelo.EstadoSoporte;
import com.tutofast.modelo.TipoSoporte;
import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.NivelVerificacion;
import com.tutofast.modelo.RegistroAuditoria;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.TipoTutor;
import com.tutofast.modelo.Usuario;
import com.tutofast.integracion.puertos.INotificador;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.repositorio.RepositorioNotificacion;
import com.tutofast.repositorio.RepositorioUsuario;
import com.tutofast.seguridad.JwtUtil;

class ServicioIdentidadTest {

    private final Map<String, Usuario> almacen = new LinkedHashMap<>();
    private final BCryptPasswordEncoder codificador = new BCryptPasswordEncoder(4);
    private final List<RegistroAuditoria> bitacora = new ArrayList<>();
    private final AlmacenamientoEnMemoria almacenamiento = new AlmacenamientoEnMemoria();
    private final INotificador notificador = mock(INotificador.class);
    private RepositorioUsuario repoUsuarios;
    private ServicioIdentidad servicio;

    @BeforeEach
    void preparar() {
        repoUsuarios = RepositorioUsuarioEnMemoria.crear(almacen);
        servicio = new ServicioIdentidad(repoUsuarios, codificador,
                new JwtUtil("secreto-de-prueba-con-mas-de-32-caracteres-ok", Duration.ofHours(8)),
                com.tutofast.RepositorioAuditoriaEnMemoria.servicio(bitacora),
                new ServicioArchivos(almacenamiento, List.of(new ContextoSoportesVerificacion(repoUsuarios))),
                new ServicioNotificaciones(notificador, mock(RepositorioNotificacion.class)));
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

    // ---- soportes de verificación (módulo 04) ----

    private String registrarTutor() {
        return servicio.registrarUsuario(registro("tutor@u.edu.co", "1012345678", Rol.TUTOR, true)).usuarioId();
    }

    @Test
    void subirSoporteGuardaElArchivoYPasaElNivelAEnRevision() {
        String id = registrarTutor();

        var soporte = servicio.subirSoporte(id, TipoSoporte.CERTIFICADO_MONITORIA, ArchivosDePrueba.pdf());

        assertEquals(EstadoSoporte.EN_REVISION, soporte.estado());
        assertEquals("certificado.pdf", soporte.nombreArchivo());
        assertTrue(almacenamiento.contiene(soporte.archivoId()));
        var perfil = almacen.get(id).getPerfilTutor();
        assertEquals(NivelVerificacion.EN_REVISION, perfil.getNivelVerificacion());
        assertEquals(1, perfil.getSoportes().size());
        assertEquals(soporte.archivoId(), perfil.getSoportes().get(0).getRutaArchivo());
        assertTrue(acciones().contains("SUBIR_SOPORTE"));
    }

    @Test
    void subirSoporteNoDegradaAUnTutorVerificado() {
        String id = registrarTutor();
        almacen.get(id).getPerfilTutor().setNivelVerificacion(NivelVerificacion.VERIFICADO);

        servicio.subirSoporte(id, TipoSoporte.OTRO, ArchivosDePrueba.png());

        assertEquals(NivelVerificacion.VERIFICADO, almacen.get(id).getPerfilTutor().getNivelVerificacion());
    }

    @Test
    void subirSoporteInvalidoNoGuardaNadaNiCambiaElNivel() {
        String id = registrarTutor();

        var e = assertThrows(ExcepcionNegocio.class, () -> servicio.subirSoporte(id, TipoSoporte.OTRO,
                ArchivosDePrueba.archivo("a.exe", "application/octet-stream", new byte[] {1, 2, 3})));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getEstado());
        assertEquals(0, almacenamiento.cantidad());
        assertEquals(NivelVerificacion.NO_VERIFICADO, almacen.get(id).getPerfilTutor().getNivelVerificacion());
        assertTrue(almacen.get(id).getPerfilTutor().getSoportes().isEmpty());
    }

    @Test
    void soloUnTutorPuedeSubirSoportes() {
        String id = servicio.registrarUsuario(registro("e@u.co", "1111111111", Rol.ESTUDIANTE, true)).usuarioId();

        assertThrows(ExcepcionAutorizacion.class,
                () -> servicio.subirSoporte(id, TipoSoporte.OTRO, ArchivosDePrueba.pdf()));
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.listarSoportes(id));
        assertEquals(0, almacenamiento.cantidad());
    }

    @Test
    void limitaLosSoportesPendientesDeRevision() {
        String id = registrarTutor();
        for (int i = 0; i < ServicioIdentidad.MAX_SOPORTES_EN_REVISION; i++) {
            servicio.subirSoporte(id, TipoSoporte.OTRO, ArchivosDePrueba.pdf());
        }

        var e = assertThrows(ExcepcionNegocio.class,
                () -> servicio.subirSoporte(id, TipoSoporte.OTRO, ArchivosDePrueba.pdf()));

        assertEquals(HttpStatus.CONFLICT, e.getEstado());
        assertEquals(ServicioIdentidad.MAX_SOPORTES_EN_REVISION, almacenamiento.cantidad());
    }

    @Test
    void siFallaElGuardadoDelPerfilSeEliminaElArchivoSubido() {
        String id = registrarTutor();
        org.mockito.Mockito.when(repoUsuarios.save(org.mockito.ArgumentMatchers.any(Usuario.class)))
                .thenThrow(new IllegalStateException("Mongo caído"));

        assertThrows(IllegalStateException.class,
                () -> servicio.subirSoporte(id, TipoSoporte.OTRO, ArchivosDePrueba.pdf()));

        assertEquals(0, almacenamiento.cantidad());
    }

    @Test
    void listaLosSoportesDelTutor() {
        String id = registrarTutor();
        assertTrue(servicio.listarSoportes(id).isEmpty());
        servicio.subirSoporte(id, TipoSoporte.CARNE_ESTUDIANTIL, ArchivosDePrueba.jpg());
        servicio.subirSoporte(id, TipoSoporte.HISTORIAL_CALIFICACIONES, ArchivosDePrueba.docx());

        var lista = servicio.listarSoportes(id);

        assertEquals(2, lista.size());
        assertEquals(TipoSoporte.CARNE_ESTUDIANTIL, lista.get(0).tipo());
        assertEquals("historial.docx", lista.get(1).nombreArchivo());
    }

    // ---- verificación de tutores y administración (módulo 05) ----

    private String registrarTutorCon(String correo, String documento) {
        String id = servicio.registrarUsuario(registro(correo, documento, Rol.TUTOR, true)).usuarioId();
        servicio.subirSoporte(id, TipoSoporte.CERTIFICADO_MONITORIA, ArchivosDePrueba.pdf());
        return id;
    }

    private String crearAdmin() {
        servicio.crearAdministradorSiNoExiste("admin@tutofast.co", "Admin#2026");
        return almacen.values().stream().filter(u -> u.tieneRol(Rol.ADMINISTRADOR)).findFirst().orElseThrow().getId();
    }

    @Test
    void laColaPriorizaLosCorreosEduCoAunqueSeanMasRecientes() {
        String normal = registrarTutorCon("tutor@gmail.com", "1000000001");
        String institucional = registrarTutorCon("laura@universidad.edu.co", "1000000002");
        String otroNormal = registrarTutorCon("otro@hotmail.com", "1000000003");

        var cola = servicio.consultarColaVerificacion(EstadoSoporte.EN_REVISION);

        assertEquals(3, cola.size());
        assertEquals(institucional, cola.get(0).usuarioId());
        assertEquals(com.tutofast.modelo.PrioridadVerificacion.ALTA, cola.get(0).prioridad());
        assertEquals("Correo institucional .edu.co", cola.get(0).motivoPrioridad());
        assertEquals(List.of(normal, otroNormal), List.of(cola.get(1).usuarioId(), cola.get(2).usuarioId()));
        assertEquals(com.tutofast.modelo.PrioridadVerificacion.NORMAL, cola.get(1).prioridad());
        assertEquals(null, cola.get(1).motivoPrioridad());
    }

    @Test
    void laColaSoloMuestraLosSoportesDelEstadoPedido() {
        String id = registrarTutorCon("tutor@universidad.edu.co", "1000000001");
        String admin = crearAdmin();
        String soporteId = almacen.get(id).getPerfilTutor().getSoportes().get(0).getId();
        servicio.verificarTutor(admin, id, soporteId, true, null);

        assertTrue(servicio.consultarColaVerificacion(EstadoSoporte.EN_REVISION).isEmpty());
        assertEquals(1, servicio.consultarColaVerificacion(EstadoSoporte.APROBADO).size());
    }

    @Test
    void aprobarUnSoporteDejaAlTutorVerificadoYLoNotifica() {
        String id = registrarTutorCon("tutor@universidad.edu.co", "1000000001");
        String admin = crearAdmin();
        String soporteId = almacen.get(id).getPerfilTutor().getSoportes().get(0).getId();

        var resultado = servicio.verificarTutor(admin, id, soporteId, true, "Certificado válido");

        assertEquals(EstadoSoporte.APROBADO, resultado.estado());
        assertEquals("Certificado válido", resultado.observacion());
        var perfil = almacen.get(id).getPerfilTutor();
        assertEquals(NivelVerificacion.VERIFICADO, perfil.getNivelVerificacion());
        assertEquals(admin, perfil.getSoportes().get(0).getRevisadoPor());
        assertNotNull(perfil.getSoportes().get(0).getFechaRevision());
        assertTrue(acciones().contains("APROBAR_SOPORTE"));
        verify(notificador).notificar(eq(id), eq(TipoNotificacion.SOPORTE_APROBADO), anyString(), anyString(), anyString());
    }

    @Test
    void rechazarExigeObservacionYDejaAlTutorSinVerificar() {
        String id = registrarTutorCon("tutor@gmail.com", "1000000001");
        String admin = crearAdmin();
        String soporteId = almacen.get(id).getPerfilTutor().getSoportes().get(0).getId();

        assertThrows(ExcepcionNegocio.class, () -> servicio.verificarTutor(admin, id, soporteId, false, "  "));
        assertEquals(NivelVerificacion.EN_REVISION, almacen.get(id).getPerfilTutor().getNivelVerificacion());

        var resultado = servicio.verificarTutor(admin, id, soporteId, false, "Documento ilegible");

        assertEquals(EstadoSoporte.RECHAZADO, resultado.estado());
        assertEquals(NivelVerificacion.NO_VERIFICADO, almacen.get(id).getPerfilTutor().getNivelVerificacion());
        assertTrue(acciones().contains("RECHAZAR_SOPORTE"));
        verify(notificador).notificar(eq(id), eq(TipoNotificacion.SOPORTE_RECHAZADO), anyString(),
                org.mockito.ArgumentMatchers.contains("Documento ilegible"), anyString());
    }

    @Test
    void rechazarUnSoporteConOtroPendienteMantieneEnRevision() {
        String id = registrarTutorCon("tutor@gmail.com", "1000000001");
        servicio.subirSoporte(id, TipoSoporte.CARNE_ESTUDIANTIL, ArchivosDePrueba.png());
        String admin = crearAdmin();
        String primero = almacen.get(id).getPerfilTutor().getSoportes().get(0).getId();

        servicio.verificarTutor(admin, id, primero, false, "No corresponde");

        assertEquals(NivelVerificacion.EN_REVISION, almacen.get(id).getPerfilTutor().getNivelVerificacion());
    }

    @Test
    void unSoporteYaRevisadoNoSePuedeRevisarDeNuevo() {
        String id = registrarTutorCon("tutor@gmail.com", "1000000001");
        String admin = crearAdmin();
        String soporteId = almacen.get(id).getPerfilTutor().getSoportes().get(0).getId();
        servicio.verificarTutor(admin, id, soporteId, true, null);

        var e = assertThrows(ExcepcionNegocio.class, () -> servicio.verificarTutor(admin, id, soporteId, false, "tarde"));

        assertEquals(HttpStatus.CONFLICT, e.getEstado());
        assertEquals(NivelVerificacion.VERIFICADO, almacen.get(id).getPerfilTutor().getNivelVerificacion());
    }

    @Test
    void verificarUnSoporteInexistenteOUnNoTutorDa404() {
        String id = registrarTutorCon("tutor@gmail.com", "1000000001");
        String estudiante = servicio.registrarUsuario(registro("est@u.edu.co", "1000000009", Rol.ESTUDIANTE, true))
                .usuarioId();
        String admin = crearAdmin();

        assertThrows(com.tutofast.excepcion.ExcepcionNoEncontrado.class,
                () -> servicio.verificarTutor(admin, id, "no-existe", true, null));
        assertThrows(com.tutofast.excepcion.ExcepcionNoEncontrado.class,
                () -> servicio.verificarTutor(admin, estudiante, "x", true, null));
    }

    @Test
    void cambiarElEstadoDeUnaCuentaLoAudita() {
        String id = registrarTutorCon("tutor@gmail.com", "1000000001");
        String admin = crearAdmin();

        var suspendida = servicio.cambiarEstadoCuenta(admin, id, EstadoCuenta.SUSPENDIDA, "Reporte de fraude");

        assertEquals(EstadoCuenta.SUSPENDIDA, suspendida.estadoCuenta());
        assertEquals(EstadoCuenta.SUSPENDIDA, almacen.get(id).getEstadoCuenta());
        assertTrue(acciones().contains("CAMBIAR_ESTADO_CUENTA"));
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.iniciarSesion(
                new InicioSesionDTO("tutor@gmail.com", "Segura#2026")));

        servicio.cambiarEstadoCuenta(admin, id, EstadoCuenta.ACTIVA, null);
        assertEquals(EstadoCuenta.ACTIVA, almacen.get(id).getEstadoCuenta());
    }

    @Test
    void noSePuedeSuspenderSinMotivoNiLaPropiaCuentaNiAOtroAdministrador() {
        String id = registrarTutorCon("tutor@gmail.com", "1000000001");
        String admin = crearAdmin();

        assertThrows(ExcepcionNegocio.class,
                () -> servicio.cambiarEstadoCuenta(admin, id, EstadoCuenta.BLOQUEADA, " "));
        assertThrows(ExcepcionNegocio.class,
                () -> servicio.cambiarEstadoCuenta(admin, admin, EstadoCuenta.SUSPENDIDA, "x"));
        Usuario otroAdmin = new Usuario();
        otroAdmin.setId("bbbbbbbbbbbbbbbbbbbbbbbb");
        otroAdmin.setCorreo("admin2@tutofast.co");
        otroAdmin.setRoles(new java.util.LinkedHashSet<>(List.of(Rol.ADMINISTRADOR)));
        almacen.put(otroAdmin.getId(), otroAdmin);
        assertThrows(ExcepcionNegocio.class,
                () -> servicio.cambiarEstadoCuenta(admin, otroAdmin.getId(), EstadoCuenta.SUSPENDIDA, "x"));
        assertEquals(EstadoCuenta.ACTIVA, almacen.get(id).getEstadoCuenta());
    }
}
