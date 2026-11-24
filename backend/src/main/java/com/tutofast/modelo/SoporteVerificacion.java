package com.tutofast.modelo;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.tutofast.excepcion.ExcepcionNegocio;

/** Soporte documental del tutor (embebido en {@code perfilTutor.soportes}). Solo se revisa una vez. */
public class SoporteVerificacion {

    private String id = UUID.randomUUID().toString();
    private TipoSoporte tipo;
    private String rutaArchivo;
    private EstadoSoporte estado = EstadoSoporte.EN_REVISION;
    private Instant fechaEnvio;
    private Instant fechaRevision;
    private String observacion;
    private String revisadoPor;

    public SoporteVerificacion() {
    }

    public SoporteVerificacion(TipoSoporte tipo, String rutaArchivo, Instant fechaEnvio) {
        this.tipo = tipo;
        this.rutaArchivo = rutaArchivo;
        this.fechaEnvio = fechaEnvio;
    }

    public void aprobar(String administradorId, String observacion, Instant ahora) {
        revisar(EstadoSoporte.APROBADO, administradorId, observacion, ahora);
    }

    public void rechazar(String administradorId, String observacion, Instant ahora) {
        revisar(EstadoSoporte.RECHAZADO, administradorId, observacion, ahora);
    }

    private void revisar(EstadoSoporte nuevo, String administradorId, String observacion, Instant ahora) {
        if (estado != EstadoSoporte.EN_REVISION) {
            throw new ExcepcionNegocio("SOPORTE_YA_REVISADO", "El soporte ya fue revisado", HttpStatus.CONFLICT);
        }
        this.estado = nuevo;
        this.revisadoPor = administradorId;
        this.observacion = observacion;
        this.fechaRevision = ahora;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public TipoSoporte getTipo() { return tipo; }
    public void setTipo(TipoSoporte tipo) { this.tipo = tipo; }
    public String getRutaArchivo() { return rutaArchivo; }
    public void setRutaArchivo(String rutaArchivo) { this.rutaArchivo = rutaArchivo; }
    public EstadoSoporte getEstado() { return estado; }
    public void setEstado(EstadoSoporte estado) { this.estado = estado; }
    public Instant getFechaEnvio() { return fechaEnvio; }
    public void setFechaEnvio(Instant fechaEnvio) { this.fechaEnvio = fechaEnvio; }
    public Instant getFechaRevision() { return fechaRevision; }
    public void setFechaRevision(Instant fechaRevision) { this.fechaRevision = fechaRevision; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
    public String getRevisadoPor() { return revisadoPor; }
    public void setRevisadoPor(String revisadoPor) { this.revisadoPor = revisadoPor; }
}
