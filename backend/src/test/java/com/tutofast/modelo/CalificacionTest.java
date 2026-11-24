package com.tutofast.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.TypeAlias;

import com.tutofast.excepcion.ExcepcionNegocio;

class CalificacionTest {

    private static CalificacionAlTutor alTutor(int claridad, int dominio, int puntualidad, int cumplimiento,
                                               int trato, int calidadPrecio) {
        CalificacionAlTutor c = new CalificacionAlTutor();
        c.setClaridadPedagogica(claridad);
        c.setDominioTematico(dominio);
        c.setPuntualidad(puntualidad);
        c.setCumplimiento(cumplimiento);
        c.setTrato(trato);
        c.setRelacionCalidadPrecio(calidadPrecio);
        return c;
    }

    private static CalificacionAlEstudiante alEstudiante(int puntualidad, int comunicacion, int cumplimiento) {
        CalificacionAlEstudiante c = new CalificacionAlEstudiante();
        c.setPuntualidad(puntualidad);
        c.setComunicacion(comunicacion);
        c.setCumplimiento(cumplimiento);
        return c;
    }

    @Test
    void promedioAlTutorEsLaMediaDeLosSeisCriterios() {
        assertThat(alTutor(5, 5, 5, 5, 5, 4).calcularPromedio()).isEqualByComparingTo("4.83");
        assertThat(alTutor(5, 5, 5, 5, 5, 5).calcularPromedio()).isEqualByComparingTo("5.00");
        assertThat(alTutor(1, 1, 1, 1, 1, 1).calcularPromedio()).isEqualByComparingTo("1.00");
        assertThat(alTutor(4, 3, 5, 4, 3, 2).calcularPromedio()).isEqualByComparingTo("3.50");
    }

    @Test
    void promedioAlEstudianteEsLaMediaDeLosTresCriterios() {
        assertThat(alEstudiante(5, 5, 4).calcularPromedio()).isEqualByComparingTo("4.67");
        assertThat(alEstudiante(5, 5, 5).calcularPromedio()).isEqualByComparingTo("5.00");
        assertThat(alEstudiante(2, 3, 3).calcularPromedio()).isEqualByComparingTo("2.67");
    }

    @Test
    void elPromedioTieneDosDecimales() {
        assertThat(alTutor(5, 5, 5, 5, 5, 4).calcularPromedio().scale()).isEqualTo(2);
    }

    @Test
    void rechazaCriteriosFueraDeRangoOAusentes() {
        assertThatThrownBy(() -> alTutor(0, 5, 5, 5, 5, 5).calcularPromedio()).isInstanceOf(ExcepcionNegocio.class);
        assertThatThrownBy(() -> alTutor(5, 5, 5, 5, 5, 6).calcularPromedio()).isInstanceOf(ExcepcionNegocio.class);
        assertThatThrownBy(() -> alEstudiante(5, -1, 5).calcularPromedio()).isInstanceOf(ExcepcionNegocio.class);
        assertThatThrownBy(() -> new CalificacionAlEstudiante().calcularPromedio()).isInstanceOf(ExcepcionNegocio.class);
    }

    @Test
    void laFechaLimiteEsFinalizacionMasCatorceDias() {
        Instant fin = Instant.parse("2026-12-05T21:00:00Z");
        assertThat(Calificacion.calcularFechaLimite(fin)).isEqualTo(Instant.parse("2026-12-19T21:00:00Z"));
    }

    @Test
    void venceDespuesDeLaFechaLimite() {
        CalificacionAlTutor c = alTutor(5, 5, 5, 5, 5, 5);
        c.setFechaLimite(Instant.parse("2026-12-19T21:00:00Z"));

        assertThat(c.haVencido(Instant.parse("2026-12-19T21:00:00Z"))).isFalse();
        assertThat(c.haVencido(Instant.parse("2026-12-19T21:00:01Z"))).isTrue();
    }

    @Test
    void nacePrivadaYSeRevelaConHacerVisible() {
        CalificacionAlEstudiante c = alEstudiante(5, 5, 5);
        assertThat(c.isVisible()).isFalse();
        c.hacerVisible();
        assertThat(c.isVisible()).isTrue();
    }

    @Test
    void cadaSubclaseFijaSuDiscriminador() {
        assertThat(new CalificacionAlTutor().getTipo()).isEqualTo(TipoCalificacion.AL_TUTOR);
        assertThat(new CalificacionAlEstudiante().getTipo()).isEqualTo(TipoCalificacion.AL_ESTUDIANTE);
        assertThat(CalificacionAlTutor.class.getAnnotation(TypeAlias.class).value()).isEqualTo("AL_TUTOR");
        assertThat(CalificacionAlEstudiante.class.getAnnotation(TypeAlias.class).value()).isEqualTo("AL_ESTUDIANTE");
    }
}
