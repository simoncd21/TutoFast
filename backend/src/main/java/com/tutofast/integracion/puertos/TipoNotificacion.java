package com.tutofast.integracion.puertos;

/** Tipos de notificación que la plataforma puede emitir (docs/arquitectura/diseno-api.md, sección 15). */
public enum TipoNotificacion {
    NUEVA_PROPUESTA,
    CONTRAOFERTA,
    PROPUESTA_ACEPTADA,
    PROPUESTA_DESCARTADA,
    SERVICIO_CONFIRMADO,
    NUEVO_MENSAJE,
    RECORDATORIO_SESION,
    SERVICIO_FINALIZADO,
    PAGO_LIBERADO,
    CALIFICACION_PENDIENTE,
    RESENA_DISPONIBLE,
    REPORTE_RECIBIDO,
    REPORTE_RESUELTO,
    SANCION_APLICADA,
    APELACION_RESUELTA,
    SOPORTE_APROBADO,
    SOPORTE_RECHAZADO
}
