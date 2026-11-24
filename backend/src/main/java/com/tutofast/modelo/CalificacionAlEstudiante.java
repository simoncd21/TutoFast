package com.tutofast.modelo;

import java.math.BigDecimal;

import org.springframework.data.annotation.TypeAlias;

/** Calificación del tutor al estudiante: tres criterios de 1 a 5. */
@TypeAlias("AL_ESTUDIANTE")
public class CalificacionAlEstudiante extends Calificacion {

    private Integer puntualidad;
    private Integer comunicacion;
    private Integer cumplimiento;

    public CalificacionAlEstudiante() {
        super(TipoCalificacion.AL_ESTUDIANTE);
    }

    @Override
    public BigDecimal calcularPromedio() {
        return promediar(
                criterioValido(puntualidad, "puntualidad"),
                criterioValido(comunicacion, "comunicacion"),
                criterioValido(cumplimiento, "cumplimiento"));
    }

    public Integer getPuntualidad() { return puntualidad; }
    public void setPuntualidad(Integer puntualidad) { this.puntualidad = puntualidad; }
    public Integer getComunicacion() { return comunicacion; }
    public void setComunicacion(Integer comunicacion) { this.comunicacion = comunicacion; }
    public Integer getCumplimiento() { return cumplimiento; }
    public void setCumplimiento(Integer cumplimiento) { this.cumplimiento = cumplimiento; }
}
