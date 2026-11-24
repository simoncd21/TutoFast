package com.tutofast.modelo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.http.HttpStatus;

import com.tutofast.excepcion.ExcepcionNegocio;

/** Reporte o reclamación contra otro usuario, opcionalmente ligado a un servicio. */
@Document(collection = "reportes")
@CompoundIndexes({
        @CompoundIndex(name = "ix_reporte_estado_fecha", def = "{'estado': 1, 'fechaRadicacion': -1}"),
        @CompoundIndex(name = "ix_reporte_reportante", def = "{'reportanteId': 1}"),
        @CompoundIndex(name = "ix_reporte_denunciado", def = "{'denunciadoId': 1}"),
        @CompoundIndex(name = "ix_reporte_servicio", def = "{'servicioId': 1}")
})
public class Reporte {

    /** Decisión del administrador sobre el reporte. */
    public static class Resolucion {
        private DecisionReporte decision;
        private AccionPago accionPago;
        private String observacion;
        private String resueltoPor;
        private Instant fecha;

        public Resolucion() {
        }

        public Resolucion(DecisionReporte decision, AccionPago accionPago, String observacion,
                          String resueltoPor, Instant fecha) {
            this.decision = decision;
            this.accionPago = accionPago;
            this.observacion = observacion;
            this.resueltoPor = resueltoPor;
            this.fecha = fecha;
        }

        public DecisionReporte getDecision() { return decision; }
        public void setDecision(DecisionReporte decision) { this.decision = decision; }
        public AccionPago getAccionPago() { return accionPago; }
        public void setAccionPago(AccionPago accionPago) { this.accionPago = accionPago; }
        public String getObservacion() { return observacion; }
        public void setObservacion(String observacion) { this.observacion = observacion; }
        public String getResueltoPor() { return resueltoPor; }
        public void setResueltoPor(String resueltoPor) { this.resueltoPor = resueltoPor; }
        public Instant getFecha() { return fecha; }
        public void setFecha(Instant fecha) { this.fecha = fecha; }
    }

    @Id
    private String id;
    private String reportanteId;
    private String denunciadoId;
    private String servicioId;
    private TipoReporte tipo;
    private String motivo;
    private List<String> evidencias = new ArrayList<>();
    private EstadoReporte estado = EstadoReporte.RADICADO;
    private Instant fechaRadicacion;
    private Resolucion resolucion;

    public static Reporte radicar(String reportanteId, String denunciadoId, String servicioId, TipoReporte tipo,
                                  String motivo, List<String> evidencias, Instant ahora) {
        if (reportanteId != null && reportanteId.equals(denunciadoId)) {
            throw new ExcepcionNegocio("AUTORREPORTE", "No puedes reportarte a ti mismo", HttpStatus.FORBIDDEN);
        }
        if (motivo == null || motivo.isBlank()) {
            throw new ExcepcionNegocio("El motivo del reporte es obligatorio");
        }
        Reporte r = new Reporte();
        r.reportanteId = reportanteId;
        r.denunciadoId = denunciadoId;
        r.servicioId = servicioId;
        r.tipo = tipo;
        r.motivo = motivo;
        r.evidencias = evidencias == null ? new ArrayList<>() : new ArrayList<>(evidencias);
        r.fechaRadicacion = ahora;
        return r;
    }

    public CategoriaReporte categoria() {
        return tipo.getCategoria();
    }

    public boolean estaAbierto() {
        return estado == EstadoReporte.RADICADO || estado == EstadoReporte.EN_REVISION;
    }

    /** RADICADO → EN_REVISION. */
    public void iniciarRevision() {
        if (estado != EstadoReporte.RADICADO) {
            throw ExcepcionNegocio.transicionInvalida("el reporte", estado, EstadoReporte.EN_REVISION);
        }
        estado = EstadoReporte.EN_REVISION;
    }

    /** EN_REVISION → RESUELTO (o DESESTIMADO si la decisión es DESESTIMADO). */
    public void resolver(DecisionReporte decision, AccionPago accionPago, String observacion,
                         String administradorId, Instant ahora) {
        if (estado != EstadoReporte.EN_REVISION) {
            throw ExcepcionNegocio.transicionInvalida("el reporte", estado, EstadoReporte.RESUELTO);
        }
        this.resolucion = new Resolucion(decision, accionPago, observacion, administradorId, ahora);
        this.estado = decision == DecisionReporte.DESESTIMADO ? EstadoReporte.DESESTIMADO : EstadoReporte.RESUELTO;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getReportanteId() { return reportanteId; }
    public void setReportanteId(String reportanteId) { this.reportanteId = reportanteId; }
    public String getDenunciadoId() { return denunciadoId; }
    public void setDenunciadoId(String denunciadoId) { this.denunciadoId = denunciadoId; }
    public String getServicioId() { return servicioId; }
    public void setServicioId(String servicioId) { this.servicioId = servicioId; }
    public TipoReporte getTipo() { return tipo; }
    public void setTipo(TipoReporte tipo) { this.tipo = tipo; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public List<String> getEvidencias() { return evidencias; }
    public void setEvidencias(List<String> evidencias) { this.evidencias = evidencias; }
    public EstadoReporte getEstado() { return estado; }
    public void setEstado(EstadoReporte estado) { this.estado = estado; }
    public Instant getFechaRadicacion() { return fechaRadicacion; }
    public void setFechaRadicacion(Instant fechaRadicacion) { this.fechaRadicacion = fechaRadicacion; }
    public Resolucion getResolucion() { return resolucion; }
    public void setResolucion(Resolucion resolucion) { this.resolucion = resolucion; }
}
