package com.tutofast.dto;

import com.tutofast.modelo.MotivoReporteMensaje;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/mensajes/{id}/reportar}. */
public record ReporteMensajeEntradaDTO(@NotNull(message = "El motivo del reporte es obligatorio") MotivoReporteMensaje motivo,
                                       @Size(max = 500, message = "El detalle admite hasta 500 caracteres") String detalle) {
}
