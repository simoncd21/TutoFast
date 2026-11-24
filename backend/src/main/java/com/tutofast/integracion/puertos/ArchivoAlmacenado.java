package com.tutofast.integracion.puertos;

import java.time.Instant;

/** Referencia ligera a un archivo guardado (sin su contenido). */
public record ArchivoAlmacenado(String id, String nombre, Instant fechaSubida) {
}
