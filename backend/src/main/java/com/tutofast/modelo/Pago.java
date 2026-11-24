package com.tutofast.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.tutofast.excepcion.ExcepcionNegocio;

/** Pago simulado de un servicio. No almacena datos de tarjeta: solo la referencia de la pasarela. */
@Document(collection = "pagos")
public class Pago {

    /** Resultado de la liquidación (R12): montos en pesos enteros. */
    public record Liquidacion(BigDecimal comisionCOP, BigDecimal montoNetoCOP) {
    }

    /** Entrada del historial de estados. */
    public static class Movimiento {
        private EstadoPago estado;
        private Instant fecha;

        public Movimiento() {
        }

        public Movimiento(EstadoPago estado, Instant fecha) {
            this.estado = estado;
            this.fecha = fecha;
        }

        public EstadoPago getEstado() { return estado; }
        public void setEstado(EstadoPago estado) { this.estado = estado; }
        public Instant getFecha() { return fecha; }
        public void setFecha(Instant fecha) { this.fecha = fecha; }
    }

    @Id
    private String id;
    @Indexed(unique = true, name = "uk_pago_servicio")
    private String servicioId;
    private BigDecimal montoRetenidoCOP;
    private BigDecimal comisionCOP;
    private BigDecimal montoNetoCOP;
    @Indexed(name = "ix_pago_estado")
    private EstadoPago estado;
    @Indexed(unique = true, sparse = true, name = "uk_pago_referencia")
    private String referenciaPasarela;
    private Instant fechaRetencion;
    private Instant fechaLiquidacion;
    private List<Movimiento> historial = new ArrayList<>();

    /** Retención inicial de fondos tras la aprobación de la pasarela. */
    public static Pago retener(String servicioId, BigDecimal monto, String referenciaPasarela, Instant ahora) {
        if (monto == null || monto.signum() <= 0) {
            throw new ExcepcionNegocio("El monto a retener debe ser mayor que cero");
        }
        Pago p = new Pago();
        p.servicioId = servicioId;
        p.montoRetenidoCOP = monto;
        p.referenciaPasarela = referenciaPasarela;
        p.fechaRetencion = ahora;
        p.registrar(EstadoPago.RETENIDO, ahora);
        return p;
    }

    /**
     * Liquidación (R12): {@code comision = round(monto × pct / 100)} con HALF_UP a pesos enteros y
     * {@code neto = monto − comision}. Deja los montos calculados en el pago y los devuelve.
     */
    public Liquidacion calcularLiquidacion(BigDecimal comisionPct) {
        if (comisionPct == null || comisionPct.signum() < 0 || comisionPct.compareTo(ParametroComision.MAXIMO) > 0) {
            throw new ExcepcionNegocio("El porcentaje de comisión debe estar entre 0 y 50");
        }
        BigDecimal comision = montoRetenidoCOP.multiply(comisionPct)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        BigDecimal neto = montoRetenidoCOP.setScale(0, RoundingMode.HALF_UP).subtract(comision);
        this.comisionCOP = comision;
        this.montoNetoCOP = neto;
        return new Liquidacion(comision, neto);
    }

    /** RETENIDO | CONGELADO → LIBERADO, liquidando con la comisión congelada del servicio. */
    public Liquidacion liberar(BigDecimal comisionPct, Instant ahora) {
        if (estado != EstadoPago.RETENIDO && estado != EstadoPago.CONGELADO) {
            throw ExcepcionNegocio.transicionInvalida("el pago", estado, EstadoPago.LIBERADO);
        }
        Liquidacion liquidacion = calcularLiquidacion(comisionPct);
        fechaLiquidacion = ahora;
        registrar(EstadoPago.LIBERADO, ahora);
        return liquidacion;
    }

    /** RETENIDO → CONGELADO (reporte radicado). */
    public void congelar(Instant ahora) {
        if (estado != EstadoPago.RETENIDO) {
            throw ExcepcionNegocio.transicionInvalida("el pago", estado, EstadoPago.CONGELADO);
        }
        registrar(EstadoPago.CONGELADO, ahora);
    }

    /** CONGELADO → RETENIDO: el reporte se desestimó o se compensa un fallo; los fondos vuelven a la custodia normal. */
    public void descongelar(Instant ahora) {
        if (estado != EstadoPago.CONGELADO) {
            throw ExcepcionNegocio.transicionInvalida("el pago", estado, EstadoPago.RETENIDO);
        }
        registrar(EstadoPago.RETENIDO, ahora);
    }

    /** CONGELADO → REEMBOLSADO: el estudiante recupera el monto completo, sin comisión. */
    public void reembolsar(Instant ahora) {
        if (estado != EstadoPago.CONGELADO) {
            throw ExcepcionNegocio.transicionInvalida("el pago", estado, EstadoPago.REEMBOLSADO);
        }
        comisionCOP = BigDecimal.ZERO;
        montoNetoCOP = BigDecimal.ZERO;
        fechaLiquidacion = ahora;
        registrar(EstadoPago.REEMBOLSADO, ahora);
    }

    private void registrar(EstadoPago nuevo, Instant ahora) {
        this.estado = nuevo;
        this.historial.add(new Movimiento(nuevo, ahora));
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getServicioId() { return servicioId; }
    public void setServicioId(String servicioId) { this.servicioId = servicioId; }
    public BigDecimal getMontoRetenidoCOP() { return montoRetenidoCOP; }
    public void setMontoRetenidoCOP(BigDecimal montoRetenidoCOP) { this.montoRetenidoCOP = montoRetenidoCOP; }
    public BigDecimal getComisionCOP() { return comisionCOP; }
    public void setComisionCOP(BigDecimal comisionCOP) { this.comisionCOP = comisionCOP; }
    public BigDecimal getMontoNetoCOP() { return montoNetoCOP; }
    public void setMontoNetoCOP(BigDecimal montoNetoCOP) { this.montoNetoCOP = montoNetoCOP; }
    public EstadoPago getEstado() { return estado; }
    public void setEstado(EstadoPago estado) { this.estado = estado; }
    public String getReferenciaPasarela() { return referenciaPasarela; }
    public void setReferenciaPasarela(String referenciaPasarela) { this.referenciaPasarela = referenciaPasarela; }
    public Instant getFechaRetencion() { return fechaRetencion; }
    public void setFechaRetencion(Instant fechaRetencion) { this.fechaRetencion = fechaRetencion; }
    public Instant getFechaLiquidacion() { return fechaLiquidacion; }
    public void setFechaLiquidacion(Instant fechaLiquidacion) { this.fechaLiquidacion = fechaLiquidacion; }
    public List<Movimiento> getHistorial() { return historial; }
    public void setHistorial(List<Movimiento> historial) { this.historial = historial; }
}
