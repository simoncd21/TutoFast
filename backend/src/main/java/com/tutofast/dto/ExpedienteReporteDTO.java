package com.tutofast.dto;

import java.util.List;

/**
 * Expediente que revisa el administrador: el reporte, el servicio y su pago (nulos si el reporte no está ligado a un
 * servicio), el historial íntegro del chat del servicio (con los mensajes retirados en su versión original) y las
 * sanciones derivadas con sus apelaciones.
 */
public record ExpedienteReporteDTO(ReporteDTO reporte, ServicioDTO servicio, LiquidacionDTO pago,
                                   List<MensajeDTO> historialChat, List<SancionDTO> sanciones) {
}
