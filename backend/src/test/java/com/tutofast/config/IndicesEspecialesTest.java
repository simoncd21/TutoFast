package com.tutofast.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;

import com.tutofast.integracion.adaptadores.DocumentoNotificacion;
import com.tutofast.modelo.Mensaje;
import com.tutofast.modelo.ParametroComision;
import com.tutofast.modelo.Propuesta;

class IndicesEspecialesTest {

    @Test
    void creaLosCuatroIndicesParcialesYElTtl() {
        MongoTemplate plantilla = mock(MongoTemplate.class);
        IndexOperations ops = mock(IndexOperations.class);
        when(plantilla.indexOps(any(Class.class))).thenReturn(ops);

        DatosInicialesSeed.crearIndicesEspeciales(plantilla);

        verify(plantilla).indexOps(Propuesta.class);
        verify(plantilla).indexOps(ParametroComision.class);
        verify(plantilla).indexOps(Mensaje.class);
        verify(plantilla).indexOps(DocumentoNotificacion.class);

        ArgumentCaptor<IndexDefinition> captor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(ops, times(4)).ensureIndex(captor.capture());
        List<IndexDefinition> indices = captor.getAllValues();

        assertThat(indices).extracting(i -> i.getIndexOptions().get("name"))
                .containsExactly("uk_propuesta_activa", "uk_comision_vigente", "ix_mensajes_reportados", "ttl_notificaciones");
        assertThat(indices.get(0).getIndexOptions()).containsEntry("unique", true).containsKey("partialFilterExpression");
        assertThat(indices.get(1).getIndexOptions()).containsEntry("unique", true).containsKey("partialFilterExpression");
        assertThat(indices.get(2).getIndexOptions()).containsKey("partialFilterExpression").doesNotContainKey("unique");
        assertThat(indices.get(3).getIndexOptions()).containsEntry("expireAfterSeconds",
                TimeUnit.DAYS.toSeconds(180));
    }

    @Test
    void unFalloDeIndiceNoImpideArrancar() {
        MongoTemplate plantilla = mock(MongoTemplate.class);
        IndexOperations ops = mock(IndexOperations.class);
        when(plantilla.indexOps(any(Class.class))).thenReturn(ops);
        doThrow(new RuntimeException("conflicto de índice")).when(ops).ensureIndex(any());

        assertThatCode(() -> DatosInicialesSeed.crearIndicesEspeciales(plantilla)).doesNotThrowAnyException();
        verify(ops, times(4)).ensureIndex(any());
        verify(plantilla, times(4)).indexOps(any(Class.class));
    }
}
