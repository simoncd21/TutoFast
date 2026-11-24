package com.tutofast.mapper;

import com.tutofast.dto.LiquidacionDTO;
import com.tutofast.dto.PagoResumenDTO;
import com.tutofast.modelo.Pago;
import com.tutofast.modelo.ServicioTutoria;

/** Conversión de Pago (más la comisión congelada del servicio) a LiquidacionDTO. */
public final class PagoMapper {

    private PagoMapper() {
    }

    public static LiquidacionDTO aLiquidacionDTO(Pago p, ServicioTutoria servicio) {
        return new LiquidacionDTO(p.getServicioId(), servicio.getPrecioFinalCOP(), servicio.getComisionPct(),
                p.getComisionCOP(), p.getMontoNetoCOP(), p.getEstado(), p.getReferenciaPasarela(),
                p.getFechaRetencion(), p.getFechaLiquidacion());
    }

    /** Resumen para incrustar en el servicio; {@code null} si todavía no hay pago. */
    public static PagoResumenDTO aResumenDTO(Pago p) {
        return p == null ? null : new PagoResumenDTO(p.getEstado(), p.getMontoRetenidoCOP(), p.getComisionCOP(),
                p.getMontoNetoCOP(), p.getReferenciaPasarela());
    }
}
