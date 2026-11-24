package com.tutofast.modelo;

import java.time.Instant;

import com.tutofast.excepcion.ExcepcionNegocio;

/** Vigencia de una sanción: intervalo [inicio, fin). {@code fin == null} significa sin vencimiento (bloqueo permanente). */
public class Periodo {

    private Instant inicio;
    private Instant fin;

    public Periodo() {
    }

    public Periodo(Instant inicio, Instant fin) {
        if (inicio == null) {
            throw new ExcepcionNegocio("El periodo debe tener fecha de inicio");
        }
        if (fin != null && !fin.isAfter(inicio)) {
            throw new ExcepcionNegocio("La fecha de fin del periodo debe ser posterior al inicio");
        }
        this.inicio = inicio;
        this.fin = fin;
    }

    public boolean contiene(Instant instante) {
        return !instante.isBefore(inicio) && (fin == null || instante.isBefore(fin));
    }

    public boolean haVencido(Instant instante) {
        return fin != null && !instante.isBefore(fin);
    }

    public Instant getInicio() { return inicio; }
    public void setInicio(Instant inicio) { this.inicio = inicio; }
    public Instant getFin() { return fin; }
    public void setFin(Instant fin) { this.fin = fin; }
}
