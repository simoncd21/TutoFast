package com.tutofast.modelo;

import java.util.ArrayList;
import java.util.List;

/** Perfil embebido del tutor (composición 0..1 de Usuario). Separa lo declarado de lo verificado (R02). */
public class PerfilTutor {

    private String presentacion;
    private List<String> areasDeInteres = new ArrayList<>();
    private String formacion;
    private TipoTutor tipoTutor;
    private String experiencia;
    private List<Modalidad> modalidades = new ArrayList<>();
    private String disponibilidad;
    private NivelVerificacion nivelVerificacion = NivelVerificacion.NO_VERIFICADO;
    private int serviciosCompletados;
    private String cuentaBancariaCobro;
    private List<String> asignaturasDominadasIds = new ArrayList<>();

    public String getPresentacion() { return presentacion; }
    public void setPresentacion(String presentacion) { this.presentacion = presentacion; }
    public List<String> getAreasDeInteres() { return areasDeInteres; }
    public void setAreasDeInteres(List<String> areasDeInteres) { this.areasDeInteres = areasDeInteres; }
    public String getFormacion() { return formacion; }
    public void setFormacion(String formacion) { this.formacion = formacion; }
    public TipoTutor getTipoTutor() { return tipoTutor; }
    public void setTipoTutor(TipoTutor tipoTutor) { this.tipoTutor = tipoTutor; }
    public String getExperiencia() { return experiencia; }
    public void setExperiencia(String experiencia) { this.experiencia = experiencia; }
    public List<Modalidad> getModalidades() { return modalidades; }
    public void setModalidades(List<Modalidad> modalidades) { this.modalidades = modalidades; }
    public String getDisponibilidad() { return disponibilidad; }
    public void setDisponibilidad(String disponibilidad) { this.disponibilidad = disponibilidad; }
    public NivelVerificacion getNivelVerificacion() { return nivelVerificacion; }
    public void setNivelVerificacion(NivelVerificacion nivelVerificacion) { this.nivelVerificacion = nivelVerificacion; }
    public int getServiciosCompletados() { return serviciosCompletados; }
    public void setServiciosCompletados(int serviciosCompletados) { this.serviciosCompletados = serviciosCompletados; }
    public String getCuentaBancariaCobro() { return cuentaBancariaCobro; }
    public void setCuentaBancariaCobro(String cuentaBancariaCobro) { this.cuentaBancariaCobro = cuentaBancariaCobro; }
    public List<String> getAsignaturasDominadasIds() { return asignaturasDominadasIds; }
    public void setAsignaturasDominadasIds(List<String> asignaturasDominadasIds) { this.asignaturasDominadasIds = asignaturasDominadasIds; }
}
