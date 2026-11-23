package com.tutofast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;

import com.mongodb.client.MongoDatabase;
import com.tutofast.controlador.SaludControlador;

class SaludControladorTest {

    @Test
    void devuelveUpCuandoMongoResponde() {
        MongoTemplate plantilla = mock(MongoTemplate.class);
        MongoDatabase bd = mock(MongoDatabase.class);
        when(plantilla.getDb()).thenReturn(bd);
        when(bd.runCommand(any(Bson.class))).thenReturn(new Document("ok", 1));

        var respuesta = new SaludControlador(plantilla).salud();

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals("UP", respuesta.getBody().get("baseDatos"));
    }

    @Test
    void devuelve503CuandoMongoFalla() {
        MongoTemplate plantilla = mock(MongoTemplate.class);
        when(plantilla.getDb()).thenThrow(new RuntimeException("sin conexión"));

        var respuesta = new SaludControlador(plantilla).salud();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, respuesta.getStatusCode());
        assertEquals("DOWN", respuesta.getBody().get("baseDatos"));
    }
}
