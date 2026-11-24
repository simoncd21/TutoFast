package com.tutofast.repositorio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;

/** Verifica los Criteria de la consulta de auditoría sin necesitar MongoDB. */
class ConsultaAuditoriaTest {

    @Test
    void sinFiltrosNoRestringeNada() {
        Query q = RepositorioRegistroAuditoriaPersonalizadoImpl.construirConsulta(null, " ", null, null);

        assertTrue(q.getQueryObject().isEmpty());
    }

    @Test
    void combinaUsuarioEntidadYRangoDeFechas() {
        Instant desde = Instant.parse("2026-01-01T00:00:00Z");
        Instant hasta = Instant.parse("2026-02-01T00:00:00Z");

        Document filtro = RepositorioRegistroAuditoriaPersonalizadoImpl
                .construirConsulta("u1", "Usuario", desde, hasta).getQueryObject();

        String texto = filtro.toString();
        assertTrue(texto.contains("usuarioId") && texto.contains("u1"), texto);
        assertTrue(texto.contains("entidadAfectada") && texto.contains("Usuario"), texto);
        assertTrue(texto.contains("$gte") && texto.contains("$lte"), texto);
        assertEquals(3, filtro.getList("$and", Document.class).size());
    }

    @Test
    void aceptaSoloUnLimiteDeFecha() {
        Document filtro = RepositorioRegistroAuditoriaPersonalizadoImpl
                .construirConsulta(null, null, Instant.parse("2026-01-01T00:00:00Z"), null).getQueryObject();

        assertTrue(filtro.toString().contains("$gte"));
        assertFalse(filtro.toString().contains("$lte"));
    }
}
