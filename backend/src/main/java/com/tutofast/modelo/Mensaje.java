package com.tutofast.modelo;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.http.HttpStatus;

import com.tutofast.excepcion.ExcepcionNegocio;

/**
 * Mensaje de un hilo {@code (solicitudId, tutorId)}. Inmutable: solo cambian {@code reportado} y
 * {@code motivoReporte}. El índice parcial de mensajes reportados lo crea {@code DatosInicialesSeed}.
 */
@Document(collection = "mensajes")
@CompoundIndex(name = "ix_hilo_marca", def = "{'solicitudId': 1, 'tutorId': 1, 'marcaTemporal': 1}")
public class Mensaje {

    @Id
    private String id;
    private String solicitudId;
    private String tutorId;
    private String emisorId;
    private String contenido;
    private Instant marcaTemporal;
    private boolean reportado;
    private String motivoReporte;
    private Archivo archivo;

    public static Mensaje nuevo(String solicitudId, String tutorId, String emisorId, String contenido,
                                Archivo archivo, Instant ahora) {
        if ((contenido == null || contenido.isBlank()) && archivo == null) {
            throw new ExcepcionNegocio("El mensaje debe tener contenido o un archivo adjunto");
        }
        Mensaje m = new Mensaje();
        m.solicitudId = solicitudId;
        m.tutorId = tutorId;
        m.emisorId = emisorId;
        m.contenido = contenido;
        m.archivo = archivo;
        m.marcaTemporal = ahora;
        return m;
    }

    public void reportar(String motivo) {
        if (reportado) {
            throw new ExcepcionNegocio("MENSAJE_YA_REPORTADO", "El mensaje ya fue reportado", HttpStatus.CONFLICT);
        }
        if (motivo == null || motivo.isBlank()) {
            throw new ExcepcionNegocio("El motivo del reporte es obligatorio");
        }
        this.reportado = true;
        this.motivoReporte = motivo;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSolicitudId() { return solicitudId; }
    public void setSolicitudId(String solicitudId) { this.solicitudId = solicitudId; }
    public String getTutorId() { return tutorId; }
    public void setTutorId(String tutorId) { this.tutorId = tutorId; }
    public String getEmisorId() { return emisorId; }
    public void setEmisorId(String emisorId) { this.emisorId = emisorId; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
    public Instant getMarcaTemporal() { return marcaTemporal; }
    public void setMarcaTemporal(Instant marcaTemporal) { this.marcaTemporal = marcaTemporal; }
    public boolean isReportado() { return reportado; }
    public void setReportado(boolean reportado) { this.reportado = reportado; }
    public String getMotivoReporte() { return motivoReporte; }
    public void setMotivoReporte(String motivoReporte) { this.motivoReporte = motivoReporte; }
    public Archivo getArchivo() { return archivo; }
    public void setArchivo(Archivo archivo) { this.archivo = archivo; }
}
