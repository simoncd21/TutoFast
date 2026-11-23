package com.tutofast.controlador;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Comprobación de salud (también usada como health check de Render). */
@RestController
public class SaludControlador {

    private final MongoTemplate mongoTemplate;

    public SaludControlador(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping("/api/salud")
    public ResponseEntity<Map<String, String>> salud() {
        boolean baseDatosArriba = true;
        try {
            mongoTemplate.getDb().runCommand(new Document("ping", 1));
        } catch (Exception e) {
            baseDatosArriba = false;
        }
        String estado = baseDatosArriba ? "UP" : "DOWN";
        Map<String, String> cuerpo = new LinkedHashMap<>();
        cuerpo.put("estado", estado);
        cuerpo.put("baseDatos", estado);
        cuerpo.put("servicio", "tutofast-backend");
        return ResponseEntity
                .status(baseDatosArriba ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(cuerpo);
    }
}
