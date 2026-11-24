package com.tutofast.modelo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Catálogo académico: universidad. El borrado es lógico ({@code activa=false}). */
@Document(collection = "universidades")
public class Universidad {

    @Id
    private String id;
    @Indexed(unique = true, name = "uk_universidad_nombre")
    private String nombre;
    private boolean activa = true;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
}
