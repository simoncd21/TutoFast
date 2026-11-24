package com.tutofast.modelo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/** Catálogo académico: asignatura de un programa. El código es único dentro del programa (HU-02). */
@Document(collection = "asignaturas")
@CompoundIndexes({
        @CompoundIndex(name = "uk_asignatura_codigo", def = "{'programaId': 1, 'codigo': 1}", unique = true),
        @CompoundIndex(name = "ix_asignatura_activa", def = "{'programaId': 1, 'activa': 1}")
})
public class Asignatura {

    @Id
    private String id;
    private String programaId;
    private String codigo;
    private String nombre;
    private boolean activa = true;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProgramaId() { return programaId; }
    public void setProgramaId(String programaId) { this.programaId = programaId; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
}
