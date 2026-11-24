package com.tutofast.modelo;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Usuario de la plataforma (colección {@code usuarios}). El correo se guarda en minúsculas. */
@Document(collection = "usuarios")
@CompoundIndexes({
        @CompoundIndex(name = "ix_tutor_busqueda", def = "{'perfilTutor.nivelVerificacion': 1, 'perfilTutor.promedioCalificacion': -1}"),
        @CompoundIndex(name = "ix_tutor_asignaturas", def = "{'perfilTutor.asignaturasDominadasIds': 1}"),
        @CompoundIndex(name = "ix_soportes_estado", def = "{'perfilTutor.soportes.estado': 1}"),
        @CompoundIndex(name = "ix_est_programa", def = "{'perfilEstudiante.programaId': 1}")
})
public class Usuario {

    @Id
    private String id;
    @Indexed(unique = true, name = "uk_correo")
    private String correo;
    private String nombre;
    @Indexed(unique = true, name = "uk_documento")
    private String documento;
    private String claveHash;
    @Indexed(name = "ix_roles")
    private Set<Rol> roles = new LinkedHashSet<>();
    private EstadoCuenta estadoCuenta = EstadoCuenta.ACTIVA;
    private Instant fechaAutorizacionDatos;
    private Instant fechaRegistro;
    private PerfilEstudiante perfilEstudiante;
    private PerfilTutor perfilTutor;

    public boolean tieneRol(Rol rol) {
        return roles.contains(rol);
    }

    public boolean estaActiva() {
        return estadoCuenta == EstadoCuenta.ACTIVA;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDocumento() { return documento; }
    public void setDocumento(String documento) { this.documento = documento; }
    public String getClaveHash() { return claveHash; }
    public void setClaveHash(String claveHash) { this.claveHash = claveHash; }
    public Set<Rol> getRoles() { return roles; }
    public void setRoles(Set<Rol> roles) { this.roles = roles; }
    public EstadoCuenta getEstadoCuenta() { return estadoCuenta; }
    public void setEstadoCuenta(EstadoCuenta estadoCuenta) { this.estadoCuenta = estadoCuenta; }
    public Instant getFechaAutorizacionDatos() { return fechaAutorizacionDatos; }
    public void setFechaAutorizacionDatos(Instant fechaAutorizacionDatos) { this.fechaAutorizacionDatos = fechaAutorizacionDatos; }
    public Instant getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(Instant fechaRegistro) { this.fechaRegistro = fechaRegistro; }
    public PerfilEstudiante getPerfilEstudiante() { return perfilEstudiante; }
    public void setPerfilEstudiante(PerfilEstudiante perfilEstudiante) { this.perfilEstudiante = perfilEstudiante; }
    public PerfilTutor getPerfilTutor() { return perfilTutor; }
    public void setPerfilTutor(PerfilTutor perfilTutor) { this.perfilTutor = perfilTutor; }
}
