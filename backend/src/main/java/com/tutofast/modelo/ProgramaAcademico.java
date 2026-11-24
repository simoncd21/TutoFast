package com.tutofast.modelo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** Catálogo académico: programa de una facultad. */
@Document(collection = "programas_academicos")
@CompoundIndex(name = "uk_programa_nombre", def = "{'facultadId': 1, 'nombre': 1}", unique = true)
public class ProgramaAcademico {

    @Id
    private String id;
    private String facultadId;
    private String nombre;
    private boolean activo = true;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFacultadId() { return facultadId; }
    public void setFacultadId(String facultadId) { this.facultadId = facultadId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
