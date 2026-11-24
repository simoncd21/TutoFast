package com.tutofast.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import com.tutofast.RepositorioAuditoriaEnMemoria;
import com.tutofast.dto.AsignaturaDTO;
import com.tutofast.dto.CatalogoEntradaDTO;
import com.tutofast.dto.FacultadDTO;
import com.tutofast.dto.ProgramaDTO;
import com.tutofast.dto.UniversidadDTO;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.modelo.Asignatura;
import com.tutofast.modelo.Facultad;
import com.tutofast.modelo.ParametroComision;
import com.tutofast.modelo.ProgramaAcademico;
import com.tutofast.modelo.RegistroAuditoria;
import com.tutofast.modelo.TipoCatalogo;
import com.tutofast.modelo.Universidad;
import com.tutofast.repositorio.RepositorioAsignatura;
import com.tutofast.repositorio.RepositorioFacultad;
import com.tutofast.repositorio.RepositorioParametroComision;
import com.tutofast.repositorio.RepositorioProgramaAcademico;
import com.tutofast.repositorio.RepositorioUniversidad;

class ServicioCatalogoTest {

    private static final String ADMIN = "aaaaaaaaaaaaaaaaaaaaaaaa";

    private final Map<String, Universidad> universidades = new LinkedHashMap<>();
    private final Map<String, Facultad> facultades = new LinkedHashMap<>();
    private final Map<String, ProgramaAcademico> programas = new LinkedHashMap<>();
    private final Map<String, Asignatura> asignaturas = new LinkedHashMap<>();
    private final Map<String, ParametroComision> comisiones = new LinkedHashMap<>();
    private final List<RegistroAuditoria> bitacora = new ArrayList<>();
    private ServicioCatalogo servicio;
    /** Si no es nulo, guardar una comisión con ese porcentaje falla (simula una caída al activar la nueva). */
    private BigDecimal porcentajeQueFalla;

    private static String nuevoId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    @BeforeEach
    void preparar() {
        RepositorioUniversidad repoU = mock(RepositorioUniversidad.class);
        when(repoU.save(any(Universidad.class))).thenAnswer(inv -> guardar(universidades, inv.getArgument(0)));
        when(repoU.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(universidades.get(inv.<String>getArgument(0))));
        when(repoU.existsByNombre(anyString())).thenAnswer(inv -> universidades.values().stream()
                .anyMatch(u -> inv.getArgument(0).equals(u.getNombre())));
        when(repoU.findByNombre(anyString())).thenAnswer(inv -> universidades.values().stream()
                .filter(u -> inv.getArgument(0).equals(u.getNombre())).findFirst());

        RepositorioFacultad repoF = mock(RepositorioFacultad.class);
        when(repoF.save(any(Facultad.class))).thenAnswer(inv -> guardar(facultades, inv.getArgument(0)));
        when(repoF.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(facultades.get(inv.<String>getArgument(0))));
        when(repoF.existsByUniversidadIdAndNombre(anyString(), anyString())).thenAnswer(inv -> facultades.values().stream()
                .anyMatch(f -> f.getUniversidadId().equals(inv.getArgument(0)) && f.getNombre().equals(inv.getArgument(1))));
        when(repoF.findByUniversidadIdAndNombre(anyString(), anyString())).thenAnswer(inv -> facultades.values().stream()
                .filter(f -> f.getUniversidadId().equals(inv.getArgument(0)) && f.getNombre().equals(inv.getArgument(1)))
                .findFirst());

        RepositorioProgramaAcademico repoP = mock(RepositorioProgramaAcademico.class);
        when(repoP.save(any(ProgramaAcademico.class))).thenAnswer(inv -> guardar(programas, inv.getArgument(0)));
        when(repoP.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(programas.get(inv.<String>getArgument(0))));
        when(repoP.existsByFacultadIdAndNombre(anyString(), anyString())).thenAnswer(inv -> programas.values().stream()
                .anyMatch(p -> p.getFacultadId().equals(inv.getArgument(0)) && p.getNombre().equals(inv.getArgument(1))));
        when(repoP.findByFacultadIdAndNombre(anyString(), anyString())).thenAnswer(inv -> programas.values().stream()
                .filter(p -> p.getFacultadId().equals(inv.getArgument(0)) && p.getNombre().equals(inv.getArgument(1)))
                .findFirst());

        RepositorioAsignatura repoA = mock(RepositorioAsignatura.class);
        when(repoA.save(any(Asignatura.class))).thenAnswer(inv -> guardar(asignaturas, inv.getArgument(0)));
        when(repoA.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(asignaturas.get(inv.<String>getArgument(0))));
        when(repoA.existsByProgramaIdAndCodigo(anyString(), anyString())).thenAnswer(inv -> asignaturas.values().stream()
                .anyMatch(a -> a.getProgramaId().equals(inv.getArgument(0)) && a.getCodigo().equals(inv.getArgument(1))));
        when(repoA.findByProgramaIdAndActivaTrueOrderByNombreAsc(anyString())).thenAnswer(inv -> asignaturas.values().stream()
                .filter(a -> a.getProgramaId().equals(inv.getArgument(0)) && a.isActiva()).toList());
        when(repoA.findByProgramaIdOrderByNombreAsc(anyString())).thenAnswer(inv -> asignaturas.values().stream()
                .filter(a -> a.getProgramaId().equals(inv.getArgument(0))).toList());

        RepositorioParametroComision repoC = mock(RepositorioParametroComision.class);
        when(repoC.save(any(ParametroComision.class))).thenAnswer(inv -> {
            ParametroComision p = inv.getArgument(0);
            if (porcentajeQueFalla != null && p.getPorcentaje().compareTo(porcentajeQueFalla) == 0) {
                throw new IllegalStateException("fallo simulado");
            }
            if (p.getId() == null) {
                p.setId(nuevoId());
            }
            // Índice parcial único uk_comision_vigente: solo una con vigente=true.
            boolean otraVigente = comisiones.values().stream()
                    .anyMatch(x -> x != p && x.isVigente() && !x.getId().equals(p.getId()));
            if (p.isVigente() && otraVigente) {
                throw new DuplicateKeyException("E11000 uk_comision_vigente");
            }
            comisiones.put(p.getId(), p);
            return p;
        });
        when(repoC.findByVigenteTrue()).thenAnswer(inv -> comisiones.values().stream()
                .filter(ParametroComision::isVigente).findFirst());
        when(repoC.findAllByOrderByVigenteDesdeDesc()).thenAnswer(inv -> comisiones.values().stream()
                .sorted((a, b) -> b.getVigenteDesde().compareTo(a.getVigenteDesde())).toList());
        when(repoC.count()).thenAnswer(inv -> (long) comisiones.size());

        servicio = new ServicioCatalogo(repoU, repoF, repoP, repoA, repoC,
                RepositorioAuditoriaEnMemoria.servicio(bitacora));
    }

    @SuppressWarnings("unchecked")
    private static <T> T guardar(Map<String, T> almacen, T entidad) {
        try {
            var getId = entidad.getClass().getMethod("getId");
            var setId = entidad.getClass().getMethod("setId", String.class);
            if (getId.invoke(entidad) == null) {
                setId.invoke(entidad, nuevoId());
            }
            almacen.put((String) getId.invoke(entidad), entidad);
            return entidad;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private String crearJerarquia() {
        var u = (UniversidadDTO) servicio.gestionarCatalogo(ADMIN, TipoCatalogo.UNIVERSIDADES, null,
                new CatalogoEntradaDTO(null, null, null, null, "Universidad Nacional"));
        var f = (FacultadDTO) servicio.gestionarCatalogo(ADMIN, TipoCatalogo.FACULTADES, null,
                new CatalogoEntradaDTO(u.id(), null, null, null, "Ingeniería"));
        var p = (ProgramaDTO) servicio.gestionarCatalogo(ADMIN, TipoCatalogo.PROGRAMAS, null,
                new CatalogoEntradaDTO(null, f.id(), null, null, "Sistemas"));
        return p.id();
    }

    private CatalogoEntradaDTO asignatura(String programaId, String codigo, String nombre) {
        return new CatalogoEntradaDTO(null, null, programaId, codigo, nombre);
    }

    // ---------------------------------------------------------------- catálogo

    @Test
    void codigoDeAsignaturaDuplicadoEnElProgramaDevuelve409() {
        String programaId = crearJerarquia();
        servicio.gestionarCatalogo(ADMIN, TipoCatalogo.ASIGNATURAS, null,
                asignatura(programaId, "IS-204", "Ecuaciones Diferenciales"));

        var e = assertThrows(ExcepcionNegocio.class, () -> servicio.gestionarCatalogo(ADMIN,
                TipoCatalogo.ASIGNATURAS, null, asignatura(programaId, "is-204", "Otra materia")));

        assertEquals(HttpStatus.CONFLICT, e.getEstado());
        assertEquals("DUPLICADO", e.getCodigo());
        assertEquals("La asignatura con este código ya se encuentra registrada en el programa", e.getMessage());
        assertEquals(1, asignaturas.size());
    }

    @Test
    void creaAsignaturaNormalizandoElCodigoYLaAuditaYLaConsulta() {
        String programaId = crearJerarquia();

        var creada = (AsignaturaDTO) servicio.gestionarCatalogo(ADMIN, TipoCatalogo.ASIGNATURAS, null,
                asignatura(programaId, " is-204 ", " Ecuaciones Diferenciales "));

        assertEquals("IS-204", creada.codigo());
        assertEquals("Ecuaciones Diferenciales", creada.nombre());
        assertTrue(creada.activa());
        assertTrue(bitacora.stream().anyMatch(r -> r.getAccion().equals("CREAR_CATALOGO")
                && ADMIN.equals(r.getUsuarioId()) && r.getEntidadAfectada().equals("Asignatura")));
        assertEquals(1, servicio.consultarCatalogo(TipoCatalogo.ASIGNATURAS, programaId, true).size());
        var detalle = servicio.consultarAsignatura(creada.id());
        assertEquals("Sistemas", detalle.programa().nombre());
        assertEquals("Ingeniería", detalle.facultad().nombre());
        assertEquals("Universidad Nacional", detalle.universidad().nombre());
    }

    @Test
    void consultarUnProgramaDevuelveSuFacultadYUniversidad() {
        String programaId = crearJerarquia();

        var detalle = servicio.consultarPrograma(programaId);

        assertEquals("Sistemas", detalle.programa().nombre());
        assertEquals("Ingeniería", detalle.facultad().nombre());
        assertEquals("Universidad Nacional", detalle.universidad().nombre());
        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.consultarPrograma(nuevoId()));
    }

    @Test
    void asignaturaDesactivadaDejaDeListarseSoloActivas() {
        String programaId = crearJerarquia();
        var a = (AsignaturaDTO) servicio.gestionarCatalogo(ADMIN, TipoCatalogo.ASIGNATURAS, null,
                asignatura(programaId, "IS-101", "Cálculo"));

        servicio.cambiarEstadoCatalogo(ADMIN, TipoCatalogo.ASIGNATURAS, a.id(), false);

        assertTrue(servicio.consultarCatalogo(TipoCatalogo.ASIGNATURAS, programaId, true).isEmpty());
        assertEquals(1, servicio.consultarCatalogo(TipoCatalogo.ASIGNATURAS, programaId, false).size());
        assertTrue(bitacora.stream().anyMatch(r -> r.getAccion().equals("DESACTIVAR_CATALOGO")));
    }

    @Test
    void editarUnaAsignaturaNoPuedeChocarConElCodigoDeOtra() {
        String programaId = crearJerarquia();
        servicio.gestionarCatalogo(ADMIN, TipoCatalogo.ASIGNATURAS, null, asignatura(programaId, "IS-101", "Cálculo"));
        var b = (AsignaturaDTO) servicio.gestionarCatalogo(ADMIN, TipoCatalogo.ASIGNATURAS, null,
                asignatura(programaId, "IS-102", "Álgebra"));

        var e = assertThrows(ExcepcionNegocio.class, () -> servicio.gestionarCatalogo(ADMIN,
                TipoCatalogo.ASIGNATURAS, b.id(), asignatura(null, "IS-101", "Álgebra")));
        assertEquals(HttpStatus.CONFLICT, e.getEstado());

        // Conservar su propio código sí está permitido (solo cambia el nombre).
        var editada = (AsignaturaDTO) servicio.gestionarCatalogo(ADMIN, TipoCatalogo.ASIGNATURAS, b.id(),
                asignatura(null, "IS-102", "Álgebra Lineal"));
        assertEquals("Álgebra Lineal", editada.nombre());
    }

    @Test
    void facultadYProgramaDuplicadosDevuelven409() {
        String programaId = crearJerarquia();
        Facultad facultad = facultades.values().iterator().next();
        Universidad universidad = universidades.values().iterator().next();

        var eU = assertThrows(ExcepcionNegocio.class, () -> servicio.gestionarCatalogo(ADMIN,
                TipoCatalogo.UNIVERSIDADES, null, new CatalogoEntradaDTO(null, null, null, null, "Universidad Nacional")));
        var eF = assertThrows(ExcepcionNegocio.class, () -> servicio.gestionarCatalogo(ADMIN,
                TipoCatalogo.FACULTADES, null, new CatalogoEntradaDTO(universidad.getId(), null, null, null, "Ingeniería")));
        var eP = assertThrows(ExcepcionNegocio.class, () -> servicio.gestionarCatalogo(ADMIN,
                TipoCatalogo.PROGRAMAS, null, new CatalogoEntradaDTO(null, facultad.getId(), null, null, "Sistemas")));

        assertEquals(HttpStatus.CONFLICT, eU.getEstado());
        assertEquals(HttpStatus.CONFLICT, eF.getEstado());
        assertEquals(HttpStatus.CONFLICT, eP.getEstado());
        assertNotNull(programaId);
    }

    @Test
    void elPadreDebeExistirYElCodigoSerValido() {
        var sinPadre = assertThrows(ExcepcionNegocio.class, () -> servicio.gestionarCatalogo(ADMIN,
                TipoCatalogo.FACULTADES, null, new CatalogoEntradaDTO(null, null, null, null, "Ingeniería")));
        assertEquals(HttpStatus.BAD_REQUEST, sinPadre.getEstado());
        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.gestionarCatalogo(ADMIN, TipoCatalogo.PROGRAMAS,
                null, new CatalogoEntradaDTO(null, nuevoId(), null, null, "Sistemas")));

        String programaId = crearJerarquia();
        var sinCodigo = assertThrows(ExcepcionNegocio.class, () -> servicio.gestionarCatalogo(ADMIN,
                TipoCatalogo.ASIGNATURAS, null, asignatura(programaId, " ", "Cálculo")));
        var codigoRaro = assertThrows(ExcepcionNegocio.class, () -> servicio.gestionarCatalogo(ADMIN,
                TipoCatalogo.ASIGNATURAS, null, asignatura(programaId, "IS 1/2", "Cálculo")));
        assertEquals(HttpStatus.BAD_REQUEST, sinCodigo.getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, codigoRaro.getEstado());
    }

    @Test
    void tipoDeCatalogoDesconocidoNoSeEncuentra() {
        assertEquals(TipoCatalogo.ASIGNATURAS, TipoCatalogo.deSegmento("asignaturas"));
        assertThrows(ExcepcionNoEncontrado.class, () -> TipoCatalogo.deSegmento("colegios"));
    }

    // ---------------------------------------------------------------- comisión

    @Test
    void cambiarLaComisionCierraLaVigenteYDejaLaNuevaActiva() {
        servicio.sembrarDatosIniciales();
        ParametroComision inicial = comisiones.values().iterator().next();
        assertEquals(0, new BigDecimal("10").compareTo(servicio.consultarComision().porcentaje()));

        var nueva = servicio.configurarComision(ADMIN, new BigDecimal("12.5"));

        assertEquals(0, new BigDecimal("12.5").compareTo(nueva.porcentaje()));
        assertNull(nueva.vigenteHasta());
        assertNotNull(inicial.getVigenteHasta(), "la anterior queda cerrada");
        assertFalse(inicial.isVigente());
        assertEquals(1, comisiones.values().stream().filter(ParametroComision::isVigente).count());
        assertEquals(nueva.id(), servicio.consultarComision().id());
        var historial = servicio.consultarHistorialComision();
        assertEquals(2, historial.size());
        assertEquals(ADMIN, historial.get(0).modificadoPor());
        assertTrue(bitacora.stream().anyMatch(r -> r.getAccion().equals("CONFIGURAR_COMISION")
                && "10".equals(r.getDetalle().get("porcentajeAnterior"))));
    }

    @Test
    void laComisionDebeEstarEntre0Y50YDiferirDeLaVigente() {
        servicio.sembrarDatosIniciales();

        var fuera = assertThrows(ExcepcionNegocio.class, () -> servicio.configurarComision(ADMIN, new BigDecimal("51")));
        var negativa = assertThrows(ExcepcionNegocio.class, () -> servicio.configurarComision(ADMIN, new BigDecimal("-1")));
        var igual = assertThrows(ExcepcionNegocio.class, () -> servicio.configurarComision(ADMIN, new BigDecimal("10.0")));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, fuera.getEstado());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, negativa.getEstado());
        assertEquals(HttpStatus.CONFLICT, igual.getEstado());
        assertEquals(1, comisiones.size(), "no se crea ni se cierra nada");
        assertTrue(comisiones.values().iterator().next().isVigente());
    }

    @Test
    void siFallaActivarLaNuevaSeReabreLaAnterior() {
        servicio.sembrarDatosIniciales();
        porcentajeQueFalla = new BigDecimal("15");

        assertThrows(IllegalStateException.class, () -> servicio.configurarComision(ADMIN, new BigDecimal("15")));

        assertEquals(1, comisiones.size());
        ParametroComision original = comisiones.values().iterator().next();
        assertTrue(original.isVigente());
        assertNull(original.getVigenteHasta());
    }

    @Test
    void sinComisionConfiguradaLaConsultaDa404() {
        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.consultarComision());
    }

    // ---------------------------------------------------------------- seed

    @Test
    void elSeedEsIdempotenteYCreaElCatalogoDeDemoYLaComisionDel10() {
        servicio.sembrarDatosIniciales();
        servicio.sembrarDatosIniciales();

        assertEquals(1, universidades.size());
        assertEquals(1, facultades.size());
        assertEquals(1, programas.size());
        assertEquals(4, asignaturas.size());
        assertTrue(asignaturas.values().stream().anyMatch(a -> a.getCodigo().equals("IS-204")));
        assertEquals(1, comisiones.size());
        assertEquals(0, new BigDecimal("10").compareTo(servicio.consultarComision().porcentaje()));
    }

    @Test
    void elSeedNoPisaUnaComisionYaConfigurada() {
        servicio.sembrarDatosIniciales();
        servicio.configurarComision(ADMIN, new BigDecimal("15"));

        servicio.sembrarDatosIniciales();

        assertEquals(2, comisiones.size());
        assertEquals(0, new BigDecimal("15").compareTo(servicio.consultarComision().porcentaje()));
    }
}
