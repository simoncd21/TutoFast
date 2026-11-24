package com.tutofast.modelo;

import java.math.BigDecimal;

/** Perfil embebido del estudiante (composición 0..1 de Usuario). */
public class PerfilEstudiante {

    private String programaId;
    private Integer semestre;
    private int calificacionesRecibidas;
    /** Reputación del estudiante (0–5), promedio de las calificaciones recibidas; {@code null} si aún no tiene. */
    private BigDecimal indicadorConfiabilidad;

    public BigDecimal getIndicadorConfiabilidad() { return indicadorConfiabilidad; }
    public void setIndicadorConfiabilidad(BigDecimal indicadorConfiabilidad) { this.indicadorConfiabilidad = indicadorConfiabilidad; }

    public String getProgramaId() { return programaId; }
    public void setProgramaId(String programaId) { this.programaId = programaId; }
    public Integer getSemestre() { return semestre; }
    public void setSemestre(Integer semestre) { this.semestre = semestre; }
    public int getCalificacionesRecibidas() { return calificacionesRecibidas; }
    public void setCalificacionesRecibidas(int calificacionesRecibidas) { this.calificacionesRecibidas = calificacionesRecibidas; }
}
