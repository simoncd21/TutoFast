package com.tutofast.modelo;

import java.math.BigDecimal;

import org.springframework.data.annotation.TypeAlias;

/** Calificación del estudiante al tutor: seis criterios de 1 a 5. */
@TypeAlias("AL_TUTOR")
public class CalificacionAlTutor extends Calificacion {

    private Integer claridadPedagogica;
    private Integer dominioTematico;
    private Integer puntualidad;
    private Integer cumplimiento;
    private Integer trato;
    private Integer relacionCalidadPrecio;

    public CalificacionAlTutor() {
        super(TipoCalificacion.AL_TUTOR);
    }

    @Override
    public BigDecimal calcularPromedio() {
        return promediar(
                criterioValido(claridadPedagogica, "claridadPedagogica"),
                criterioValido(dominioTematico, "dominioTematico"),
                criterioValido(puntualidad, "puntualidad"),
                criterioValido(cumplimiento, "cumplimiento"),
                criterioValido(trato, "trato"),
                criterioValido(relacionCalidadPrecio, "relacionCalidadPrecio"));
    }

    public Integer getClaridadPedagogica() { return claridadPedagogica; }
    public void setClaridadPedagogica(Integer claridadPedagogica) { this.claridadPedagogica = claridadPedagogica; }
    public Integer getDominioTematico() { return dominioTematico; }
    public void setDominioTematico(Integer dominioTematico) { this.dominioTematico = dominioTematico; }
    public Integer getPuntualidad() { return puntualidad; }
    public void setPuntualidad(Integer puntualidad) { this.puntualidad = puntualidad; }
    public Integer getCumplimiento() { return cumplimiento; }
    public void setCumplimiento(Integer cumplimiento) { this.cumplimiento = cumplimiento; }
    public Integer getTrato() { return trato; }
    public void setTrato(Integer trato) { this.trato = trato; }
    public Integer getRelacionCalidadPrecio() { return relacionCalidadPrecio; }
    public void setRelacionCalidadPrecio(Integer relacionCalidadPrecio) { this.relacionCalidadPrecio = relacionCalidadPrecio; }
}
