package com.tutofast.modelo;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Registro de auditoría (solo inserción). {@code usuarioId} es {@code null} cuando actúa el sistema. */
@Document(collection = "registros_auditoria")
@CompoundIndexes({
        @CompoundIndex(name = "ix_auditoria_usuario_fecha", def = "{'usuarioId': 1, 'fecha': -1}"),
        @CompoundIndex(name = "ix_auditoria_entidad_fecha", def = "{'entidadAfectada': 1, 'fecha': -1}")
})
public class RegistroAuditoria {

    @Id
    private String id;
    private String usuarioId;
    private String accion;
    private String entidadAfectada;
    @Indexed(name = "ix_auditoria_fecha", direction = IndexDirection.DESCENDING)
    private Instant fecha;
    private Map<String, Object> detalle = new LinkedHashMap<>();

    public RegistroAuditoria() {
    }

    public RegistroAuditoria(String usuarioId, String accion, String entidadAfectada, Instant fecha,
                             Map<String, Object> detalle) {
        this.usuarioId = usuarioId;
        this.accion = accion;
        this.entidadAfectada = entidadAfectada;
        this.fecha = fecha;
        this.detalle = detalle == null ? new LinkedHashMap<>() : new LinkedHashMap<>(detalle);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public String getAccion() { return accion; }
    public void setAccion(String accion) { this.accion = accion; }
    public String getEntidadAfectada() { return entidadAfectada; }
    public void setEntidadAfectada(String entidadAfectada) { this.entidadAfectada = entidadAfectada; }
    public Instant getFecha() { return fecha; }
    public void setFecha(Instant fecha) { this.fecha = fecha; }
    public Map<String, Object> getDetalle() { return detalle; }
    public void setDetalle(Map<String, Object> detalle) { this.detalle = detalle; }
}
