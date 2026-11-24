package com.tutofast.modelo;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.http.HttpStatus;

import com.tutofast.excepcion.ExcepcionNegocio;

/**
 * Servicio acordado. Guarda las condiciones congeladas al confirmar (R09): ningún método modifica
 * {@code precioFinalCOP}, {@code duracion}, {@code modalidad} ni {@code comisionPct}.
 */
@Document(collection = "servicios_tutoria")
@CompoundIndexes({
        @CompoundIndex(name = "ix_servicio_estudiante", def = "{'estudianteId': 1, 'estado': 1}"),
        @CompoundIndex(name = "ix_servicio_tutor", def = "{'tutorId': 1, 'estado': 1}"),
        @CompoundIndex(name = "ix_servicio_programador", def = "{'estado': 1, 'fechaHora': 1}")
})
public class ServicioTutoria {

    /** Espera tras el fin programado antes del cierre automático (HU-08). */
    public static final Duration PLAZO_CIERRE_AUTOMATICO = Duration.ofHours(24);

    @Id
    private String id;
    @Indexed(unique = true, name = "uk_servicio_solicitud")
    private String solicitudId;
    @Indexed(unique = true, name = "uk_servicio_propuesta")
    private String propuestaId;
    private String estudianteId;
    private String tutorId;
    private BigDecimal precioFinalCOP;
    private Duracion duracion;
    private Modalidad modalidad;
    private String lugarOEnlace;
    private Instant fechaHora;
    private BigDecimal comisionPct;
    private Instant fechaConfirmacion;
    private Instant fechaInicio;
    private Instant fechaFinalizacion;
    private EstadoServicio estado = EstadoServicio.CONFIRMADO;
    /** Marcas de las tareas programadas para no repetir recordatorios. */
    private boolean recordatorioEnviado;
    private boolean recordatorioCalificacionEnviado;

    /**
     * Confirmación bilateral: congela las condiciones de la propuesta aceptada y la comisión vigente, y formaliza la
     * solicitud. Exige solicitud TUTOR_SELECCIONADO y propuesta ACEPTADA de esa misma solicitud.
     */
    public static ServicioTutoria confirmar(SolicitudTutoria solicitud, Propuesta propuesta,
                                            BigDecimal comisionPct, Instant ahora) {
        if (propuesta.getEstado() != EstadoPropuesta.ACEPTADA
                || (solicitud.getId() != null && !solicitud.getId().equals(propuesta.getSolicitudId()))) {
            throw new ExcepcionNegocio("PROPUESTA_NO_SELECCIONADA",
                    "La propuesta no ha sido seleccionada para esta solicitud", HttpStatus.CONFLICT);
        }
        solicitud.confirmar(); // exige TUTOR_SELECCIONADO
        ServicioTutoria s = new ServicioTutoria();
        s.solicitudId = solicitud.getId();
        s.propuestaId = propuesta.getId();
        s.estudianteId = solicitud.getEstudianteId();
        s.tutorId = propuesta.getTutorId();
        s.precioFinalCOP = propuesta.getPrecioCOP();
        s.duracion = propuesta.getDuracion();
        s.modalidad = propuesta.getModalidad();
        s.lugarOEnlace = propuesta.getModalidad() == Modalidad.PRESENCIAL ? solicitud.getZonaEncuentro() : null;
        s.fechaHora = solicitud.getFechaHora();
        s.comisionPct = comisionPct;
        s.fechaConfirmacion = ahora;
        return s;
    }

    /** CONFIRMADO → EN_DESARROLLO. */
    public void iniciarSesion(Instant ahora) {
        if (estado != EstadoServicio.CONFIRMADO) {
            throw ExcepcionNegocio.transicionInvalida("el servicio", estado, EstadoServicio.EN_DESARROLLO);
        }
        estado = EstadoServicio.EN_DESARROLLO;
        fechaInicio = ahora;
    }

    /** EN_DESARROLLO → FINALIZADO. */
    public void finalizar(Instant ahora) {
        if (estado == EstadoServicio.FINALIZADO) {
            throw new ExcepcionNegocio("SERVICIO_YA_FINALIZADO",
                    "El servicio ya se encuentra finalizado y no requiere nuevas acciones", HttpStatus.CONFLICT);
        }
        if (estado == EstadoServicio.EN_REVISION) {
            throw new ExcepcionNegocio("SERVICIO_EN_REVISION",
                    "El servicio está en revisión por un reporte y no puede finalizarse", HttpStatus.CONFLICT);
        }
        if (estado != EstadoServicio.EN_DESARROLLO) {
            throw ExcepcionNegocio.transicionInvalida("el servicio", estado, EstadoServicio.FINALIZADO);
        }
        estado = EstadoServicio.FINALIZADO;
        fechaFinalizacion = ahora;
    }

    /** Un reporte congela el servicio: CONFIRMADO | EN_DESARROLLO → EN_REVISION. */
    public void ponerEnRevision() {
        if (estado != EstadoServicio.CONFIRMADO && estado != EstadoServicio.EN_DESARROLLO) {
            throw ExcepcionNegocio.transicionInvalida("el servicio", estado, EstadoServicio.EN_REVISION);
        }
        estado = EstadoServicio.EN_REVISION;
    }

    /** Resolución del administrador: EN_REVISION → FINALIZADO (se libera el pago). */
    public void resolverFinalizando(Instant ahora) {
        exigirEnRevision(EstadoServicio.FINALIZADO);
        estado = EstadoServicio.FINALIZADO;
        fechaFinalizacion = ahora;
    }

    /** Resolución del administrador: EN_REVISION → CANCELADO (se reembolsa el pago). */
    public void resolverCancelando() {
        exigirEnRevision(EstadoServicio.CANCELADO);
        estado = EstadoServicio.CANCELADO;
    }

    private void exigirEnRevision(EstadoServicio destino) {
        if (estado != EstadoServicio.EN_REVISION) {
            throw ExcepcionNegocio.transicionInvalida("el servicio", estado, destino);
        }
    }

    /** Solo mientras está CONFIRMADO; precio, duración y modalidad son inmutables. */
    public void actualizarLugarOEnlace(String lugarOEnlace) {
        if (estado != EstadoServicio.CONFIRMADO) {
            throw new ExcepcionNegocio("SERVICIO_NO_EDITABLE",
                    "El enlace solo puede modificarse mientras el servicio está confirmado", HttpStatus.CONFLICT);
        }
        this.lugarOEnlace = lugarOEnlace;
    }

    /** Fin programado: {@code fechaHora + duracion}. */
    public Instant finProgramado() {
        return fechaHora.plus(Duration.ofMinutes(duracion.getMinutos()));
    }

    /** ¿Debe iniciarse automáticamente? (CONFIRMADO y llegó la hora). */
    public boolean debeIniciar(Instant ahora) {
        return estado == EstadoServicio.CONFIRMADO && !ahora.isBefore(fechaHora);
    }

    /** ¿Califica para el cierre automático? (EN_DESARROLLO y fin + 24 h vencido, salvo {@code ignorarPlazo}). */
    public boolean puedeCerrarseAutomaticamente(Instant ahora, boolean ignorarPlazo) {
        return estado == EstadoServicio.EN_DESARROLLO
                && (ignorarPlazo || !ahora.isBefore(finProgramado().plus(PLAZO_CIERRE_AUTOMATICO)));
    }

    public boolean esParticipante(String usuarioId) {
        return usuarioId != null && (usuarioId.equals(estudianteId) || usuarioId.equals(tutorId));
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSolicitudId() { return solicitudId; }
    public void setSolicitudId(String solicitudId) { this.solicitudId = solicitudId; }
    public String getPropuestaId() { return propuestaId; }
    public void setPropuestaId(String propuestaId) { this.propuestaId = propuestaId; }
    public String getEstudianteId() { return estudianteId; }
    public void setEstudianteId(String estudianteId) { this.estudianteId = estudianteId; }
    public String getTutorId() { return tutorId; }
    public void setTutorId(String tutorId) { this.tutorId = tutorId; }
    public BigDecimal getPrecioFinalCOP() { return precioFinalCOP; }
    public void setPrecioFinalCOP(BigDecimal precioFinalCOP) { this.precioFinalCOP = precioFinalCOP; }
    public Duracion getDuracion() { return duracion; }
    public void setDuracion(Duracion duracion) { this.duracion = duracion; }
    public Modalidad getModalidad() { return modalidad; }
    public void setModalidad(Modalidad modalidad) { this.modalidad = modalidad; }
    public String getLugarOEnlace() { return lugarOEnlace; }
    public void setLugarOEnlace(String lugarOEnlace) { this.lugarOEnlace = lugarOEnlace; }
    public Instant getFechaHora() { return fechaHora; }
    public void setFechaHora(Instant fechaHora) { this.fechaHora = fechaHora; }
    public BigDecimal getComisionPct() { return comisionPct; }
    public void setComisionPct(BigDecimal comisionPct) { this.comisionPct = comisionPct; }
    public Instant getFechaConfirmacion() { return fechaConfirmacion; }
    public void setFechaConfirmacion(Instant fechaConfirmacion) { this.fechaConfirmacion = fechaConfirmacion; }
    public Instant getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(Instant fechaInicio) { this.fechaInicio = fechaInicio; }
    public Instant getFechaFinalizacion() { return fechaFinalizacion; }
    public void setFechaFinalizacion(Instant fechaFinalizacion) { this.fechaFinalizacion = fechaFinalizacion; }
    public boolean isRecordatorioEnviado() { return recordatorioEnviado; }
    public void setRecordatorioEnviado(boolean recordatorioEnviado) { this.recordatorioEnviado = recordatorioEnviado; }
    public boolean isRecordatorioCalificacionEnviado() { return recordatorioCalificacionEnviado; }
    public void setRecordatorioCalificacionEnviado(boolean valor) { this.recordatorioCalificacionEnviado = valor; }
    public EstadoServicio getEstado() { return estado; }
    public void setEstado(EstadoServicio estado) { this.estado = estado; }
}
