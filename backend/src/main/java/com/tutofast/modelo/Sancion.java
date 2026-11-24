package com.tutofast.modelo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.http.HttpStatus;

import com.tutofast.excepcion.ExcepcionNegocio;

/** Sanción derivada de un reporte (con sus apelaciones embebidas). */
@Document(collection = "sanciones")
@CompoundIndexes({
        @CompoundIndex(name = "ix_sancion_usuario_estado", def = "{'usuarioId': 1, 'estado': 1}"),
        @CompoundIndex(name = "ix_sancion_reporte", def = "{'reporteId': 1}"),
        @CompoundIndex(name = "ix_sancion_apelacion_resultado", def = "{'apelaciones.resultado': 1}")
})
public class Sancion {

    @Id
    private String id;
    private String reporteId;
    private String usuarioId;
    private TipoSancion tipo;
    private Periodo vigencia;
    private EstadoSancion estado = EstadoSancion.VIGENTE;
    private List<Apelacion> apelaciones = new ArrayList<>();

    public static Sancion aplicar(String reporteId, String usuarioId, TipoSancion tipo, Periodo vigencia) {
        if (tipo == TipoSancion.SUSPENSION_TEMPORAL && (vigencia == null || vigencia.getFin() == null)) {
            throw new ExcepcionNegocio("La suspensión temporal requiere una fecha de fin");
        }
        Sancion s = new Sancion();
        s.reporteId = reporteId;
        s.usuarioId = usuarioId;
        s.tipo = tipo;
        s.vigencia = vigencia;
        return s;
    }

    public boolean tieneApelacionPendiente() {
        return apelaciones.stream().anyMatch(Apelacion::estaPendiente);
    }

    /** VIGENTE → EN_APELACION. Una sola apelación pendiente a la vez. */
    public Apelacion apelar(String descargo, List<String> evidencias, Instant ahora) {
        if (tieneApelacionPendiente() || estado != EstadoSancion.VIGENTE) {
            throw new ExcepcionNegocio("APELACION_NO_PERMITIDA",
                    "Ya hay una apelación pendiente o la sanción no está vigente", HttpStatus.CONFLICT);
        }
        if (descargo == null || descargo.isBlank()) {
            throw new ExcepcionNegocio("El descargo es obligatorio");
        }
        Apelacion apelacion = new Apelacion(descargo, evidencias, ahora);
        apelaciones.add(apelacion);
        estado = EstadoSancion.EN_APELACION;
        return apelacion;
    }

    /** EN_APELACION → RATIFICADA | REVOCADA según el resultado de la apelación. */
    public void resolverApelacion(String apelacionId, ResultadoApelacion resultado, Instant ahora) {
        if (estado != EstadoSancion.EN_APELACION) {
            throw ExcepcionNegocio.transicionInvalida("la sanción", estado, "resuelta");
        }
        Apelacion apelacion = apelaciones.stream().filter(a -> a.getId().equals(apelacionId)).findFirst()
                .orElseThrow(() -> new ExcepcionNegocio("APELACION_INEXISTENTE", "La apelación no existe", HttpStatus.NOT_FOUND));
        apelacion.resolver(resultado, ahora);
        estado = resultado == ResultadoApelacion.REVOCADA ? EstadoSancion.REVOCADA : EstadoSancion.RATIFICADA;
    }

    /** ¿Mantiene suspendida la cuenta en este instante? */
    public boolean suspendeCuenta(Instant ahora) {
        if (tipo == TipoSancion.ADVERTENCIA) {
            return false;
        }
        boolean activa = estado == EstadoSancion.VIGENTE || estado == EstadoSancion.EN_APELACION
                || estado == EstadoSancion.RATIFICADA;
        return activa && (vigencia == null || vigencia.contiene(ahora));
    }

    /** VIGENTE | RATIFICADA → CUMPLIDA al vencer la vigencia. */
    public void cumplir(Instant ahora) {
        if ((estado != EstadoSancion.VIGENTE && estado != EstadoSancion.RATIFICADA)
                || vigencia == null || !vigencia.haVencido(ahora)) {
            throw ExcepcionNegocio.transicionInvalida("la sanción", estado, EstadoSancion.CUMPLIDA);
        }
        estado = EstadoSancion.CUMPLIDA;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getReporteId() { return reporteId; }
    public void setReporteId(String reporteId) { this.reporteId = reporteId; }
    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public TipoSancion getTipo() { return tipo; }
    public void setTipo(TipoSancion tipo) { this.tipo = tipo; }
    public Periodo getVigencia() { return vigencia; }
    public void setVigencia(Periodo vigencia) { this.vigencia = vigencia; }
    public EstadoSancion getEstado() { return estado; }
    public void setEstado(EstadoSancion estado) { this.estado = estado; }
    public List<Apelacion> getApelaciones() { return apelaciones; }
    public void setApelaciones(List<Apelacion> apelaciones) { this.apelaciones = apelaciones; }
}
