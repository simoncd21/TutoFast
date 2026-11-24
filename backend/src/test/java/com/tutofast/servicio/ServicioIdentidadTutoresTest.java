package com.tutofast.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutofast.AlmacenamientoEnMemoria;
import com.tutofast.RepositorioAuditoriaEnMemoria;
import com.tutofast.RepositorioUsuarioEnMemoria;
import com.tutofast.dto.PerfilPublicoTutorDTO;
import com.tutofast.dto.TutorResumenDTO;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.modelo.Asignatura;
import com.tutofast.modelo.CalificacionAlTutor;
import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.EstadoSoporte;
import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.NivelVerificacion;
import com.tutofast.modelo.PerfilTutor;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.SoporteVerificacion;
import com.tutofast.modelo.TipoSoporte;
import com.tutofast.modelo.TipoTutor;
import com.tutofast.modelo.Usuario;
import com.tutofast.repositorio.FiltroTutores;
import com.tutofast.repositorio.RepositorioAsignatura;
import com.tutofast.repositorio.RepositorioCalificacion;
import com.tutofast.repositorio.RepositorioUsuario;
import com.tutofast.seguridad.JwtUtil;

/** Búsqueda pública de tutores, perfil público y reseñas de {@link ServicioIdentidad}. */
class ServicioIdentidadTutoresTest {

    private final Map<String, Usuario> almacen = new LinkedHashMap<>();
    private RepositorioUsuario repoUsuarios;
    private RepositorioAsignatura repoAsignaturas;
    private RepositorioCalificacion repoCalificaciones;
    private ServicioIdentidad servicio;

    @BeforeEach
    void preparar() {
        repoUsuarios = RepositorioUsuarioEnMemoria.crear(almacen);
        when(repoUsuarios.existsById(anyString())).thenAnswer(inv -> almacen.containsKey(inv.<String>getArgument(0)));
        when(repoUsuarios.findAllById(any())).thenAnswer(inv -> {
            List<Usuario> lista = new ArrayList<>();
            for (Object id : (Iterable<?>) inv.getArgument(0)) {
                if (almacen.containsKey(id)) {
                    lista.add(almacen.get(id));
                }
            }
            return lista;
        });
        when(repoUsuarios.buscarTutores(any(FiltroTutores.class), any(Pageable.class))).thenAnswer(inv -> {
            List<Usuario> tutores = almacen.values().stream().filter(u -> u.tieneRol(Rol.TUTOR)).toList();
            return new PageImpl<>(tutores, inv.<Pageable>getArgument(1), tutores.size());
        });
        repoAsignaturas = mock(RepositorioAsignatura.class);
        repoCalificaciones = mock(RepositorioCalificacion.class);
        servicio = new ServicioIdentidad(repoUsuarios, new BCryptPasswordEncoder(4),
                new JwtUtil("secreto-de-prueba-con-mas-de-32-caracteres-ok", Duration.ofHours(8)),
                RepositorioAuditoriaEnMemoria.servicio(new ArrayList<>()),
                new ServicioArchivos(new AlmacenamientoEnMemoria(), List.of()), mock(ServicioNotificaciones.class),
                repoAsignaturas, repoCalificaciones);
    }

    private Usuario tutor(String id, String nombre) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNombre(nombre);
        u.setCorreo(id + "@u.edu.co");
        u.setDocumento("1098765432");
        u.getRoles().add(Rol.TUTOR);
        PerfilTutor p = new PerfilTutor();
        p.setPresentacion("Monitora de cálculo");
        p.setTipoTutor(TipoTutor.MONITOR_CERTIFICADO);
        p.setModalidades(new ArrayList<>(List.of(Modalidad.VIRTUAL)));
        p.setAreasDeInteres(new ArrayList<>(List.of("Ecuaciones diferenciales")));
        p.setNivelVerificacion(NivelVerificacion.VERIFICADO);
        p.setPromedioCalificacion(new BigDecimal("4.75"));
        p.setServiciosCompletados(12);
        p.setCuentaBancariaCobro("Bancolombia ahorros ****4821");
        p.setAsignaturasDominadasIds(new ArrayList<>(List.of("asig1")));
        u.setPerfilTutor(p);
        almacen.put(id, u);
        return u;
    }

    private FiltroTutores filtroEnviado() {
        ArgumentCaptor<FiltroTutores> captor = ArgumentCaptor.forClass(FiltroTutores.class);
        verify(repoUsuarios).buscarTutores(captor.capture(), any(Pageable.class));
        return captor.getValue();
    }

    @Test
    void buscarDevuelveResumenesSinDatosPrivados() throws Exception {
        tutor("t1", "Laura Martínez");

        var pagina = servicio.buscarTutores(null, null, null, null, null, false, null, 0, 12);

        assertEquals(1, pagina.totalElementos());
        TutorResumenDTO t = pagina.contenido().get(0);
        assertEquals("Laura Martínez", t.nombre());
        assertTrue(t.verificado());
        assertEquals(new BigDecimal("4.75"), t.promedioCalificacion());
        assertEquals(12, t.serviciosCompletados());
        assertEquals(List.of(Modalidad.VIRTUAL), t.modalidades());
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(t);
        assertFalse(json.contains("4821") || json.contains("1098765432") || json.contains("@u.edu.co"), json);
        FiltroTutores f = filtroEnviado();
        assertEquals(FiltroTutores.Orden.CALIFICACION, f.orden());
        assertNull(f.asignaturaIds());
    }

    @Test
    void elProgramaSeExpandeASusAsignaturasYSeTransmitenLosDemasFiltros() {
        Asignatura a = new Asignatura();
        a.setId("asig1");
        Asignatura b = new Asignatura();
        b.setId("asig2");
        when(repoAsignaturas.findByProgramaIdOrderByNombreAsc("prog1")).thenReturn(List.of(a, b));

        servicio.buscarTutores("prog1", null, Modalidad.VIRTUAL, "L-J", new BigDecimal("4"), true, "servicios", 0, 5);

        FiltroTutores f = filtroEnviado();
        assertEquals(List.of("asig1", "asig2"), List.copyOf(f.asignaturaIds()));
        assertEquals(Modalidad.VIRTUAL, f.modalidad());
        assertEquals("L-J", f.disponibilidad());
        assertTrue(f.soloVerificados());
        assertEquals(FiltroTutores.Orden.SERVICIOS, f.orden());
    }

    @Test
    void laAsignaturaPrevaleceSobreElPrograma() {
        servicio.buscarTutores("prog1", "asig9", null, null, null, false, null, 0, 12);
        assertEquals(List.of("asig9"), List.copyOf(filtroEnviado().asignaturaIds()));
    }

    @Test
    void validaCalificacionMinimaYOrden() {
        ExcepcionNegocio e = assertThrows(ExcepcionNegocio.class,
                () -> servicio.buscarTutores(null, null, null, null, new BigDecimal("5.1"), false, null, 0, 12));
        assertEquals(HttpStatus.BAD_REQUEST, e.getEstado());
        assertThrows(ExcepcionNegocio.class,
                () -> servicio.buscarTutores(null, null, null, null, new BigDecimal("-1"), false, null, 0, 12));
        e = assertThrows(ExcepcionNegocio.class,
                () -> servicio.buscarTutores(null, null, null, null, null, false, "raro", 0, 12));
        assertEquals(HttpStatus.BAD_REQUEST, e.getEstado());
    }

    @Test
    void elTamanoDePaginaSeLimitaA50() {
        servicio.buscarTutores(null, null, null, null, null, false, null, -3, 500);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repoUsuarios).buscarTutores(any(FiltroTutores.class), captor.capture());
        assertEquals(50, captor.getValue().getPageSize());
        assertEquals(0, captor.getValue().getPageNumber());
    }

    @Test
    void perfilPublicoNoExponeCuentaBancariaDocumentoNiCorreo() throws Exception {
        Usuario u = tutor("t1", "Laura Martínez");
        SoporteVerificacion aprobado = new SoporteVerificacion();
        aprobado.setTipo(TipoSoporte.CERTIFICADO_MONITORIA);
        aprobado.setEstado(EstadoSoporte.APROBADO);
        aprobado.setFechaRevision(Instant.parse("2026-11-01T10:00:00Z"));
        SoporteVerificacion pendiente = new SoporteVerificacion();
        pendiente.setTipo(TipoSoporte.OTRO);
        pendiente.setEstado(EstadoSoporte.EN_REVISION);
        u.getPerfilTutor().getSoportes().addAll(List.of(aprobado, pendiente));
        Asignatura asig = new Asignatura();
        asig.setId("asig1");
        asig.setNombre("Ecuaciones Diferenciales");
        when(repoAsignaturas.findAllById(any())).thenReturn(List.of(asig));

        PerfilPublicoTutorDTO dto = servicio.obtenerPerfilPublicoTutor("t1");

        assertEquals("Laura Martínez", dto.nombre());
        assertEquals(List.of("Ecuaciones Diferenciales"), dto.declarado().asignaturas());
        assertEquals(NivelVerificacion.VERIFICADO, dto.verificado().nivelVerificacion());
        assertEquals(1, dto.verificado().soportesAprobados().size(), "solo los soportes aprobados");
        assertEquals(TipoSoporte.CERTIFICADO_MONITORIA, dto.verificado().soportesAprobados().get(0).tipo());
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(dto);
        assertFalse(json.contains("4821") || json.contains("1098765432") || json.contains("@u.edu.co"), json);
    }

    @Test
    void perfilPublicoNoExisteParaCuentasSuspendidasNiParaNoTutores() {
        Usuario suspendido = tutor("t1", "Suspendido");
        suspendido.setEstadoCuenta(EstadoCuenta.SUSPENDIDA);
        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.obtenerPerfilPublicoTutor("t1"));

        Usuario estudiante = new Usuario();
        estudiante.setId("e1");
        estudiante.getRoles().add(Rol.ESTUDIANTE);
        almacen.put("e1", estudiante);
        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.obtenerPerfilPublicoTutor("e1"));
        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.obtenerPerfilPublicoTutor("nadie"));
    }

    @Test
    void resenasVisiblesConNombreDelAutor() {
        tutor("t1", "Laura");
        Usuario autor = new Usuario();
        autor.setId("e1");
        autor.setNombre("Camila Rojas");
        almacen.put("e1", autor);
        CalificacionAlTutor c = new CalificacionAlTutor();
        c.setAutorId("e1");
        c.setDestinatarioId("t1");
        c.setComentario("Muy clara");
        c.setClaridadPedagogica(5);
        c.setDominioTematico(5);
        c.setPuntualidad(4);
        c.setCumplimiento(5);
        c.setTrato(5);
        c.setRelacionCalidadPrecio(4);
        c.setVisible(true);
        when(repoCalificaciones.findByDestinatarioIdAndVisibleTrueOrderByFechaEmisionDesc("t1")).thenReturn(List.of(c));

        var resenas = servicio.listarResenas("t1");

        assertEquals(1, resenas.size());
        assertEquals("Camila Rojas", resenas.get(0).autorNombre());
        assertEquals("Muy clara", resenas.get(0).comentario());
        assertEquals(0, new BigDecimal("4.67").compareTo(resenas.get(0).promedio()));
        assertEquals(6, resenas.get(0).criterios().size());
        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.listarResenas("no-existe"));
    }
}
