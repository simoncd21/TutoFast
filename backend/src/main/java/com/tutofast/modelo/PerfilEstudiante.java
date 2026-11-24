package com.tutofast.modelo;

/** Perfil embebido del estudiante (composición 0..1 de Usuario). */
public class PerfilEstudiante {

    private String programaId;
    private Integer semestre;
    private int calificacionesRecibidas;

    public String getProgramaId() { return programaId; }
    public void setProgramaId(String programaId) { this.programaId = programaId; }
    public Integer getSemestre() { return semestre; }
    public void setSemestre(Integer semestre) { this.semestre = semestre; }
    public int getCalificacionesRecibidas() { return calificacionesRecibidas; }
    public void setCalificacionesRecibidas(int calificacionesRecibidas) { this.calificacionesRecibidas = calificacionesRecibidas; }
}
