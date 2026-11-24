package com.tutofast.integracion.adaptadores;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Notificación guardada en la colección {@code notificaciones} (infraestructura de {@code INotificador}, no es una
 * clase de dominio). El índice TTL de 180 días sobre {@code fecha} lo crea {@code DatosInicialesSeed}.
 */
@Document(collection = "notificaciones")
@CompoundIndex(name = "ix_notificacion_usuario", def = "{'usuarioId': 1, 'leida': 1, 'fecha': -1}")
public class DocumentoNotificacion {

    @Id
    private String id;
    private String usuarioId;
    private String tipo;
    private String titulo;
    private String mensaje;
    private String enlace;
    private boolean leida;
    private Instant fecha;

    public DocumentoNotificacion() {
    }

    public DocumentoNotificacion(String usuarioId, String tipo, String titulo, String mensaje, String enlace, Instant fecha) {
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.titulo = titulo;
        this.mensaje = mensaje;
        this.enlace = enlace;
        this.fecha = fecha;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    public String getEnlace() { return enlace; }
    public void setEnlace(String enlace) { this.enlace = enlace; }
    public boolean isLeida() { return leida; }
    public void setLeida(boolean leida) { this.leida = leida; }
    public Instant getFecha() { return fecha; }
    public void setFecha(Instant fecha) { this.fecha = fecha; }
}
