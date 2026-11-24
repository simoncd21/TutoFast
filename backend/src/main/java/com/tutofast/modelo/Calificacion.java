package com.tutofast.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.http.HttpStatus;

import com.tutofast.excepcion.ExcepcionNegocio;

/**
 * Calificación bilateral (abstracta). Las dos subclases comparten la colección {@code calificaciones}: se distinguen
 * con {@code @TypeAlias} ({@code _class}) y con el campo explícito {@code tipo}, sobre el que se apoya el índice
 * único {@code (servicioId, tipo)} (máximo dos calificaciones por servicio).
 */
@Document(collection = "calificaciones")
@CompoundIndexes({
        @CompoundIndex(name = "uk_calificacion_servicio_tipo", def = "{'servicioId': 1, 'tipo': 1}", unique = true),
        @CompoundIndex(name = "ix_calificacion_destinatario", def = "{'destinatarioId': 1, 'visible': 1, 'fechaEmision': -1}")
})
public abstract class Calificacion {

    public static final int CRITERIO_MINIMO = 1;
    public static final int CRITERIO_MAXIMO = 5;
    /** Plazo para calificar desde la finalización del servicio (HU-09). */
    public static final Duration PLAZO_CALIFICACION = Duration.ofDays(14);

    @Id
    private String id;
    private TipoCalificacion tipo;
    private String servicioId;
    private String autorId;
    private String destinatarioId;
    private String comentario;
    private Instant fechaEmision;
    private Instant fechaLimite;
    private boolean visible;
    /** Reporte de la reseña por parte de su destinatario (moderación del administrador). */
    private boolean reportada;
    private String motivoReporte;
    private String reportadaPorId;
    private Instant fechaReporte;
    /** Una reseña ocultada por un administrador no vuelve a mostrarse ni cuenta para el promedio. */
    private boolean ocultaPorModeracion;
    private Moderacion moderacion;

    protected Calificacion(TipoCalificacion tipo) {
        this.tipo = tipo;
    }

    /** Media de los criterios con dos decimales (HALF_UP). Lanza {@link ExcepcionNegocio} si algún criterio no es válido. */
    public abstract BigDecimal calcularPromedio();

    /** Fecha límite: finalización + 14 días. */
    public static Instant calcularFechaLimite(Instant fechaFinalizacion) {
        return fechaFinalizacion.plus(PLAZO_CALIFICACION);
    }

    public boolean haVencido(Instant ahora) {
        return fechaLimite != null && ahora.isAfter(fechaLimite);
    }

    /** Se revela cuando ambas partes calificaron o venció el plazo (HU-09). */
    public void hacerVisible() {
        if (!ocultaPorModeracion) {
            this.visible = true;
        }
    }

    /** El destinatario reporta una reseña ya publicada (una sola vez). */
    public void reportar(String motivo, String reportanteId, Instant ahora) {
        if (!visible) {
            throw new ExcepcionNegocio("RESENA_NO_PUBLICADA", "Solo se pueden reportar reseñas ya publicadas",
                    HttpStatus.CONFLICT);
        }
        if (reportada) {
            throw new ExcepcionNegocio("RESENA_YA_REPORTADA", "La reseña ya fue reportada", HttpStatus.CONFLICT);
        }
        if (motivo == null || motivo.isBlank()) {
            throw new ExcepcionNegocio("El motivo del reporte es obligatorio");
        }
        this.reportada = true;
        this.motivoReporte = motivo.strip();
        this.reportadaPorId = reportanteId;
        this.fechaReporte = ahora;
    }

    /** Comprueba, sin cambiar nada, que la reseña tenga un reporte pendiente (se resuelve una sola vez). */
    public void exigirModerable() {
        if (!reportada) {
            throw new ExcepcionNegocio("RESENA_NO_REPORTADA", "La reseña no tiene un reporte pendiente", HttpStatus.CONFLICT);
        }
        if (moderacion != null) {
            throw new ExcepcionNegocio("RESENA_YA_MODERADA", "El reporte de esta reseña ya fue resuelto", HttpStatus.CONFLICT);
        }
    }

    /** Resolución del administrador: confirmar la infracción oculta la reseña de forma definitiva. */
    public void moderar(Moderacion resultado) {
        exigirModerable();
        this.moderacion = resultado;
        if (resultado.confirmaInfraccion()) {
            this.ocultaPorModeracion = true;
            this.visible = false;
        }
    }

    protected static BigDecimal promediar(int... criterios) {
        int suma = 0;
        for (int c : criterios) {
            suma += c;
        }
        return BigDecimal.valueOf(suma).divide(BigDecimal.valueOf(criterios.length), 2, RoundingMode.HALF_UP);
    }

    /** Verifica que el criterio exista y esté entre 1 y 5; devuelve su valor. */
    protected static int criterioValido(Integer valor, String nombre) {
        if (valor == null || valor < CRITERIO_MINIMO || valor > CRITERIO_MAXIMO) {
            throw new ExcepcionNegocio("El criterio '" + nombre + "' debe estar entre 1 y 5");
        }
        return valor;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public TipoCalificacion getTipo() { return tipo; }
    public String getServicioId() { return servicioId; }
    public void setServicioId(String servicioId) { this.servicioId = servicioId; }
    public String getAutorId() { return autorId; }
    public void setAutorId(String autorId) { this.autorId = autorId; }
    public String getDestinatarioId() { return destinatarioId; }
    public void setDestinatarioId(String destinatarioId) { this.destinatarioId = destinatarioId; }
    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }
    public Instant getFechaEmision() { return fechaEmision; }
    public void setFechaEmision(Instant fechaEmision) { this.fechaEmision = fechaEmision; }
    public Instant getFechaLimite() { return fechaLimite; }
    public void setFechaLimite(Instant fechaLimite) { this.fechaLimite = fechaLimite; }
    public boolean isReportada() { return reportada; }
    public void setReportada(boolean reportada) { this.reportada = reportada; }
    public String getMotivoReporte() { return motivoReporte; }
    public void setMotivoReporte(String motivoReporte) { this.motivoReporte = motivoReporte; }
    public String getReportadaPorId() { return reportadaPorId; }
    public void setReportadaPorId(String reportadaPorId) { this.reportadaPorId = reportadaPorId; }
    public Instant getFechaReporte() { return fechaReporte; }
    public void setFechaReporte(Instant fechaReporte) { this.fechaReporte = fechaReporte; }
    public boolean isOcultaPorModeracion() { return ocultaPorModeracion; }
    public void setOcultaPorModeracion(boolean ocultaPorModeracion) { this.ocultaPorModeracion = ocultaPorModeracion; }
    public Moderacion getModeracion() { return moderacion; }
    public void setModeracion(Moderacion moderacion) { this.moderacion = moderacion; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
}
