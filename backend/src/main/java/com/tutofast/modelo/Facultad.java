package com.tutofast.modelo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** Catálogo académico: facultad de una universidad. */
@Document(collection = "facultades")
@CompoundIndex(name = "uk_facultad_nombre", def = "{'universidadId': 1, 'nombre': 1}", unique = true)
public class Facultad {

    @Id
    private String id;
    private String universidadId;
    private String nombre;
    private boolean activa = true;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUniversidadId() { return universidadId; }
    public void setUniversidadId(String universidadId) { this.universidadId = universidadId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
}
