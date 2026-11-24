package com.tutofast.modelo;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.http.HttpStatus;

import com.tutofast.excepcion.ExcepcionNegocio;

/** Solicitud de tutoría publicada por un estudiante (colección {@code solicitudes_tutoria}). */
@Document(collection = "solicitudes_tutoria", language = "spanish")
@CompoundIndexes({
        @CompoundIndex(name = "ix_busqueda", def = "{'estado': 1, 'asignaturaId': 1, 'modalidad': 1, 'fechaHora': 1}"),
        @CompoundIndex(name = "ix_programa", def = "{'estado': 1, 'programaId': 1, 'precioInicialCOP': 1}"),
        @CompoundIndex(name = "ix_mis_solicitudes", def = "{'estudianteId': 1, 'fechaCreacion': -1}")
})
public class SolicitudTutoria {

    public static final int DESCRIPCION_MINIMA = 20;
    /** Una descripción con menos palabras que esto y una frase genérica se considera vaga. */
    private static final int PALABRAS_DESCRIPCION_ESPECIFICA = 8;

    /** Frases por defecto (negociables, HU-03); el servicio puede pasar sus propias listas. */
    public static final List<String> FRASES_GENERICAS_POR_DEFECTO = List.of(
            "necesito ayuda", "ayuda con", "quiero ayuda", "ayudame", "explicame", "quiero aprender",
            "no entiendo nada", "necesito una tutoria", "necesito clases");
    public static final List<String> PALABRAS_FRAUDE_POR_DEFECTO = List.of(
            "resolver examen parcial en vivo", "examen en vivo", "evaluacion en vivo", "hacer evaluacion virtual por mi",
            "hacer el examen por mi", "hacer examen por mi", "resolver el quiz por mi", "suplantacion", "suplantar",
            "presentar el examen por mi", "presentar mi examen");

    @Id
    private String id;
    private String estudianteId;
    private String universidadId;
    private String facultadId;
    private String programaId;
    private String asignaturaId;
    @TextIndexed(weight = 2)
    private String tema;
    @TextIndexed
    private String descripcion;
    private TipoServicio tipoServicio;
    private Instant fechaHora;
    private Modalidad modalidad;
    private String zonaEncuentro;
    private Duracion duracion;
    private BigDecimal precioInicialCOP;
    private EstadoSolicitud estado = EstadoSolicitud.PUBLICADA;
    private int totalPropuestas;
    private Instant fechaCreacion;
    private Instant fechaActualizacion;
    private List<Archivo> archivos = new ArrayList<>();

    // ---- Integridad (R04, R06) ----

    /** Valida con las listas por defecto. */
    public void validarIntegridad() {
        validarIntegridad(FRASES_GENERICAS_POR_DEFECTO, PALABRAS_FRAUDE_POR_DEFECTO);
    }

    /**
     * Valida los datos de la solicitud sin E/S.
     *
     * @throws ExcepcionNegocio 422 con código {@code FRAUDE_ACADEMICO}, {@code DESCRIPCION_INSUFICIENTE} o {@code DATOS_INVALIDOS}
     */
    public void validarIntegridad(Collection<String> frasesGenericas, Collection<String> palabrasFraude) {
        String texto = normalizar(nvl(tema) + " " + nvl(descripcion));
        for (String frase : palabrasFraude) {
            if (texto.contains(normalizar(frase))) {
                throw new ExcepcionNegocio("FRAUDE_ACADEMICO",
                        "Solicitud rechazada: TutoFast prohíbe terminantemente la suplantación y la realización de "
                                + "evaluaciones calificables individuales ajenas (R06)", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        if (tema == null || tema.isBlank()) {
            throw datosInvalidos("El tema es obligatorio");
        }
        if (descripcion == null || descripcion.strip().length() < DESCRIPCION_MINIMA || esGenerica(descripcion, frasesGenericas)) {
            throw new ExcepcionNegocio("DESCRIPCION_INSUFICIENTE",
                    "La descripción debe tener al menos 20 caracteres y especificar el objetivo pedagógico del tema; "
                            + "no se admiten textos genéricos", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (precioInicialCOP == null || precioInicialCOP.signum() <= 0) {
            throw datosInvalidos("El precio inicial debe ser mayor que cero");
        }
        if (duracion == null || !duracion.permitidaEnSolicitud()) {
            throw datosInvalidos("La duración debe ser de 30, 60 o 120 minutos");
        }
        if (modalidad == null || tipoServicio == null || fechaHora == null) {
            throw datosInvalidos("La modalidad, el tipo de servicio y la fecha y hora son obligatorios");
        }
        if (modalidad == Modalidad.PRESENCIAL && (zonaEncuentro == null || zonaEncuentro.isBlank())) {
            throw datosInvalidos("La modalidad presencial exige una zona de encuentro segura");
        }
    }

    private static boolean esGenerica(String descripcion, Collection<String> frases) {
        String normalizada = normalizar(descripcion);
        boolean corta = normalizada.split(" ").length < PALABRAS_DESCRIPCION_ESPECIFICA;
        return corta && frases.stream().map(SolicitudTutoria::normalizar).anyMatch(normalizada::contains);
    }

    /** Minúsculas, sin tildes ni signos, espacios simples. */
    static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").strip();
    }

    private static ExcepcionNegocio datosInvalidos(String mensaje) {
        return new ExcepcionNegocio("DATOS_INVALIDOS", mensaje, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    // ---- Transiciones de estado (R10) ----

    /** Registra una propuesta nueva: la primera pasa PUBLICADA → PROPUESTAS_RECIBIDAS. */
    public void registrarPropuesta() {
        if (!estado.admitePropuestas()) {
            throw new ExcepcionNegocio("SOLICITUD_NO_ADMITE_PROPUESTAS",
                    "La solicitud ya ha sido acordada con otro tutor y no admite nuevas propuestas", HttpStatus.CONFLICT);
        }
        if (estado == EstadoSolicitud.PUBLICADA) {
            estado = EstadoSolicitud.PROPUESTAS_RECIBIDAS;
        }
        totalPropuestas++;
    }

    /** Una contrapropuesta o alternativa lleva la solicitud a NEGOCIACION_EN_CURSO. */
    public void iniciarNegociacion() {
        if (!estado.admitePropuestas()) {
            throw ExcepcionNegocio.transicionInvalida("la solicitud", estado, EstadoSolicitud.NEGOCIACION_EN_CURSO);
        }
        estado = EstadoSolicitud.NEGOCIACION_EN_CURSO;
    }

    public void seleccionarTutor() {
        if (estado != EstadoSolicitud.PROPUESTAS_RECIBIDAS && estado != EstadoSolicitud.NEGOCIACION_EN_CURSO) {
            throw ExcepcionNegocio.transicionInvalida("la solicitud", estado, EstadoSolicitud.TUTOR_SELECCIONADO);
        }
        estado = EstadoSolicitud.TUTOR_SELECCIONADO;
    }

    /** Confirmación bilateral: TUTOR_SELECCIONADO → FORMALIZADA. */
    public void confirmar() {
        if (estado != EstadoSolicitud.TUTOR_SELECCIONADO) {
            throw ExcepcionNegocio.transicionInvalida("la solicitud", estado, EstadoSolicitud.FORMALIZADA);
        }
        estado = EstadoSolicitud.FORMALIZADA;
    }

    /** Solo antes de confirmar (R05). */
    public void cancelar() {
        if (estado == EstadoSolicitud.FORMALIZADA) {
            throw new ExcepcionNegocio("SOLICITUD_FORMALIZADA",
                    "Cualquier cancelación posterior a la confirmación debe canalizarse mediante el módulo de reportes y soporte",
                    HttpStatus.CONFLICT);
        }
        if (estado == EstadoSolicitud.CANCELADA) {
            throw ExcepcionNegocio.transicionInvalida("la solicitud", estado, EstadoSolicitud.CANCELADA);
        }
        estado = EstadoSolicitud.CANCELADA;
    }

    /** R05: no se puede modificar tras la confirmación bilateral ni una vez cancelada. */
    public void validarEditable() {
        if (estado == EstadoSolicitud.FORMALIZADA || estado == EstadoSolicitud.CANCELADA) {
            throw new ExcepcionNegocio("SOLICITUD_NO_EDITABLE",
                    "La solicitud no puede modificarse tras la confirmación bilateral", HttpStatus.CONFLICT);
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEstudianteId() { return estudianteId; }
    public void setEstudianteId(String estudianteId) { this.estudianteId = estudianteId; }
    public String getUniversidadId() { return universidadId; }
    public void setUniversidadId(String universidadId) { this.universidadId = universidadId; }
    public String getFacultadId() { return facultadId; }
    public void setFacultadId(String facultadId) { this.facultadId = facultadId; }
    public String getProgramaId() { return programaId; }
    public void setProgramaId(String programaId) { this.programaId = programaId; }
    public String getAsignaturaId() { return asignaturaId; }
    public void setAsignaturaId(String asignaturaId) { this.asignaturaId = asignaturaId; }
    public String getTema() { return tema; }
    public void setTema(String tema) { this.tema = tema; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public TipoServicio getTipoServicio() { return tipoServicio; }
    public void setTipoServicio(TipoServicio tipoServicio) { this.tipoServicio = tipoServicio; }
    public Instant getFechaHora() { return fechaHora; }
    public void setFechaHora(Instant fechaHora) { this.fechaHora = fechaHora; }
    public Modalidad getModalidad() { return modalidad; }
    public void setModalidad(Modalidad modalidad) { this.modalidad = modalidad; }
    public String getZonaEncuentro() { return zonaEncuentro; }
    public void setZonaEncuentro(String zonaEncuentro) { this.zonaEncuentro = zonaEncuentro; }
    public Duracion getDuracion() { return duracion; }
    public void setDuracion(Duracion duracion) { this.duracion = duracion; }
    public BigDecimal getPrecioInicialCOP() { return precioInicialCOP; }
    public void setPrecioInicialCOP(BigDecimal precioInicialCOP) { this.precioInicialCOP = precioInicialCOP; }
    public EstadoSolicitud getEstado() { return estado; }
    public void setEstado(EstadoSolicitud estado) { this.estado = estado; }
    public int getTotalPropuestas() { return totalPropuestas; }
    public void setTotalPropuestas(int totalPropuestas) { this.totalPropuestas = totalPropuestas; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Instant fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public Instant getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(Instant fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
    public List<Archivo> getArchivos() { return archivos; }
    public void setArchivos(List<Archivo> archivos) { this.archivos = archivos; }
}
