package com.tutofast.modelo;

/** Duración en minutos. La solicitud solo admite 30, 60 y 120 (R04); las propuestas admiten las cinco. */
public enum Duracion {
    MIN_30(30), MIN_45(45), MIN_60(60), MIN_90(90), MIN_120(120);

    private final int minutos;

    Duracion(int minutos) {
        this.minutos = minutos;
    }

    public int getMinutos() {
        return minutos;
    }

    public boolean permitidaEnSolicitud() {
        return this == MIN_30 || this == MIN_60 || this == MIN_120;
    }
}
