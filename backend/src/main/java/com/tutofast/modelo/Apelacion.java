package com.tutofast.modelo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.tutofast.excepcion.ExcepcionNegocio;

/** Apelación de una sanción (embebida en {@code sanciones.apelaciones}). */
public class Apelacion {

    private String id = UUID.randomUUID().toString();
    private String descargo;
    private List<String> evidencias = new ArrayList<>();
    private ResultadoApelacion resultado = ResultadoApelacion.PENDIENTE;
    private Instant fechaPresentacion;
    private Instant fechaResolucion;

    public Apelacion() {
    }

    public Apelacion(String descargo, List<String> evidencias, Instant fechaPresentacion) {
        this.descargo = descargo;
        this.evidencias = evidencias == null ? new ArrayList<>() : new ArrayList<>(evidencias);
        this.fechaPresentacion = fechaPresentacion;
    }

    public boolean estaPendiente() {
        return resultado == ResultadoApelacion.PENDIENTE;
    }

    /** PENDIENTE → RATIFICADA | REVOCADA. */
    public void resolver(ResultadoApelacion nuevo, Instant ahora) {
        if (!estaPendiente()) {
            throw new ExcepcionNegocio("APELACION_YA_RESUELTA", "La apelación ya fue resuelta", HttpStatus.CONFLICT);
        }
        if (nuevo == ResultadoApelacion.PENDIENTE) {
            throw new ExcepcionNegocio("La resolución debe ser RATIFICADA o REVOCADA");
        }
        this.resultado = nuevo;
        this.fechaResolucion = ahora;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDescargo() { return descargo; }
    public void setDescargo(String descargo) { this.descargo = descargo; }
    public List<String> getEvidencias() { return evidencias; }
    public void setEvidencias(List<String> evidencias) { this.evidencias = evidencias; }
    public ResultadoApelacion getResultado() { return resultado; }
    public void setResultado(ResultadoApelacion resultado) { this.resultado = resultado; }
    public Instant getFechaPresentacion() { return fechaPresentacion; }
    public void setFechaPresentacion(Instant fechaPresentacion) { this.fechaPresentacion = fechaPresentacion; }
    public Instant getFechaResolucion() { return fechaResolucion; }
    public void setFechaResolucion(Instant fechaResolucion) { this.fechaResolucion = fechaResolucion; }
}
