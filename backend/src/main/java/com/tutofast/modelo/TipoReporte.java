package com.tutofast.modelo;

public enum TipoReporte {
    INASISTENCIA(CategoriaReporte.INCUMPLIMIENTO_SERVICIO),
    COBRO_EXTERNO(CategoriaReporte.INCUMPLIMIENTO_SERVICIO),
    FRAUDE_ACADEMICO(CategoriaReporte.CONDUCTA_ABUSIVA),
    CONDUCTA_ABUSIVA(CategoriaReporte.CONDUCTA_ABUSIVA),
    ACOSO(CategoriaReporte.CONDUCTA_ABUSIVA),
    FALSEDAD_CREDENCIALES(CategoriaReporte.CONDUCTA_ABUSIVA),
    DESACUERDO_ACADEMICO(CategoriaReporte.DESACUERDO_ACADEMICO);

    private final CategoriaReporte categoria;

    TipoReporte(CategoriaReporte categoria) {
        this.categoria = categoria;
    }

    /** Categoría R14 derivada del tipo. */
    public CategoriaReporte getCategoria() {
        return categoria;
    }
}
