package com.tutofast;

import java.util.Arrays;

import org.springframework.mock.web.MockMultipartFile;

/** Archivos mínimos con la firma (magic bytes) correcta de cada formato permitido. */
public final class ArchivosDePrueba {

    private ArchivosDePrueba() {
    }

    public static MockMultipartFile pdf() {
        return archivo("certificado.pdf", "application/pdf", new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34});
    }

    public static MockMultipartFile png() {
        return archivo("carne.png", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00});
    }

    public static MockMultipartFile jpg() {
        return archivo("foto.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});
    }

    public static MockMultipartFile docx() {
        return archivo("historial.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14});
    }

    public static MockMultipartFile archivo(String nombre, String contentType, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, contentType, contenido);
    }

    /** PDF válido (firma correcta) con el tamaño indicado en bytes. */
    public static MockMultipartFile pdfDeTamano(long bytes) {
        byte[] datos = new byte[(int) bytes];
        Arrays.fill(datos, (byte) 'a');
        System.arraycopy(new byte[] {0x25, 0x50, 0x44, 0x46}, 0, datos, 0, 4);
        return archivo("grande.pdf", "application/pdf", datos);
    }
}
