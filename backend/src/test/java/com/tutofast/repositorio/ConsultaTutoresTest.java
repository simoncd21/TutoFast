package com.tutofast.repositorio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.modelo.Modalidad;

/** Verifica los Criteria de la búsqueda de tutores sin necesitar MongoDB. */
class ConsultaTutoresTest {

    private static String consulta(FiltroTutores filtro) {
        return RepositorioUsuarioPersonalizadoImpl.construirConsultaTutores(filtro).getQueryObject().toString();
    }

    @Test
    void sinFiltrosSoloTutoresConCuentaActivaYPerfil() {
        String json = consulta(new FiltroTutores(null, null, null, null, false, FiltroTutores.Orden.CALIFICACION));

        assertThat(json).contains("TUTOR").contains("ACTIVA").contains("perfilTutor");
        assertThat(json).doesNotContain("SUSPENDIDA").doesNotContain("BLOQUEADA")
                .doesNotContain("asignaturasDominadasIds").doesNotContain("promedioCalificacion");
    }

    @Test
    void aplicaTodosLosFiltrosInformados() {
        String json = consulta(new FiltroTutores(List.of("a1", "a2"), Modalidad.PRESENCIAL, "L-J",
                new BigDecimal("4.5"), true, FiltroTutores.Orden.SERVICIOS));

        assertThat(json).contains("perfilTutor.asignaturasDominadasIds").contains("a1").contains("a2")
                .contains("perfilTutor.modalidades").contains("PRESENCIAL")
                .contains("perfilTutor.disponibilidad").contains("perfilTutor.promedioCalificacion")
                .contains("perfilTutor.nivelVerificacion").contains("VERIFICADO");
    }

    @Test
    void ordenInterpretaLosValoresDeLaApi() {
        assertThat(FiltroTutores.Orden.deTexto(null)).isEqualTo(FiltroTutores.Orden.CALIFICACION);
        assertThat(FiltroTutores.Orden.deTexto("Servicios")).isEqualTo(FiltroTutores.Orden.SERVICIOS);
        org.junit.jupiter.api.Assertions.assertThrows(ExcepcionNegocio.class, () -> FiltroTutores.Orden.deTexto("raro"));
    }
}
