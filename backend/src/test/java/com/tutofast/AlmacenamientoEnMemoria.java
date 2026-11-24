package com.tutofast;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import java.io.ByteArrayInputStream;

import com.tutofast.integracion.puertos.ArchivoAlmacenado;
import com.tutofast.integracion.puertos.ArchivoRecuperado;
import com.tutofast.integracion.puertos.IAlmacenamiento;

/** Almacenamiento de prueba en memoria (sin GridFS). Permite fijar la fecha de subida de un archivo. */
public class AlmacenamientoEnMemoria implements IAlmacenamiento {

    private record Entrada(String nombre, String contentType, byte[] datos, Instant fecha) {
    }

    private final Map<String, Entrada> archivos = new LinkedHashMap<>();

    @Override
    public String guardar(String nombre, String contentType, InputStream contenido) {
        try {
            String id = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            archivos.put(id, new Entrada(nombre, contentType, contenido.readAllBytes(), Instant.now()));
            return id;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<ArchivoRecuperado> recuperar(String id) {
        return Optional.ofNullable(archivos.get(id)).map(e -> new ArchivoRecuperado(id, e.nombre(), e.contentType(),
                e.datos().length, new ByteArrayInputStream(e.datos())));
    }

    @Override
    public void eliminar(String id) {
        archivos.remove(id);
    }

    @Override
    public List<ArchivoAlmacenado> listarAnterioresA(Instant limite) {
        List<ArchivoAlmacenado> resultado = new ArrayList<>();
        archivos.forEach((id, e) -> {
            if (e.fecha().isBefore(limite)) {
                resultado.add(new ArchivoAlmacenado(id, e.nombre(), e.fecha()));
            }
        });
        return resultado;
    }

    public boolean contiene(String id) {
        return archivos.containsKey(id);
    }

    public int cantidad() {
        return archivos.size();
    }

    /** Simula que el archivo se subió en otro momento (para probar la limpieza de huérfanos). */
    public void fijarFecha(String id, Instant fecha) {
        Entrada e = archivos.get(id);
        archivos.put(id, new Entrada(e.nombre(), e.contentType(), e.datos(), fecha));
    }
}
