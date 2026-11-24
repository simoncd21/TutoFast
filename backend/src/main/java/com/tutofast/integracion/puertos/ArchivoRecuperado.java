package com.tutofast.integracion.puertos;

import java.io.InputStream;

/** Archivo leído del almacenamiento. Quien lo consume debe cerrar {@code contenido}. */
public record ArchivoRecuperado(String id, String nombre, String contentType, long tamanoBytes, InputStream contenido) {
}
