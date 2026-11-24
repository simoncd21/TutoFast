package com.tutofast.modelo;

/** Decisión del administrador sobre un mensaje o una reseña reportados. */
public enum DecisionModeracion {
    /** El reporte no procede: el contenido se conserva tal como estaba. */
    DESESTIMAR,
    /** Se confirma la infracción: el mensaje se retira de la vista de los participantes o la reseña se oculta. */
    CONFIRMAR_INFRACCION
}
