package com.tutofast.modelo;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.mapping.Document;

import com.tutofast.excepcion.ExcepcionNegocio;

/**
 * Porcentaje de comisión histórico. Solo uno está vigente ({@code vigenteHasta == null}); el índice parcial único
 * (sobre {@code vigente=true}) que lo garantiza lo crea {@code DatosInicialesSeed}.
 */
@Document(collection = "parametros_comision")
public class ParametroComision {

    public static final BigDecimal MAXIMO = new BigDecimal("50");

    @Id
    private String id;
    private BigDecimal porcentaje;
    @Indexed(name = "ix_comision_vigente_desde", direction = IndexDirection.DESCENDING)
    private Instant vigenteDesde;
    private Instant vigenteHasta;
    /** Derivado de {@code vigenteHasta == null}; Spring omite los nulos, así que se guarda explícito para el índice parcial. */
    private boolean vigente = true;
    private String modificadoPor;

    public static ParametroComision nuevo(BigDecimal porcentaje, Instant desde, String modificadoPor) {
        if (porcentaje == null || porcentaje.signum() < 0 || porcentaje.compareTo(MAXIMO) > 0) {
            throw new ExcepcionNegocio("El porcentaje de comisión debe estar entre 0 y 50");
        }
        ParametroComision p = new ParametroComision();
        p.porcentaje = porcentaje;
        p.vigenteDesde = desde;
        p.modificadoPor = modificadoPor;
        return p;
    }

    public boolean estaVigente() {
        return vigenteHasta == null;
    }

    /** Cierra este parámetro; los servicios ya confirmados conservan su {@code comisionPct}. */
    public void cerrar(Instant ahora) {
        if (!estaVigente()) {
            throw new ExcepcionNegocio("El parámetro de comisión ya fue cerrado");
        }
        this.vigenteHasta = ahora;
        this.vigente = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public BigDecimal getPorcentaje() { return porcentaje; }
    public void setPorcentaje(BigDecimal porcentaje) { this.porcentaje = porcentaje; }
    public Instant getVigenteDesde() { return vigenteDesde; }
    public void setVigenteDesde(Instant vigenteDesde) { this.vigenteDesde = vigenteDesde; }
    public Instant getVigenteHasta() { return vigenteHasta; }
    public boolean isVigente() { return vigente; }
    public void setVigenteHasta(Instant vigenteHasta) { this.vigenteHasta = vigenteHasta; this.vigente = vigenteHasta == null; }
    public String getModificadoPor() { return modificadoPor; }
    public void setModificadoPor(String modificadoPor) { this.modificadoPor = modificadoPor; }
}
