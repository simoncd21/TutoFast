package com.tutofast.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.tutofast.AlmacenamientoEnMemoria;
import com.tutofast.ArchivosDePrueba;
import com.tutofast.RepositorioAuditoriaEnMemoria;
import com.tutofast.dto.SolicitudDetalleDTO;
import com.tutofast.dto.SolicitudEntradaDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.modelo.Asignatura;
import com.tutofast.modelo.Duracion;
import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.EstadoPropuesta;
import com.tutofast.modelo.EstadoSolicitud;
import com.tutofast.modelo.Facultad;
import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.PerfilEstudiante;
import com.tutofast.modelo.ProgramaAcademico;
import com.tutofast.modelo.Propuesta;
import com.tutofast.modelo.RegistroAuditoria;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.SolicitudTutoria;
import com.tutofast.modelo.TipoPropuesta;
import com.tutofast.modelo.TipoServicio;
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
import com.tutofast.repositorio.RepositorioUsuario;
import com.tutofast.seguridad.UsuarioAutenticado;

class ServicioSolicitudesTest {

    private static final String TEMA = "Ecuaciones diferenciales de primer orden y variables separables";
    private static final String DESCRIPCION = "Requiero explicación detallada del método de separación de variables "
            + "y resolución guiada de 3 ejercicios preparatorios para el taller";

    private final Map<String, Usuario> usuarios = new LinkedHashMap<>();
    private final Map<String, SolicitudTutoria> solicitudes = new LinkedHashMap<>();
    private final Map<String, Propuesta> propuestas = new LinkedHashMap<>();
    private final List<RegistroAuditoria> bitacora = new ArrayList<>();
    private final AlmacenamientoEnMemoria almacenamiento = new AlmacenamientoEnMemoria();
    private RepositorioSolicitudTutoria repoSolicitudes;
    private Asignatura asignatura;
    private ProgramaAcademico programa;
    private Facultad facultad;
    private Universidad universidad;
    private ServicioNotificaciones notificaciones;
    private ServicioSolicitudes servicio;
    private Usuario estudiante;
    private boolean fallarAlGuardar;

    private static String nuevoId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    @BeforeEach
    void preparar() {
        universidad = new Universidad();
        universidad.setId(nuevoId());
        universidad.setActiva(true);
        facultad = new Facultad();
        facultad.setId(nuevoId());
        facultad.setUniversidadId(universidad.getId());
        facultad.setActiva(true);
        programa = new ProgramaAcademico();
        programa.setId(nuevoId());
        programa.setFacultadId(facultad.getId());
        programa.setNombre("Ingeniería de Sistemas");
        programa.setActivo(true);
        asignatura = new Asignatura();
        asignatura.setId(nuevoId());
        asignatura.setProgramaId(programa.getId());
        asignatura.setNombre("Ecuaciones Diferenciales");
        asignatura.setActiva(true);

        RepositorioUniversidad repoU = mock(RepositorioUniversidad.class);
        when(repoU.findById(anyString())).thenAnswer(inv -> Optional.of(universidad).filter(u -> u.getId().equals(inv.getArgument(0))));
        RepositorioFacultad repoF = mock(RepositorioFacultad.class);
        when(repoF.findById(anyString())).thenAnswer(inv -> Optional.of(facultad).filter(f -> f.getId().equals(inv.getArgument(0))));
        RepositorioProgramaAcademico repoP = mock(RepositorioProgramaAcademico.class);
        when(repoP.findById(anyString())).thenAnswer(inv -> Optional.of(programa).filter(p -> p.getId().equals(inv.getArgument(0))));
        when(repoP.findAllById(any())).thenAnswer(inv -> List.of(programa));
        RepositorioAsignatura repoA = mock(RepositorioAsignatura.class);
        when(repoA.findById(anyString())).thenAnswer(inv -> Optional.of(asignatura).filter(a -> a.getId().equals(inv.getArgument(0))));
        when(repoA.findAllById(any())).thenAnswer(inv -> List.of(asignatura));

        RepositorioUsuario repoUsuarios = mock(RepositorioUsuario.class);
        when(repoUsuarios.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(usuarios.get(inv.<String>getArgument(0))));
        when(repoUsuarios.findAllById(any())).thenAnswer(inv -> new ArrayList<>(usuarios.values()));

        repoSolicitudes = mock(RepositorioSolicitudTutoria.class);
        when(repoSolicitudes.save(any(SolicitudTutoria.class))).thenAnswer(inv -> {
            if (fallarAlGuardar) {
                throw new IllegalStateException("fallo simulado de MongoDB");
            }
            SolicitudTutoria s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(nuevoId());
            }
            solicitudes.put(s.getId(), s);
            return s;
        });
        when(repoSolicitudes.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(solicitudes.get(inv.<String>getArgument(0))));
        when(repoSolicitudes.findByEstudianteIdOrderByFechaCreacionDesc(anyString())).thenAnswer(inv -> solicitudes.values().stream()
                .filter(s -> s.getEstudianteId().equals(inv.getArgument(0))).toList());

        RepositorioPropuesta repoPropuestas = mock(RepositorioPropuesta.class);
        when(repoPropuestas.findBySolicitudIdAndActivaTrue(anyString())).thenAnswer(inv -> propuestas.values().stream()
                .filter(p -> p.getSolicitudId().equals(inv.getArgument(0)) && p.isActiva()).toList());
        when(repoPropuestas.save(any(Propuesta.class))).thenAnswer(inv -> {
            Propuesta p = inv.getArgument(0);
            propuestas.put(p.getId(), p);
            return p;
        });

        notificaciones = mock(ServicioNotificaciones.class);
        estudiante = usuario("Estudiante Uno", EstadoCuenta.ACTIVA, Rol.ESTUDIANTE);
        servicio = crearServicio(List.of(), List.of(), repoU, repoF, repoP, repoA, repoUsuarios, repoPropuestas);
    }

    private ServicioSolicitudes crearServicio(List<String> frases, List<String> fraude, RepositorioUniversidad repoU,
                                              RepositorioFacultad repoF, RepositorioProgramaAcademico repoP,
                                              RepositorioAsignatura repoA, RepositorioUsuario repoUsuarios,
                                              RepositorioPropuesta repoPropuestas) {
        return new ServicioSolicitudes(repoSolicitudes, repoUsuarios, repoA, repoP, repoF, repoU, repoPropuestas,
                mock(RepositorioServicioTutoria.class), new ServicioArchivos(almacenamiento, List.of()),
                RepositorioAuditoriaEnMemoria.servicio(bitacora), notificaciones, frases, fraude);
    }

    private Usuario usuario(String nombre, EstadoCuenta estado, Rol... roles) {
        Usuario u = new Usuario();
        u.setId(nuevoId());
        u.setNombre(nombre);
        u.setCorreo(u.getId() + "@u.edu.co");
        u.setRoles(new HashSet<>(Set.of(roles)));
        u.setEstadoCuenta(estado);
        PerfilEstudiante perfil = new PerfilEstudiante();
        perfil.setIndicadorConfiabilidad(new BigDecimal("4.50"));
        u.setPerfilEstudiante(perfil);
        usuarios.put(u.getId(), u);
        return u;
    }

    private SolicitudEntradaDTO datos() {
        return datos(TEMA, DESCRIPCION);
    }

    private SolicitudEntradaDTO datos(String tema, String descripcion) {
        return new SolicitudEntradaDTO(asignatura.getId(), tema, descripcion, TipoServicio.RESOLUCION_GUIADA_EJERCICIOS,
                Instant.now().plus(3, ChronoUnit.DAYS), Modalidad.PRESENCIAL, "Biblioteca central, sala 3",
                Duracion.MIN_60, new BigDecimal("25000"));
    }

    private SolicitudEntradaDTO con(SolicitudEntradaDTO d, Duracion duracion, Instant fecha, BigDecimal precio,
                                    Modalidad modalidad, String zona) {
        return new SolicitudEntradaDTO(d.asignaturaId(), d.tema(), d.descripcion(), d.tipoServicio(), fecha,
                modalidad, zona, duracion, precio);
    }

    private SolicitudDetalleDTO publicar() {
        return servicio.publicarSolicitud(estudiante.getId(), datos(), List.of());
    }

    private static void assertNegocio(String codigo, HttpStatus estado, Runnable accion) {
        ExcepcionNegocio e = assertThrows(ExcepcionNegocio.class, accion::run);
        assertEquals(codigo, e.getCodigo());
        assertEquals(estado, e.getEstado());
    }

    // ---- Integridad ----

    @Test
    void descripcionGenericaSeRechazaConDescripcionInsuficiente() {
        assertNegocio("DESCRIPCION_INSUFICIENTE", HttpStatus.UNPROCESSABLE_ENTITY, () -> servicio
                .publicarSolicitud(estudiante.getId(), datos("Matemáticas", "necesito ayuda con matemáticas"), List.of()));
        assertTrue(solicitudes.isEmpty());
    }

    @Test
    void descripcionMuyCortaSeRechaza() {
        assertNegocio("DESCRIPCION_INSUFICIENTE", HttpStatus.UNPROCESSABLE_ENTITY, () -> servicio
                .publicarSolicitud(estudiante.getId(), datos("Cálculo", "derivadas"), List.of()));
    }

    @Test
    void fraudeAcademicoSeRechazaYSeAudita() {
        assertNegocio("FRAUDE_ACADEMICO", HttpStatus.UNPROCESSABLE_ENTITY, () -> servicio.publicarSolicitud(
                estudiante.getId(), datos("Parcial de cálculo", "Necesito resolver examen parcial en vivo mientras lo presento"),
                List.of()));
        assertTrue(solicitudes.isEmpty());
        assertEquals(1, bitacora.stream().filter(r -> "FRAUDE_ACADEMICO_DETECTADO".equals(r.getAccion())).count());
        assertEquals(estudiante.getId(), bitacora.get(0).getUsuarioId());
    }

    @Test
    void fraudeIgnoraTildesYMayusculas() {
        assertNegocio("FRAUDE_ACADEMICO", HttpStatus.UNPROCESSABLE_ENTITY, () -> servicio.publicarSolicitud(
                estudiante.getId(), datos("Evaluación", "Quiero que alguien HACER EL EXAMEN POR MÍ en la plataforma virtual"),
                List.of()));
    }

    @Test
    void listasConfigurablesReemplazanLasPorDefecto() {
        ServicioSolicitudes personalizado = new ServicioSolicitudes(repoSolicitudes, mock(RepositorioUsuario.class),
                mock(RepositorioAsignatura.class), mock(RepositorioProgramaAcademico.class),
                mock(RepositorioFacultad.class), mock(RepositorioUniversidad.class), mock(RepositorioPropuesta.class),
                mock(RepositorioServicioTutoria.class), new ServicioArchivos(almacenamiento, List.of()),
                RepositorioAuditoriaEnMemoria.servicio(bitacora), notificaciones, List.of("ayuda urgente"),
                List.of("pagar por mi nota"));
        SolicitudTutoria s = new SolicitudTutoria();
        s.setTema("Álgebra lineal");
        s.setDescripcion("Quiero pagar por mi nota en el curso de álgebra lineal de este semestre");
        s.setPrecioInicialCOP(BigDecimal.TEN);
        s.setDuracion(Duracion.MIN_60);
        s.setModalidad(Modalidad.VIRTUAL);
        s.setTipoServicio(TipoServicio.EXPLICACION_TEMA);
        s.setFechaHora(Instant.now());
        assertNegocio("FRAUDE_ACADEMICO", HttpStatus.UNPROCESSABLE_ENTITY, () -> personalizado.validarIntegridad("u1", s));
        s.setDescripcion("ayuda urgente con álgebra");
        assertNegocio("DESCRIPCION_INSUFICIENTE", HttpStatus.UNPROCESSABLE_ENTITY, () -> personalizado.validarIntegridad("u1", s));
    }

    // ---- Validaciones de publicación ----

    @Test
    void duracionDe90MinutosEs400() {
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> servicio.publicarSolicitud(estudiante.getId(),
                con(datos(), Duracion.MIN_90, Instant.now().plusSeconds(86400), new BigDecimal("25000"),
                        Modalidad.VIRTUAL, null), List.of()));
    }

    @Test
    void fechaPasadaPrecioNoPositivoYPresencialSinZonaSeRechazan() {
        Instant futuro = Instant.now().plusSeconds(86400);
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> servicio.publicarSolicitud(estudiante.getId(),
                con(datos(), Duracion.MIN_60, Instant.now().minusSeconds(60), BigDecimal.TEN, Modalidad.VIRTUAL, null),
                List.of()));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> servicio.publicarSolicitud(estudiante.getId(),
                con(datos(), Duracion.MIN_60, futuro, BigDecimal.ZERO, Modalidad.VIRTUAL, null), List.of()));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> servicio.publicarSolicitud(estudiante.getId(),
                con(datos(), Duracion.MIN_60, futuro, new BigDecimal("-5"), Modalidad.VIRTUAL, null), List.of()));
        assertNegocio("DATOS_INVALIDOS", HttpStatus.UNPROCESSABLE_ENTITY, () -> servicio.publicarSolicitud(
                estudiante.getId(), con(datos(), Duracion.MIN_60, futuro, BigDecimal.TEN, Modalidad.PRESENCIAL, " "),
                List.of()));
    }

    @Test
    void asignaturaInexistenteOInactivaSeRechaza() {
        asignatura.setActiva(false);
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> servicio.publicarSolicitud(estudiante.getId(), datos(), List.of()));
        asignatura.setActiva(true);
        programa.setActivo(false);
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> servicio.publicarSolicitud(estudiante.getId(), datos(), List.of()));
        programa.setActivo(true);
        universidad.setActiva(false);
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> servicio.publicarSolicitud(estudiante.getId(), datos(), List.of()));
        universidad.setActiva(true);
        SolicitudEntradaDTO sinAsignatura = datos();
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> servicio.publicarSolicitud(estudiante.getId(),
                new SolicitudEntradaDTO("no-existe", sinAsignatura.tema(), sinAsignatura.descripcion(),
                        sinAsignatura.tipoServicio(), sinAsignatura.fechaHora(), sinAsignatura.modalidad(),
                        sinAsignatura.zonaEncuentro(), sinAsignatura.duracion(), sinAsignatura.precioInicialCOP()),
                List.of()));
        assertTrue(solicitudes.isEmpty());
    }

    @Test
    void cuentaNoActivaORolIncorrectoNoPublican() {
        Usuario suspendido = usuario("Suspendido", EstadoCuenta.SUSPENDIDA, Rol.ESTUDIANTE);
        Usuario tutor = usuario("Solo tutor", EstadoCuenta.ACTIVA, Rol.TUTOR);
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.publicarSolicitud(suspendido.getId(), datos(), List.of()));
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.publicarSolicitud(tutor.getId(), datos(), List.of()));
        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.publicarSolicitud("desconocido", datos(), List.of()));
    }

    // ---- Publicación ----

    @Test
    void publicarDerivaElCatalogoYQuedaPublicada() {
        SolicitudDetalleDTO dto = publicar();
        assertEquals(EstadoSolicitud.PUBLICADA, dto.estado());
        assertEquals(estudiante.getId(), dto.estudianteId());
        assertEquals(universidad.getId(), dto.universidadId());
        assertEquals(facultad.getId(), dto.facultadId());
        assertEquals(programa.getId(), dto.programaId());
        assertEquals(0, new BigDecimal("25000").compareTo(dto.precioInicialCOP()));
        assertEquals(0, dto.totalPropuestas());
        assertNull(dto.servicioId());
        assertNotNull(solicitudes.get(dto.id()).getFechaCreacion());
        assertEquals(1, bitacora.stream().filter(r -> "SOLICITUD_PUBLICADA".equals(r.getAccion())).count());
    }

    @Test
    void publicarConArchivosLosGuardaEnAlmacenamiento() {
        SolicitudDetalleDTO dto = servicio.publicarSolicitud(estudiante.getId(), datos(),
                List.of(ArchivosDePrueba.pdf(), ArchivosDePrueba.png()));
        assertEquals(2, dto.archivos().size());
        assertEquals(2, almacenamiento.cantidad());
        assertTrue(almacenamiento.contiene(dto.archivos().get(0).archivoId()));
    }

    @Test
    void archivoNoPermitidoNoDejaNadaGuardado() {
        MockMultipartFile exe = new MockMultipartFile("archivos", "virus.exe", "application/octet-stream", new byte[] {1, 2, 3});
        assertThrows(ExcepcionNegocio.class, () -> servicio.publicarSolicitud(estudiante.getId(), datos(),
                List.of(ArchivosDePrueba.pdf(), exe)));
        assertEquals(0, almacenamiento.cantidad());
        assertTrue(solicitudes.isEmpty());
    }

    @Test
    void siPersistirFallaSeBorranLosArchivosSubidos() {
        fallarAlGuardar = true;
        assertThrows(IllegalStateException.class, () -> servicio.publicarSolicitud(estudiante.getId(), datos(),
                List.of(ArchivosDePrueba.pdf())));
        assertEquals(0, almacenamiento.cantidad());
    }

    @Test
    void validarAntesDeSubirNoTocaElAlmacenamiento() {
        assertNegocio("FRAUDE_ACADEMICO", HttpStatus.UNPROCESSABLE_ENTITY, () -> servicio.publicarSolicitud(
                estudiante.getId(), datos("Parcial", "Debo resolver examen parcial en vivo con apoyo externo"),
                List.of(ArchivosDePrueba.pdf())));
        assertEquals(0, almacenamiento.cantidad());
    }

    @Test
    void masDeCincoArchivosEs400() {
        List<MockMultipartFile> seis = List.of(ArchivosDePrueba.pdf(), ArchivosDePrueba.pdf(), ArchivosDePrueba.pdf(),
                ArchivosDePrueba.pdf(), ArchivosDePrueba.pdf(), ArchivosDePrueba.pdf());
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> servicio.publicarSolicitud(estudiante.getId(),
                datos(), new ArrayList<>(seis)));
        assertEquals(0, almacenamiento.cantidad());
    }

    // ---- Edición ----

    @Test
    void editarActualizaLosCamposDeLaSolicitudPropia() {
        SolicitudDetalleDTO creada = publicar();
        SolicitudEntradaDTO nuevo = con(datos("Integrales dobles", "Repaso de integrales dobles en coordenadas polares con ejemplos"),
                Duracion.MIN_120, Instant.now().plusSeconds(172800), new BigDecimal("40000"), Modalidad.VIRTUAL, null);
        SolicitudDetalleDTO editada = servicio.editarSolicitud(estudiante.getId(), creada.id(), nuevo);
        assertEquals("Integrales dobles", editada.tema());
        assertEquals(Duracion.MIN_120, editada.duracion());
        assertEquals(Modalidad.VIRTUAL, editada.modalidad());
        assertEquals(0, new BigDecimal("40000").compareTo(editada.precioInicialCOP()));
        assertEquals(EstadoSolicitud.PUBLICADA, editada.estado());
    }

    @Test
    void editarSolicitudAjenaEsProhibido() {
        SolicitudDetalleDTO creada = publicar();
        Usuario otro = usuario("Otro estudiante", EstadoCuenta.ACTIVA, Rol.ESTUDIANTE);
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.editarSolicitud(otro.getId(), creada.id(), datos()));
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.cancelarSolicitud(otro.getId(), creada.id()));
    }

    @Test
    void editarSolicitudFormalizadaEs409() {
        SolicitudDetalleDTO creada = publicar();
        solicitudes.get(creada.id()).setEstado(EstadoSolicitud.FORMALIZADA);
        assertNegocio("SOLICITUD_NO_EDITABLE", HttpStatus.CONFLICT,
                () -> servicio.editarSolicitud(estudiante.getId(), creada.id(), datos()));
    }

    @Test
    void editarConFraudeSeRechazaYConservaLosDatos() {
        SolicitudDetalleDTO creada = publicar();
        assertNegocio("FRAUDE_ACADEMICO", HttpStatus.UNPROCESSABLE_ENTITY, () -> servicio.editarSolicitud(
                estudiante.getId(), creada.id(), datos("Otro", "Hacer el examen por mí del próximo viernes por la tarde")));
        assertEquals(1, bitacora.stream().filter(r -> "FRAUDE_ACADEMICO_DETECTADO".equals(r.getAccion())).count());
    }

    @Test
    void solicitudInexistenteEs404() {
        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.editarSolicitud(estudiante.getId(), "x", datos()));
    }

    @Test
    void agregarArchivosRespetaElMaximoYElEstado() {
        SolicitudDetalleDTO creada = servicio.publicarSolicitud(estudiante.getId(), datos(),
                List.of(ArchivosDePrueba.pdf(), ArchivosDePrueba.pdf(), ArchivosDePrueba.pdf(), ArchivosDePrueba.pdf()));
        assertEquals(1, servicio.agregarArchivos(estudiante.getId(), creada.id(), List.of(ArchivosDePrueba.png())).size());
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> servicio.agregarArchivos(estudiante.getId(),
                creada.id(), List.of(ArchivosDePrueba.jpg())));
        solicitudes.get(creada.id()).setEstado(EstadoSolicitud.FORMALIZADA);
        assertNegocio("SOLICITUD_NO_EDITABLE", HttpStatus.CONFLICT, () -> servicio.agregarArchivos(estudiante.getId(),
                creada.id(), List.of(ArchivosDePrueba.jpg())));
    }

    // ---- Cancelación ----

    @Test
    void cancelarDescartaLasPropuestasActivasYNotificaAlTutor() {
        SolicitudDetalleDTO creada = publicar();
        Propuesta p = Propuesta.nueva(creada.id(), "tutor-1", TipoPropuesta.ACEPTA_PRECIO, new BigDecimal("25000"),
                Duracion.MIN_60, Modalidad.PRESENCIAL, null, Instant.now());
        p.setId("p1");
        propuestas.put(p.getId(), p);

        SolicitudDetalleDTO cancelada = servicio.cancelarSolicitud(estudiante.getId(), creada.id());

        assertEquals(EstadoSolicitud.CANCELADA, cancelada.estado());
        assertEquals(EstadoPropuesta.DESCARTADA, propuestas.get("p1").getEstado());
        verify(notificaciones).notificar(anyString(), any(TipoNotificacion.class), anyString(), anyString(), anyString());
    }

    @Test
    void cancelarDespuesDeFormalizadaEs409() {
        SolicitudDetalleDTO creada = publicar();
        solicitudes.get(creada.id()).setEstado(EstadoSolicitud.FORMALIZADA);
        assertNegocio("SOLICITUD_FORMALIZADA", HttpStatus.CONFLICT,
                () -> servicio.cancelarSolicitud(estudiante.getId(), creada.id()));
        assertEquals(EstadoSolicitud.FORMALIZADA, solicitudes.get(creada.id()).getEstado());
    }

    @Test
    void cancelarDosVecesEs409() {
        SolicitudDetalleDTO creada = publicar();
        servicio.cancelarSolicitud(estudiante.getId(), creada.id());
        assertNegocio("TRANSICION_INVALIDA", HttpStatus.CONFLICT,
                () -> servicio.cancelarSolicitud(estudiante.getId(), creada.id()));
    }

    // ---- Búsqueda y consulta ----

    @Test
    void buscarDevuelvePaginaConNombresDeCatalogoYEstudiante() {
        SolicitudDetalleDTO creada = publicar();
        when(repoSolicitudes.buscar(any(), any(Pageable.class))).thenAnswer(inv -> new PageImpl<>(
                List.of(solicitudes.get(creada.id())), inv.getArgument(1), 1));

        var pagina = servicio.buscarSolicitudes(FiltroSolicitudes.vacio(), 0, 12);

        assertEquals(1, pagina.totalElementos());
        var resumen = pagina.contenido().get(0);
        assertEquals("Ecuaciones Diferenciales", resumen.asignaturaNombre());
        assertEquals("Ingeniería de Sistemas", resumen.programaNombre());
        assertEquals("Estudiante Uno", resumen.estudianteNombre());
        assertEquals(0, new BigDecimal("4.50").compareTo(resumen.estudianteConfiabilidad()));
    }

    @Test
    void busquedaSinResultadosDevuelveContenidoVacio() {
        when(repoSolicitudes.buscar(any(), any(Pageable.class))).thenAnswer(inv -> new PageImpl<>(List.of(),
                inv.getArgument(1), 0));
        var pagina = servicio.buscarSolicitudes(FiltroSolicitudes.vacio(), 0, 12);
        assertTrue(pagina.contenido().isEmpty());
        assertEquals(0, pagina.totalElementos());
    }

    @Test
    void laPaginacionSeAcotaYPasaElFiltro() {
        when(repoSolicitudes.buscar(any(), any(Pageable.class))).thenAnswer(inv -> new PageImpl<>(List.of(),
                inv.getArgument(1), 0));
        FiltroSolicitudes filtro = new FiltroSolicitudes(null, null, null, asignatura.getId(), "cálculo",
                Modalidad.VIRTUAL, null, null, BigDecimal.TEN, null, null, FiltroSolicitudes.Orden.PRECIO_ASC);
        servicio.buscarSolicitudes(filtro, -3, 9999);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<FiltroSolicitudes> captorFiltro = ArgumentCaptor.forClass(FiltroSolicitudes.class);
        verify(repoSolicitudes).buscar(captorFiltro.capture(), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(ServicioSolicitudes.TAMANO_PAGINA_MAXIMO, captor.getValue().getPageSize());
        assertEquals("cálculo", captorFiltro.getValue().tema());
    }

    @Test
    void ordenDeTextoInterpretaLosValoresDeLaApi() {
        assertEquals(FiltroSolicitudes.Orden.PRECIO_DESC, FiltroSolicitudes.Orden.deTexto("precioDesc"));
        assertEquals(FiltroSolicitudes.Orden.RECIENTES, FiltroSolicitudes.Orden.deTexto(null));
        assertNegocio("VALIDACION", HttpStatus.BAD_REQUEST, () -> FiltroSolicitudes.Orden.deTexto("aleatorio"));
    }

    @Test
    void misSolicitudesSoloIncluyeLasPropias() {
        publicar();
        Usuario otro = usuario("Otro", EstadoCuenta.ACTIVA, Rol.ESTUDIANTE);
        servicio.publicarSolicitud(otro.getId(), datos(), List.of());
        assertEquals(1, servicio.misSolicitudes(estudiante.getId()).size());
    }

    @Test
    void detalleDeSolicitudCanceladaSoloLoVenDuenoYAdministrador() {
        SolicitudDetalleDTO creada = publicar();
        servicio.cancelarSolicitud(estudiante.getId(), creada.id());
        UsuarioAutenticado dueno = new UsuarioAutenticado(estudiante.getId(), "e@u.edu.co", "E", Set.of(Rol.ESTUDIANTE));
        UsuarioAutenticado tutor = new UsuarioAutenticado("t1", "t@u.edu.co", "T", Set.of(Rol.TUTOR));
        UsuarioAutenticado admin = new UsuarioAutenticado("a1", "a@u.edu.co", "A", Set.of(Rol.ADMINISTRADOR));
        assertEquals(EstadoSolicitud.CANCELADA, servicio.obtenerSolicitud(dueno, creada.id()).estado());
        assertEquals(EstadoSolicitud.CANCELADA, servicio.obtenerSolicitud(admin, creada.id()).estado());
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.obtenerSolicitud(tutor, creada.id()));
    }

    @Test
    void detalleDeSolicitudAbiertaLoVeCualquierUsuarioAutenticado() {
        SolicitudDetalleDTO creada = publicar();
        UsuarioAutenticado tutor = new UsuarioAutenticado("t1", "t@u.edu.co", "T", Set.of(Rol.TUTOR));
        assertEquals(creada.id(), servicio.obtenerSolicitud(tutor, creada.id()).id());
        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.obtenerSolicitud(tutor, "nada"));
    }
}
