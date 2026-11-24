package com.tutofast.modelo;

/** {@code activa = ENVIADA | CONTRAOFERTA_ESTUDIANTE | ACEPTADA}. */
public enum EstadoPropuesta {
    ENVIADA, CONTRAOFERTA_ESTUDIANTE, ACEPTADA, DESCARTADA, RETIRADA;

    public boolean esActiva() {
        return this == ENVIADA || this == CONTRAOFERTA_ESTUDIANTE || this == ACEPTADA;
    }
}
