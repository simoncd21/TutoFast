package com.tutofast.modelo;

/** Motivos con los que un participante puede reportar un mensaje del chat. */
public enum MotivoReporteMensaje {
    CONDUCTA_ABUSIVA(TipoReporte.CONDUCTA_ABUSIVA),
    ACOSO(TipoReporte.ACOSO),
    CONTACTO_EXTERNO(TipoReporte.COBRO_EXTERNO),
    FRAUDE_ACADEMICO(TipoReporte.FRAUDE_ACADEMICO),
    SPAM(TipoReporte.CONDUCTA_ABUSIVA),
    OTRO(TipoReporte.CONDUCTA_ABUSIVA);

    private final TipoReporte tipoReporte;

    MotivoReporteMensaje(TipoReporte tipoReporte) {
        this.tipoReporte = tipoReporte;
    }

    /** Tipo de {@link Reporte} que se radica si el administrador confirma la infracción y sanciona. */
    public TipoReporte tipoReporte() {
        return tipoReporte;
    }
}
