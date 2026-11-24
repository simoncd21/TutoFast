package com.tutofast.modelo;

import java.time.Instant;

/** Resultado de la revisión de un administrador sobre un mensaje o una reseña reportados (embebido). */
public class Moderacion {

    private DecisionModeracion decision;
    private String observacion;
    private String moderadoPor;
    private Instant fecha;
    private String sancionId;

    public Moderacion() {
    }

    public Moderacion(DecisionModeracion decision, String observacion, String moderadoPor, Instant fecha) {
        this.decision = decision;
        this.observacion = observacion;
        this.moderadoPor = moderadoPor;
        this.fecha = fecha;
    }

    public boolean confirmaInfraccion() {
        return decision == DecisionModeracion.CONFIRMAR_INFRACCION;
    }

    public DecisionModeracion getDecision() { return decision; }
    public void setDecision(DecisionModeracion decision) { this.decision = decision; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
    public String getModeradoPor() { return moderadoPor; }
    public void setModeradoPor(String moderadoPor) { this.moderadoPor = moderadoPor; }
    public Instant getFecha() { return fecha; }
    public void setFecha(Instant fecha) { this.fecha = fecha; }
    public String getSancionId() { return sancionId; }
    public void setSancionId(String sancionId) { this.sancionId = sancionId; }
}
