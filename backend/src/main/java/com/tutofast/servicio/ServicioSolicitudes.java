package com.tutofast.servicio;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.tutofast.dto.ArchivoDTO;
import com.tutofast.dto.Pagina;
import com.tutofast.dto.SolicitudDetalleDTO;
import com.tutofast.dto.SolicitudEntradaDTO;
import com.tutofast.dto.SolicitudResumenDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.mapper.SolicitudMapper;
import com.tutofast.modelo.Archivo;
import com.tutofast.modelo.Asignatura;
import com.tutofast.modelo.Facultad;
import com.tutofast.modelo.ProgramaAcademico;
import com.tutofast.modelo.Propuesta;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.SolicitudTutoria;
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

/** Publicación, edición, cancelación y exploración de solicitudes de tutoría (R04–R07). */
@Service
public class ServicioSolicitudes {

    private static final Logger log = LoggerFactory.getLogger(ServicioSolicitudes.class);

    public static final int MAX_ARCHIVOS = 5;
    public static final int TAMANO_PAGINA_MAXIMO = 50;
    public static final int TAMANO_PAGINA_POR_DEFECTO = 12;

    private final RepositorioSolicitudTutoria solicitudes;
    private final RepositorioUsuario usuarios;
    private final RepositorioAsignatura asignaturas;
    private final RepositorioProgramaAcademico programas;
    private final RepositorioFacultad facultades;
    private final RepositorioUniversidad universidades;
    private final RepositorioPropuesta propuestas;
    private final RepositorioServicioTutoria servicios;
    private final ServicioArchivos archivos;
    private final ServicioAuditoria auditoria;
    private final ServicioNotificaciones notificaciones;
    private final List<String> frasesGenericas;
    private final List<String> palabrasFraude;

    /**
     * Las listas se configuran con {@code tutofast.solicitudes.frases-genericas} y
     * {@code tutofast.solicitudes.palabras-fraude} (separadas por coma); si están vacías se usan las del dominio.
     */
    public ServicioSolicitudes(RepositorioSolicitudTutoria solicitudes, RepositorioUsuario usuarios,
                               RepositorioAsignatura asignaturas, RepositorioProgramaAcademico programas,
                               RepositorioFacultad facultades, RepositorioUniversidad universidades,
                               RepositorioPropuesta propuestas, RepositorioServicioTutoria servicios,
                               ServicioArchivos archivos, ServicioAuditoria auditoria,
                               ServicioNotificaciones notificaciones,
                               @Value("${tutofast.solicitudes.frases-genericas:}") List<String> frasesGenericas,
                               @Value("${tutofast.solicitudes.palabras-fraude:}") List<String> palabrasFraude) {
        this.solicitudes = solicitudes;
        this.usuarios = usuarios;
        this.asignaturas = asignaturas;
        this.programas = programas;
        this.facultades = facultades;
        this.universidades = universidades;
        this.propuestas = propuestas;
        this.servicios = servicios;
        this.archivos = archivos;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
        this.frasesGenericas = limpiar(frasesGenericas, SolicitudTutoria.FRASES_GENERICAS_POR_DEFECTO);
        this.palabrasFraude = limpiar(palabrasFraude, SolicitudTutoria.PALABRAS_FRAUDE_POR_DEFECTO);
    }

    private static List<String> limpiar(List<String> configuradas, List<String> porDefecto) {
        List<String> validas = configuradas == null ? List.of()
                : configuradas.stream().filter(f -> f != null && !f.isBlank()).map(String::strip).toList();
        return validas.isEmpty() ? porDefecto : validas;
    }

    // ---- Publicar ----

    /** Orden: validar → subir a GridFS → persistir (si persistir falla se compensa borrando los archivos). */
    public SolicitudDetalleDTO publicarSolicitud(String estudianteId, SolicitudEntradaDTO dto,
                                                 List<MultipartFile> adjuntos) {
        exigirEstudianteActivo(estudianteId);
        List<MultipartFile> lista = sinVacios(adjuntos);
        if (lista.size() > MAX_ARCHIVOS) {
            throw datosInvalidos("Puede adjuntar hasta " + MAX_ARCHIVOS + " archivos por solicitud");
        }
        SolicitudTutoria solicitud = new SolicitudTutoria();
        solicitud.setEstudianteId(estudianteId);
        aplicar(solicitud, dto);
        Instant ahora = Instant.now();
        solicitud.setFechaCreacion(ahora);
        solicitud.setFechaActualizacion(ahora);

        List<Archivo> subidos = subir(lista);
        solicitud.setArchivos(subidos);
        try {
            solicitud = solicitudes.save(solicitud);
        } catch (RuntimeException e) {
            compensar(subidos);
            throw e;
        }
        auditoria.registrarAccion(estudianteId, "SOLICITUD_PUBLICADA", "solicitud:" + solicitud.getId(),
                Map.of("asignaturaId", solicitud.getAsignaturaId()));
        return SolicitudMapper.aDetalleDTO(solicitud, null);
    }

    // ---- Editar ----

    public SolicitudDetalleDTO editarSolicitud(String estudianteId, String solicitudId, SolicitudEntradaDTO dto) {
        exigirEstudianteActivo(estudianteId);
        SolicitudTutoria solicitud = obtenerPropia(estudianteId, solicitudId);
        solicitud.validarEditable();
        aplicar(solicitud, dto);
        solicitud.setFechaActualizacion(Instant.now());
        solicitud = solicitudes.save(solicitud);
        auditoria.registrarAccion(estudianteId, "SOLICITUD_EDITADA", "solicitud:" + solicitud.getId(), null);
        return SolicitudMapper.aDetalleDTO(solicitud, null);
    }

    /** Añade adjuntos a una solicitud propia aún no confirmada (máximo 5 en total). */
    public List<ArchivoDTO> agregarArchivos(String estudianteId, String solicitudId, List<MultipartFile> adjuntos) {
        exigirEstudianteActivo(estudianteId);
        SolicitudTutoria solicitud = obtenerPropia(estudianteId, solicitudId);
        solicitud.validarEditable();
        List<MultipartFile> lista = sinVacios(adjuntos);
        if (lista.isEmpty()) {
            throw datosInvalidos("Debe adjuntar al menos un archivo");
        }
        if (solicitud.getArchivos().size() + lista.size() > MAX_ARCHIVOS) {
            throw datosInvalidos("Una solicitud admite hasta " + MAX_ARCHIVOS + " archivos");
        }
        List<Archivo> subidos = subir(lista);
        solicitud.getArchivos().addAll(subidos);
        solicitud.setFechaActualizacion(Instant.now());
        try {
            solicitudes.save(solicitud);
        } catch (RuntimeException e) {
            compensar(subidos);
            throw e;
        }
        return SolicitudMapper.aArchivosDTO(subidos);
    }

    // ---- Cancelar ----

    /** R05: solo antes de la confirmación bilateral. Las propuestas activas pasan a DESCARTADA. */
    public SolicitudDetalleDTO cancelarSolicitud(String estudianteId, String solicitudId) {
        exigirEstudianteActivo(estudianteId);
        SolicitudTutoria solicitud = obtenerPropia(estudianteId, solicitudId);
        solicitud.cancelar();
        Instant ahora = Instant.now();
        solicitud.setFechaActualizacion(ahora);
        solicitud = solicitudes.save(solicitud);
        for (Propuesta p : propuestas.findBySolicitudIdAndActivaTrue(solicitudId)) {
            p.descartar(ahora);
            propuestas.save(p);
            notificarDescarte(p.getTutorId(), solicitud);
        }
        auditoria.registrarAccion(estudianteId, "SOLICITUD_CANCELADA", "solicitud:" + solicitudId, null);
        return SolicitudMapper.aDetalleDTO(solicitud, null);
    }

    // ---- Consultar ----

    public Pagina<SolicitudResumenDTO> buscarSolicitudes(FiltroSolicitudes filtro, int pagina, int tamano) {
        Page<SolicitudTutoria> resultado = solicitudes.buscar(filtro, paginacion(pagina, tamano));
        Map<String, SolicitudResumenDTO> resumenes = resumenes(resultado.getContent());
        return Pagina.de(resultado, s -> resumenes.get(s.getId()));
    }

    public List<SolicitudResumenDTO> misSolicitudes(String estudianteId) {
        List<SolicitudTutoria> propias = solicitudes.findByEstudianteIdOrderByFechaCreacionDesc(estudianteId);
        Map<String, SolicitudResumenDTO> resumenes = resumenes(propias);
        return propias.stream().map(s -> resumenes.get(s.getId())).toList();
    }

    public SolicitudDetalleDTO obtenerSolicitud(UsuarioAutenticado usuario, String solicitudId) {
        SolicitudTutoria solicitud = solicitudes.findById(solicitudId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Solicitud no encontrada"));
        boolean dueno = solicitud.getEstudianteId().equals(usuario.id());
        if (!dueno && !usuario.tieneAlgunRol(Rol.ADMINISTRADOR) && !solicitud.getEstado().visibleParaExplorar()) {
            throw new ExcepcionAutorizacion("No tiene acceso a esta solicitud");
        }
        String servicioId = servicios.findBySolicitudId(solicitudId).map(s -> s.getId()).orElse(null);
        return SolicitudMapper.aDetalleDTO(solicitud, servicioId);
    }

    // ---- Integridad ----

    /**
     * Valida la solicitud con las listas configuradas. Un intento de fraude académico se registra en la auditoría
     * antes de rechazarlo.
     */
    public void validarIntegridad(String estudianteId, SolicitudTutoria solicitud) {
        try {
            solicitud.validarIntegridad(frasesGenericas, palabrasFraude);
        } catch (ExcepcionNegocio e) {
            if ("FRAUDE_ACADEMICO".equals(e.getCodigo())) {
                auditoria.registrarAccion(estudianteId, "FRAUDE_ACADEMICO_DETECTADO", "solicitud",
                        Map.of("tema", String.valueOf(solicitud.getTema()), "regla", "R06"));
            }
            throw e;
        }
    }

    // ---- Internos ----

    private void aplicar(SolicitudTutoria solicitud, SolicitudEntradaDTO dto) {
        if (dto.duracion() == null || !dto.duracion().permitidaEnSolicitud()) {
            throw datosInvalidos("La duración debe ser de 30, 60 o 120 minutos");
        }
        if (dto.fechaHora() == null || !dto.fechaHora().isAfter(Instant.now())) {
            throw datosInvalidos("La fecha y hora de la tutoría deben ser futuras");
        }
        if (dto.precioInicialCOP() == null || dto.precioInicialCOP().signum() <= 0) {
            throw datosInvalidos("El precio inicial debe ser mayor que cero");
        }
        Asignatura asignatura = asignaturas.findById(dto.asignaturaId())
                .orElseThrow(() -> datosInvalidos("La asignatura seleccionada no existe"));
        ProgramaAcademico programa = programas.findById(asignatura.getProgramaId()).orElse(null);
        Facultad facultad = programa == null ? null : facultades.findById(programa.getFacultadId()).orElse(null);
        Universidad universidad = facultad == null ? null
                : universidades.findById(facultad.getUniversidadId()).orElse(null);
        if (!asignatura.isActiva() || programa == null || !programa.isActivo() || facultad == null
                || !facultad.isActiva() || universidad == null || !universidad.isActiva()) {
            throw datosInvalidos("La asignatura seleccionada no está vigente en el catálogo");
        }
        solicitud.setUniversidadId(universidad.getId());
        solicitud.setFacultadId(facultad.getId());
        solicitud.setProgramaId(programa.getId());
        solicitud.setAsignaturaId(asignatura.getId());
        solicitud.setTema(dto.tema() == null ? null : dto.tema().strip());
        solicitud.setDescripcion(dto.descripcion() == null ? null : dto.descripcion().strip());
        solicitud.setTipoServicio(dto.tipoServicio());
        solicitud.setFechaHora(dto.fechaHora());
        solicitud.setModalidad(dto.modalidad());
        solicitud.setZonaEncuentro(dto.zonaEncuentro() == null || dto.zonaEncuentro().isBlank()
                ? null : dto.zonaEncuentro().strip());
        solicitud.setDuracion(dto.duracion());
        solicitud.setPrecioInicialCOP(dto.precioInicialCOP().setScale(2, RoundingMode.HALF_UP));
        validarIntegridad(solicitud.getEstudianteId(), solicitud);
    }

    private void exigirEstudianteActivo(String estudianteId) {
        Usuario u = usuarios.findById(estudianteId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Usuario no encontrado"));
        if (!u.estaActiva()) {
            throw new ExcepcionAutorizacion("Su cuenta no está activa; no puede gestionar solicitudes");
        }
        if (!u.tieneRol(Rol.ESTUDIANTE)) {
            throw new ExcepcionAutorizacion("Solo los estudiantes pueden gestionar solicitudes");
        }
    }

    private SolicitudTutoria obtenerPropia(String estudianteId, String solicitudId) {
        SolicitudTutoria s = solicitudes.findById(solicitudId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Solicitud no encontrada"));
        if (!s.getEstudianteId().equals(estudianteId)) {
            throw new ExcepcionAutorizacion("Solo el dueño de la solicitud puede realizar esta acción");
        }
        return s;
    }

    private static List<MultipartFile> sinVacios(List<MultipartFile> adjuntos) {
        return adjuntos == null ? List.of() : adjuntos.stream().filter(a -> a != null && !a.isEmpty()).toList();
    }

    private List<Archivo> subir(List<MultipartFile> lista) {
        List<Archivo> subidos = new ArrayList<>();
        try {
            for (MultipartFile f : lista) {
                ArchivoDTO a = archivos.guardar(f);
                subidos.add(new Archivo(a.nombre(), a.tipo(), a.tamanoMB(), a.archivoId()));
            }
        } catch (RuntimeException e) {
            compensar(subidos);
            throw e;
        }
        return subidos;
    }

    private void compensar(List<Archivo> subidos) {
        for (Archivo a : subidos) {
            try {
                archivos.eliminar(a.getGridFsId());
            } catch (RuntimeException e) {
                log.warn("No se pudo compensar el archivo {}; lo limpiará la tarea de huérfanos", a.getGridFsId(), e);
            }
        }
    }

    private void notificarDescarte(String tutorId, SolicitudTutoria s) {
        try {
            notificaciones.notificar(tutorId, TipoNotificacion.PROPUESTA_DESCARTADA, "Solicitud cancelada",
                    "El estudiante canceló la solicitud \"" + s.getTema() + "\"; su propuesta fue descartada",
                    "solicitudes.html");
        } catch (RuntimeException e) {
            log.warn("No se pudo notificar al tutor {}", tutorId, e);
        }
    }

    private static PageRequest paginacion(int pagina, int tamano) {
        int t = tamano <= 0 ? TAMANO_PAGINA_POR_DEFECTO : Math.min(tamano, TAMANO_PAGINA_MAXIMO);
        return PageRequest.of(Math.max(pagina, 0), t);
    }

    /** Resuelve nombres de asignatura, programa y estudiante en lote (sin consultas por fila). */
    private Map<String, SolicitudResumenDTO> resumenes(List<SolicitudTutoria> lista) {
        Map<String, Asignatura> asig = indexar(asignaturas.findAllById(ids(lista, SolicitudTutoria::getAsignaturaId)),
                Asignatura::getId);
        Map<String, ProgramaAcademico> prog = indexar(programas.findAllById(ids(lista, SolicitudTutoria::getProgramaId)),
                ProgramaAcademico::getId);
        Map<String, Usuario> est = indexar(usuarios.findAllById(ids(lista, SolicitudTutoria::getEstudianteId)),
                Usuario::getId);
        Map<String, SolicitudResumenDTO> mapa = new HashMap<>();
        for (SolicitudTutoria s : lista) {
            Asignatura a = asig.get(s.getAsignaturaId());
            ProgramaAcademico p = prog.get(s.getProgramaId());
            mapa.put(s.getId(), SolicitudMapper.aResumenDTO(s, a == null ? null : a.getNombre(),
                    p == null ? null : p.getNombre(), est.get(s.getEstudianteId())));
        }
        return mapa;
    }

    private static Set<String> ids(List<SolicitudTutoria> lista, Function<SolicitudTutoria, String> campo) {
        return lista.stream().map(campo).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static <T> Map<String, T> indexar(Iterable<T> elementos, Function<T, String> clave) {
        Map<String, T> mapa = new HashMap<>();
        elementos.forEach(e -> mapa.put(clave.apply(e), e));
        return mapa;
    }

    private static ExcepcionNegocio datosInvalidos(String mensaje) {
        return new ExcepcionNegocio("VALIDACION", mensaje, HttpStatus.BAD_REQUEST);
    }
}
