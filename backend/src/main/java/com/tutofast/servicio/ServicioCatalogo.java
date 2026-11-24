package com.tutofast.servicio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.tutofast.dto.AsignaturaDTO;
import com.tutofast.dto.AsignaturaDetalleDTO;
import com.tutofast.dto.CatalogoEntradaDTO;
import com.tutofast.dto.ComisionDTO;
import com.tutofast.dto.ComisionHistorialDTO;
import com.tutofast.dto.ElementoCatalogoDTO;
import com.tutofast.dto.ProgramaDetalleDTO;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.mapper.CatalogoMapper;
import com.tutofast.modelo.Asignatura;
import com.tutofast.modelo.Facultad;
import com.tutofast.modelo.ParametroComision;
import com.tutofast.modelo.ProgramaAcademico;
import com.tutofast.modelo.TipoCatalogo;
import com.tutofast.modelo.Universidad;
import com.tutofast.repositorio.RepositorioAsignatura;
import com.tutofast.repositorio.RepositorioFacultad;
import com.tutofast.repositorio.RepositorioParametroComision;
import com.tutofast.repositorio.RepositorioProgramaAcademico;
import com.tutofast.repositorio.RepositorioUniversidad;

/** Catálogo académico (universidad → facultad → programa → asignatura) y comisión de la plataforma. */
@Service
public class ServicioCatalogo {

    private static final Logger log = LoggerFactory.getLogger(ServicioCatalogo.class);

    public static final BigDecimal COMISION_INICIAL = new BigDecimal("10");
    public static final String MENSAJE_ASIGNATURA_DUPLICADA =
            "La asignatura con este código ya se encuentra registrada en el programa";
    private static final Pattern PATRON_CODIGO = Pattern.compile("^[A-Z0-9][A-Z0-9._-]{1,19}$");

    private final RepositorioUniversidad universidades;
    private final RepositorioFacultad facultades;
    private final RepositorioProgramaAcademico programas;
    private final RepositorioAsignatura asignaturas;
    private final RepositorioParametroComision comisiones;
    private final ServicioAuditoria auditoria;

    public ServicioCatalogo(RepositorioUniversidad universidades, RepositorioFacultad facultades,
                            RepositorioProgramaAcademico programas, RepositorioAsignatura asignaturas,
                            RepositorioParametroComision comisiones, ServicioAuditoria auditoria) {
        this.universidades = universidades;
        this.facultades = facultades;
        this.programas = programas;
        this.asignaturas = asignaturas;
        this.comisiones = comisiones;
        this.auditoria = auditoria;
    }

    // ---------------------------------------------------------------- consulta

    /**
     * Lista un nivel del catálogo. {@code padreId} es la universidad (facultades), la facultad (programas) o el
     * programa (asignaturas); se ignora para universidades.
     */
    public List<? extends ElementoCatalogoDTO> consultarCatalogo(TipoCatalogo tipo, String padreId,
                                                                boolean soloActivas) {
        return switch (tipo) {
            case UNIVERSIDADES -> (soloActivas ? universidades.findByActivaTrueOrderByNombreAsc()
                    : universidades.findAllByOrderByNombreAsc()).stream().map(CatalogoMapper::aDTO).toList();
            case FACULTADES -> (soloActivas ? facultades.findByUniversidadIdAndActivaTrueOrderByNombreAsc(padreId)
                    : facultades.findByUniversidadIdOrderByNombreAsc(padreId)).stream()
                    .map(CatalogoMapper::aDTO).toList();
            case PROGRAMAS -> (soloActivas ? programas.findByFacultadIdAndActivoTrueOrderByNombreAsc(padreId)
                    : programas.findByFacultadIdOrderByNombreAsc(padreId)).stream()
                    .map(CatalogoMapper::aDTO).toList();
            case ASIGNATURAS -> (soloActivas ? asignaturas.findByProgramaIdAndActivaTrueOrderByNombreAsc(padreId)
                    : asignaturas.findByProgramaIdOrderByNombreAsc(padreId)).stream()
                    .map(CatalogoMapper::aDTO).toList();
        };
    }

    /** Programa con su facultad y universidad. */
    public ProgramaDetalleDTO consultarPrograma(String programaId) {
        ProgramaAcademico programa = programas.findById(programaId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Programa académico no encontrado"));
        Facultad facultad = facultades.findById(programa.getFacultadId())
                .orElseThrow(() -> new ExcepcionNoEncontrado("Facultad no encontrada"));
        Universidad universidad = universidades.findById(facultad.getUniversidadId())
                .orElseThrow(() -> new ExcepcionNoEncontrado("Universidad no encontrada"));
        return new ProgramaDetalleDTO(CatalogoMapper.aDTO(programa), CatalogoMapper.aDTO(facultad),
                CatalogoMapper.aDTO(universidad));
    }

    /** Asignatura con su jerarquía completa. */
    public AsignaturaDetalleDTO consultarAsignatura(String asignaturaId) {
        Asignatura asignatura = asignaturas.findById(asignaturaId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Asignatura no encontrada"));
        ProgramaAcademico programa = programas.findById(asignatura.getProgramaId())
                .orElseThrow(() -> new ExcepcionNoEncontrado("Programa académico no encontrado"));
        Facultad facultad = facultades.findById(programa.getFacultadId())
                .orElseThrow(() -> new ExcepcionNoEncontrado("Facultad no encontrada"));
        Universidad universidad = universidades.findById(facultad.getUniversidadId())
                .orElseThrow(() -> new ExcepcionNoEncontrado("Universidad no encontrada"));
        return new AsignaturaDetalleDTO(CatalogoMapper.aDTO(asignatura), CatalogoMapper.aDTO(programa),
                CatalogoMapper.aDTO(facultad), CatalogoMapper.aDTO(universidad));
    }

    // ---------------------------------------------------------------- gestión

    /**
     * Crea ({@code id == null}) o edita un elemento del catálogo. Rechaza con 409 los duplicados: nombre repetido
     * dentro del mismo padre y código de asignatura repetido dentro del programa.
     */
    public ElementoCatalogoDTO gestionarCatalogo(String administradorId, TipoCatalogo tipo, String id,
                                                 CatalogoEntradaDTO entrada) {
        String nombre = entrada.nombre().trim();
        if (nombre.isEmpty()) {
            throw validacion("El nombre es obligatorio");
        }
        ElementoCatalogoDTO resultado = switch (tipo) {
            case UNIVERSIDADES -> gestionarUniversidad(id, nombre);
            case FACULTADES -> gestionarFacultad(id, entrada.universidadId(), nombre);
            case PROGRAMAS -> gestionarPrograma(id, entrada.facultadId(), nombre);
            case ASIGNATURAS -> gestionarAsignatura(id, entrada.programaId(), normalizarCodigo(entrada.codigo()),
                    nombre);
        };
        auditoria.registrarAccion(administradorId, id == null ? "CREAR_CATALOGO" : "EDITAR_CATALOGO",
                entidad(tipo), detalle("tipo", tipo.segmento(), "nombre", nombre,
                        "id", identificador(resultado)));
        return resultado;
    }

    /** Borrado lógico: desactiva o reactiva un elemento sin eliminarlo ni tocar a sus hijos. */
    public ElementoCatalogoDTO cambiarEstadoCatalogo(String administradorId, TipoCatalogo tipo, String id,
                                                     boolean activa) {
        ElementoCatalogoDTO resultado = switch (tipo) {
            case UNIVERSIDADES -> {
                Universidad u = universidades.findById(id)
                        .orElseThrow(() -> new ExcepcionNoEncontrado("Universidad no encontrada"));
                u.setActiva(activa);
                yield CatalogoMapper.aDTO(universidades.save(u));
            }
            case FACULTADES -> {
                Facultad f = facultades.findById(id)
                        .orElseThrow(() -> new ExcepcionNoEncontrado("Facultad no encontrada"));
                f.setActiva(activa);
                yield CatalogoMapper.aDTO(facultades.save(f));
            }
            case PROGRAMAS -> {
                ProgramaAcademico p = programas.findById(id)
                        .orElseThrow(() -> new ExcepcionNoEncontrado("Programa académico no encontrado"));
                p.setActivo(activa);
                yield CatalogoMapper.aDTO(programas.save(p));
            }
            case ASIGNATURAS -> {
                Asignatura a = asignaturas.findById(id)
                        .orElseThrow(() -> new ExcepcionNoEncontrado("Asignatura no encontrada"));
                a.setActiva(activa);
                yield CatalogoMapper.aDTO(asignaturas.save(a));
            }
        };
        auditoria.registrarAccion(administradorId, activa ? "ACTIVAR_CATALOGO" : "DESACTIVAR_CATALOGO",
                entidad(tipo), detalle("tipo", tipo.segmento(), "id", id));
        return resultado;
    }

    private ElementoCatalogoDTO gestionarUniversidad(String id, String nombre) {
        Universidad universidad = id == null ? new Universidad() : universidades.findById(id)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Universidad no encontrada"));
        if (!nombre.equals(universidad.getNombre()) && universidades.existsByNombre(nombre)) {
            throw duplicado("Ya existe una universidad con ese nombre");
        }
        universidad.setNombre(nombre);
        return CatalogoMapper.aDTO(guardar(() -> universidades.save(universidad)));
    }

    private ElementoCatalogoDTO gestionarFacultad(String id, String universidadId, String nombre) {
        Facultad facultad;
        if (id == null) {
            exigirPadre(universidadId, "universidadId");
            universidades.findById(universidadId)
                    .orElseThrow(() -> new ExcepcionNoEncontrado("Universidad no encontrada"));
            facultad = new Facultad();
            facultad.setUniversidadId(universidadId);
        } else {
            facultad = facultades.findById(id).orElseThrow(() -> new ExcepcionNoEncontrado("Facultad no encontrada"));
        }
        if (!nombre.equals(facultad.getNombre())
                && facultades.existsByUniversidadIdAndNombre(facultad.getUniversidadId(), nombre)) {
            throw duplicado("Ya existe una facultad con ese nombre en la universidad");
        }
        facultad.setNombre(nombre);
        Facultad base = facultad;
        return CatalogoMapper.aDTO(guardar(() -> facultades.save(base)));
    }

    private ElementoCatalogoDTO gestionarPrograma(String id, String facultadId, String nombre) {
        ProgramaAcademico programa;
        if (id == null) {
            exigirPadre(facultadId, "facultadId");
            facultades.findById(facultadId).orElseThrow(() -> new ExcepcionNoEncontrado("Facultad no encontrada"));
            programa = new ProgramaAcademico();
            programa.setFacultadId(facultadId);
        } else {
            programa = programas.findById(id)
                    .orElseThrow(() -> new ExcepcionNoEncontrado("Programa académico no encontrado"));
        }
        if (!nombre.equals(programa.getNombre())
                && programas.existsByFacultadIdAndNombre(programa.getFacultadId(), nombre)) {
            throw duplicado("Ya existe un programa con ese nombre en la facultad");
        }
        programa.setNombre(nombre);
        ProgramaAcademico base = programa;
        return CatalogoMapper.aDTO(guardar(() -> programas.save(base)));
    }

    private ElementoCatalogoDTO gestionarAsignatura(String id, String programaId, String codigo, String nombre) {
        Asignatura asignatura;
        if (id == null) {
            exigirPadre(programaId, "programaId");
            programas.findById(programaId)
                    .orElseThrow(() -> new ExcepcionNoEncontrado("Programa académico no encontrado"));
            asignatura = new Asignatura();
            asignatura.setProgramaId(programaId);
        } else {
            asignatura = asignaturas.findById(id)
                    .orElseThrow(() -> new ExcepcionNoEncontrado("Asignatura no encontrada"));
        }
        if (!codigo.equals(asignatura.getCodigo())
                && asignaturas.existsByProgramaIdAndCodigo(asignatura.getProgramaId(), codigo)) {
            throw duplicado(MENSAJE_ASIGNATURA_DUPLICADA);
        }
        asignatura.setCodigo(codigo);
        asignatura.setNombre(nombre);
        Asignatura base = asignatura;
        return CatalogoMapper.aDTO(guardar(() -> asignaturas.save(base)));
    }

    // ---------------------------------------------------------------- comisión

    /** Comisión vigente; 404 si todavía no se ha configurado ninguna. */
    public ComisionDTO consultarComision() {
        return CatalogoMapper.aDTO(comisiones.findByVigenteTrue()
                .orElseThrow(() -> new ExcepcionNoEncontrado("Aún no hay una comisión vigente configurada")));
    }

    /** Historial completo, la más reciente primero. */
    public List<ComisionHistorialDTO> consultarHistorialComision() {
        return comisiones.findAllByOrderByVigenteDesdeDesc().stream().map(CatalogoMapper::aHistorialDTO).toList();
    }

    /**
     * Cambia la comisión. El índice parcial único {@code uk_comision_vigente} admite una sola vigente, así que
     * primero se cierra la anterior y después se inserta la nueva; si la inserción falla se reabre la anterior.
     * Solo afecta a confirmaciones futuras: los servicios ya confirmados conservan su {@code comisionPct}.
     */
    public ComisionDTO configurarComision(String administradorId, BigDecimal porcentaje) {
        Instant ahora = Instant.now();
        ParametroComision nueva = ParametroComision.nuevo(porcentaje, ahora, administradorId);
        ParametroComision anterior = comisiones.findByVigenteTrue().orElse(null);
        if (anterior != null && anterior.getPorcentaje().compareTo(porcentaje) == 0) {
            throw new ExcepcionNegocio("ESTADO_INVALIDO",
                    "El porcentaje solicitado ya es la comisión vigente", HttpStatus.CONFLICT);
        }
        if (anterior != null) {
            anterior.cerrar(ahora);
            comisiones.save(anterior);
        }
        ParametroComision guardada;
        try {
            guardada = comisiones.save(nueva);
        } catch (DuplicateKeyException e) {
            // Otro administrador activó una comisión al mismo tiempo: la suya queda vigente.
            throw new ExcepcionNegocio("DUPLICADO",
                    "La comisión fue modificada por otro administrador; vuelva a intentarlo", HttpStatus.CONFLICT);
        } catch (RuntimeException e) {
            if (anterior != null) {
                anterior.setVigenteHasta(null);
                comisiones.save(anterior);
            }
            throw e;
        }
        auditoria.registrarAccion(administradorId, "CONFIGURAR_COMISION", "ParametroComision",
                detalle("porcentajeAnterior", anterior == null ? null : anterior.getPorcentaje().toPlainString(),
                        "porcentajeNuevo", porcentaje.toPlainString()));
        return CatalogoMapper.aDTO(guardada);
    }

    // ---------------------------------------------------------------- seed

    /**
     * Datos de demostración (idempotente): una universidad con su facultad, programa y asignaturas, y la comisión
     * inicial del 10 % si todavía no existe ninguna. No pisa nada que ya exista.
     */
    public void sembrarDatosIniciales() {
        Universidad universidad = universidades.findByNombre("Universidad Demo TutoFast").orElseGet(() -> {
            Universidad u = new Universidad();
            u.setNombre("Universidad Demo TutoFast");
            return universidades.save(u);
        });
        Facultad facultad = facultades.findByUniversidadIdAndNombre(universidad.getId(), "Facultad de Ingeniería")
                .orElseGet(() -> {
                    Facultad f = new Facultad();
                    f.setUniversidadId(universidad.getId());
                    f.setNombre("Facultad de Ingeniería");
                    return facultades.save(f);
                });
        ProgramaAcademico programa = programas.findByFacultadIdAndNombre(facultad.getId(), "Ingeniería de Sistemas")
                .orElseGet(() -> {
                    ProgramaAcademico p = new ProgramaAcademico();
                    p.setFacultadId(facultad.getId());
                    p.setNombre("Ingeniería de Sistemas");
                    return programas.save(p);
                });
        Map<String, String> demo = new LinkedHashMap<>();
        demo.put("IS-101", "Cálculo Diferencial");
        demo.put("IS-204", "Ecuaciones Diferenciales");
        demo.put("IS-210", "Programación Orientada a Objetos");
        demo.put("IS-305", "Bases de Datos");
        demo.forEach((codigo, nombre) -> {
            if (!asignaturas.existsByProgramaIdAndCodigo(programa.getId(), codigo)) {
                Asignatura a = new Asignatura();
                a.setProgramaId(programa.getId());
                a.setCodigo(codigo);
                a.setNombre(nombre);
                asignaturas.save(a);
            }
        });

        if (comisiones.count() == 0) {
            comisiones.save(ParametroComision.nuevo(COMISION_INICIAL, Instant.now(), null));
            auditoria.registrarAccion(null, "SEED_COMISION", "ParametroComision",
                    detalle("porcentaje", COMISION_INICIAL.toPlainString()));
            log.info("Comisión inicial del {} % creada", COMISION_INICIAL);
        }
    }

    // ---------------------------------------------------------------- auxiliares

    private static String normalizarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw validacion("El código de la asignatura es obligatorio");
        }
        String normalizado = codigo.trim().toUpperCase(Locale.ROOT);
        if (!PATRON_CODIGO.matcher(normalizado).matches()) {
            throw validacion("El código solo admite letras, números, punto, guion y guion bajo (2 a 20 caracteres)");
        }
        return normalizado;
    }

    private static void exigirPadre(String padreId, String campo) {
        if (padreId == null || padreId.isBlank()) {
            throw validacion("El campo " + campo + " es obligatorio");
        }
    }

    /** Traduce la violación de un índice único (condición de carrera) a 409. */
    private static <T> T guardar(java.util.function.Supplier<T> operacion) {
        try {
            return operacion.get();
        } catch (DuplicateKeyException e) {
            throw duplicado("Ya existe un registro con esos datos");
        }
    }

    private static String entidad(TipoCatalogo tipo) {
        return switch (tipo) {
            case UNIVERSIDADES -> "Universidad";
            case FACULTADES -> "Facultad";
            case PROGRAMAS -> "ProgramaAcademico";
            case ASIGNATURAS -> "Asignatura";
        };
    }

    private static String identificador(ElementoCatalogoDTO elemento) {
        return switch (elemento) {
            case com.tutofast.dto.UniversidadDTO u -> u.id();
            case com.tutofast.dto.FacultadDTO f -> f.id();
            case com.tutofast.dto.ProgramaDTO p -> p.id();
            case AsignaturaDTO a -> a.id();
        };
    }

    private static ExcepcionNegocio duplicado(String mensaje) {
        return new ExcepcionNegocio("DUPLICADO", mensaje, HttpStatus.CONFLICT);
    }

    private static ExcepcionNegocio validacion(String mensaje) {
        return new ExcepcionNegocio("VALIDACION", mensaje, HttpStatus.BAD_REQUEST);
    }

    private static Map<String, Object> detalle(Object... pares) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pares.length; i += 2) {
            mapa.put(String.valueOf(pares[i]), pares[i + 1]);
        }
        return mapa;
    }
}
