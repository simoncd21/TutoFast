package com.tutofast.modelo;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import com.tutofast.excepcion.ExcepcionNegocio;

/**
 * Propuesta de un tutor sobre una solicitud. Una propuesta activa por tutor y solicitud: el índice parcial único
 * {@code uk_propuesta_activa} (filtro {@code activa=true}) lo crea {@code DatosInicialesSeed}.
 */
@Document(collection = "propuestas")
@CompoundIndexes({
        @CompoundIndex(name = "ix_propuesta_solicitud_estado", def = "{'solicitudId': 1, 'estado': 1}"),
        @CompoundIndex(name = "ix_propuesta_tutor", def = "{'tutorId': 1, 'fechaCreacion': -1}")
})
public class Propuesta {

    @Id
    private String id;
    private String solicitudId;
    private String tutorId;
    private TipoPropuesta tipo;
    private BigDecimal precioCOP;
    private Duracion duracion;
    private Modalidad modalidad;
    private String nota;
    /** Precio propuesto por el estudiante cuando contraoferta; {@code null} si no hay contraoferta. */
    private BigDecimal contraofertaCOP;
    private EstadoPropuesta estado = EstadoPropuesta.ENVIADA;
    /** Derivado de {@code estado}; se guarda para poder indexarlo de forma parcial. */
    private boolean activa = true;
    private Instant fechaCreacion;
    private Instant fechaActualizacion;

    public static Propuesta nueva(String solicitudId, String tutorId, TipoPropuesta tipo, BigDecimal precioCOP,
                                  Duracion duracion, Modalidad modalidad, String nota, Instant ahora) {
        if (precioCOP == null || precioCOP.signum() <= 0) {
            throw new ExcepcionNegocio("El precio de la propuesta debe ser mayor que cero");
        }
        Propuesta p = new Propuesta();
        p.solicitudId = solicitudId;
        p.tutorId = tutorId;
        p.tipo = tipo;
        p.precioCOP = precioCOP;
        p.duracion = duracion;
        p.modalidad = modalidad;
        p.nota = nota;
        p.fechaCreacion = ahora;
        p.fechaActualizacion = ahora;
        return p;
    }

    /** El tutor edita su propuesta; vuelve a ENVIADA y anula una contraoferta previa. */
    public void modificar(TipoPropuesta tipo, BigDecimal precioCOP, Duracion duracion, Modalidad modalidad,
                          String nota, Instant ahora) {
        exigirEstado(EstadoPropuesta.ENVIADA, EstadoPropuesta.CONTRAOFERTA_ESTUDIANTE);
        if (precioCOP == null || precioCOP.signum() <= 0) {
            throw new ExcepcionNegocio("El precio de la propuesta debe ser mayor que cero");
        }
        this.tipo = tipo;
        this.precioCOP = precioCOP;
        this.duracion = duracion;
        this.modalidad = modalidad;
        this.nota = nota;
        this.contraofertaCOP = null;
        cambiarEstado(EstadoPropuesta.ENVIADA, ahora);
    }

    /** El estudiante responde con otro precio: ENVIADA → CONTRAOFERTA_ESTUDIANTE. */
    public void contraofertar(BigDecimal precioEstudianteCOP, Instant ahora) {
        exigirEstado(EstadoPropuesta.ENVIADA);
        if (precioEstudianteCOP == null || precioEstudianteCOP.signum() <= 0) {
            throw new ExcepcionNegocio("El precio de la contraoferta debe ser mayor que cero");
        }
        this.contraofertaCOP = precioEstudianteCOP;
        cambiarEstado(EstadoPropuesta.CONTRAOFERTA_ESTUDIANTE, ahora);
    }

    /** El estudiante la selecciona: pasa a ACEPTADA. */
    public void aceptar(Instant ahora) {
        exigirEstado(EstadoPropuesta.ENVIADA, EstadoPropuesta.CONTRAOFERTA_ESTUDIANTE);
        cambiarEstado(EstadoPropuesta.ACEPTADA, ahora);
    }

    /** Al confirmar a otro tutor o cancelar la solicitud. */
    public void descartar(Instant ahora) {
        exigirEstado(EstadoPropuesta.ENVIADA, EstadoPropuesta.CONTRAOFERTA_ESTUDIANTE);
        cambiarEstado(EstadoPropuesta.DESCARTADA, ahora);
    }

    /** El tutor retira su propuesta. */
    public void retirar(Instant ahora) {
        exigirEstado(EstadoPropuesta.ENVIADA, EstadoPropuesta.CONTRAOFERTA_ESTUDIANTE);
        cambiarEstado(EstadoPropuesta.RETIRADA, ahora);
    }

    private void exigirEstado(EstadoPropuesta... permitidos) {
        for (EstadoPropuesta e : permitidos) {
            if (estado == e) {
                return;
            }
        }
        throw ExcepcionNegocio.transicionInvalida("la propuesta", estado, "el estado solicitado");
    }

    private void cambiarEstado(EstadoPropuesta nuevo, Instant ahora) {
        this.estado = nuevo;
        this.activa = nuevo.esActiva();
        this.fechaActualizacion = ahora;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSolicitudId() { return solicitudId; }
    public void setSolicitudId(String solicitudId) { this.solicitudId = solicitudId; }
    public String getTutorId() { return tutorId; }
    public void setTutorId(String tutorId) { this.tutorId = tutorId; }
    public TipoPropuesta getTipo() { return tipo; }
    public void setTipo(TipoPropuesta tipo) { this.tipo = tipo; }
    public BigDecimal getPrecioCOP() { return precioCOP; }
    public void setPrecioCOP(BigDecimal precioCOP) { this.precioCOP = precioCOP; }
    public Duracion getDuracion() { return duracion; }
    public void setDuracion(Duracion duracion) { this.duracion = duracion; }
    public Modalidad getModalidad() { return modalidad; }
    public void setModalidad(Modalidad modalidad) { this.modalidad = modalidad; }
    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }
    public BigDecimal getContraofertaCOP() { return contraofertaCOP; }
    public void setContraofertaCOP(BigDecimal contraofertaCOP) { this.contraofertaCOP = contraofertaCOP; }
    public EstadoPropuesta getEstado() { return estado; }
    public void setEstado(EstadoPropuesta estado) { this.estado = estado; this.activa = estado.esActiva(); }
    public boolean isActiva() { return activa; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Instant fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public Instant getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(Instant fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
}
