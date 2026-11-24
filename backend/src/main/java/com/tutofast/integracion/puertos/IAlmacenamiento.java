package com.tutofast.integracion.puertos;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Puerto de almacenamiento de archivos (implementado con GridFS dentro de MongoDB Atlas). */
public interface IAlmacenamiento {

    /** Guarda el contenido y devuelve su identificador. */
    String guardar(String nombre, String contentType, InputStream contenido);

    Optional<ArchivoRecuperado> recuperar(String id);

    /** Elimina el archivo; no falla si no existe. */
    void eliminar(String id);

    /** Archivos subidos antes de {@code limite} (candidatos a la limpieza de huérfanos). */
    List<ArchivoAlmacenado> listarAnterioresA(Instant limite);
}
