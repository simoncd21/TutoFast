package com.tutofast.repositorio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Query;

import com.tutofast.modelo.EstadoSolicitud;
import com.tutofast.modelo.Modalidad;

/** Verifica los Criteria dinámicos sin necesitar MongoDB. */
class ConsultaSolicitudesTest {

    private static Document consulta(FiltroSolicitudes filtro) {
        Query q = RepositorioSolicitudTutoriaPersonalizadoImpl.construirConsulta(filtro);
        return q.getQueryObject();
    }

    @Test
    void sinFiltrosSoloDevuelveLasSolicitudesExplorables() {
        String json = consulta(FiltroSolicitudes.vacio()).toString();

        assertThat(json).contains("estado").contains("PUBLICADA").contains("PROPUESTAS_RECIBIDAS")
                .contains("NEGOCIACION_EN_CURSO");
        assertThat(json).doesNotContain("FORMALIZADA").doesNotContain("CANCELADA");
    }

    @Test
    void aplicaSoloLosFiltrosInformados() {
        FiltroSolicitudes filtro = new FiltroSolicitudes(null, null, "prog1", "asig1", null, Modalidad.VIRTUAL,
                null, null, null, null, null, null);

        String json = consulta(filtro).toString();

        assertThat(json).contains("programaId").contains("prog1").contains("asignaturaId").contains("asig1")
                .contains("modalidad").contains("VIRTUAL");
        assertThat(json).doesNotContain("universidadId").doesNotContain("facultadId")
                .doesNotContain("fechaHora").doesNotContain("precioInicialCOP");
    }

    @Test
    void combinaRangoDePrecioYDeFechas() {
        FiltroSolicitudes filtro = new FiltroSolicitudes(null, null, null, null, null, null,
                Instant.parse("2026-12-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"),
                new BigDecimal("10000"), new BigDecimal("30000"), null, null);

        String json = consulta(filtro).toString();

        assertThat(json).contains("$gte").contains("$lte").contains("fechaHora").contains("precioInicialCOP");
    }

    @Test
    void ignoraTextosEnBlancoYUsaBusquedaDeTextoParaElTema() {
        FiltroSolicitudes sinTema = new FiltroSolicitudes(" ", "", null, null, "  ", null, null, null, null, null, null, null);
        assertThat(consulta(sinTema).toString()).doesNotContain("universidadId").doesNotContain("$text");

        FiltroSolicitudes conTema = new FiltroSolicitudes(null, null, null, null, "ecuaciones diferenciales", null,
                null, null, null, null, null, null);
        assertThat(consulta(conTema).containsKey("$text")).isTrue();
    }

    @Test
    void losEstadosExplicitosReemplazanLosPorDefecto() {
        FiltroSolicitudes filtro = new FiltroSolicitudes(null, null, null, null, null, null, null, null, null, null,
                Set.of(EstadoSolicitud.FORMALIZADA), null);

        String json = consulta(filtro).toString();

        assertThat(json).contains("FORMALIZADA").doesNotContain("PUBLICADA");
    }

    @Test
    void ordenaSegunElCriterio() {
        assertThat(ordenDe(null)).isEqualTo(Sort.by(Sort.Direction.DESC, "fechaCreacion"));
        assertThat(ordenDe(FiltroSolicitudes.Orden.PRECIO_ASC)).isEqualTo(Sort.by(Sort.Direction.ASC, "precioInicialCOP"));
        assertThat(ordenDe(FiltroSolicitudes.Orden.PRECIO_DESC)).isEqualTo(Sort.by(Sort.Direction.DESC, "precioInicialCOP"));
        assertThat(ordenDe(FiltroSolicitudes.Orden.FECHA)).isEqualTo(Sort.by(Sort.Direction.ASC, "fechaHora"));
    }

    private static Sort ordenDe(FiltroSolicitudes.Orden orden) {
        return RepositorioSolicitudTutoriaPersonalizadoImpl.ordenar(
                new FiltroSolicitudes(null, null, null, null, null, null, null, null, null, null, null, orden));
    }
}
