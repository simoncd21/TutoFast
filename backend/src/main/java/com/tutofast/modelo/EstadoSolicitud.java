package com.tutofast.modelo;

/** Ciclo de vida de la solicitud (R10). CANCELADA solo es posible antes de FORMALIZADA (R05). */
public enum EstadoSolicitud {
    PUBLICADA, PROPUESTAS_RECIBIDAS, NEGOCIACION_EN_CURSO, TUTOR_SELECCIONADO, FORMALIZADA, CANCELADA;

    /** Estados en los que la solicitud aparece en el panel de exploración (R07). */
    public boolean visibleParaExplorar() {
        return this == PUBLICADA || this == PROPUESTAS_RECIBIDAS || this == NEGOCIACION_EN_CURSO;
    }

    /** Estados en los que todavía admite propuestas de tutores. */
    public boolean admitePropuestas() {
        return visibleParaExplorar();
    }
}
