package com.tutofast.servicio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.tutofast.dto.ArchivoDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.puertos.ArchivoAlmacenado;
import com.tutofast.integracion.puertos.ArchivoRecuperado;
import com.tutofast.integracion.puertos.IAlmacenamiento;
import com.tutofast.modelo.TipoArchivo;
import com.tutofast.seguridad.UsuarioAutenticado;

/**
 * Validación, almacenamiento y entrega de archivos (PDF, PNG, JPG, DOCX de hasta 10 MB).
 * Ningún método es {@code @Transactional}: las operaciones de GridFS nunca van dentro de una transacción.
 */
@Service
public class ServicioArchivos {

    public static final long TAMANO_MAXIMO_BYTES = 10L * 1024 * 1024;
    /** Un archivo sin ninguna referencia se considera huérfano solo pasado este tiempo desde su subida. */
    public static final Duration GRACIA_HUERFANOS = Duration.ofHours(24);
    static final int LARGO_MAXIMO_NOMBRE = 150;

    private static final Logger log = LoggerFactory.getLogger(ServicioArchivos.class);

    private final IAlmacenamiento almacenamiento;
    private final List<ContextoArchivo> contextos;

    public ServicioArchivos(IAlmacenamiento almacenamiento, List<ContextoArchivo> contextos) {
        this.almacenamiento = almacenamiento;
        this.contextos = contextos;
    }

    /** Determina el tipo por la extensión del nombre; cualquier otra cosa es 415. */
    public TipoArchivo validarFormato(String nombre) {
        String minuscula = nombre == null ? "" : nombre.toLowerCase(Locale.ROOT).trim();
        int punto = minuscula.lastIndexOf('.');
        String extension = punto < 0 ? "" : minuscula.substring(punto + 1);
        return switch (extension) {
            case "pdf" -> TipoArchivo.PDF;
            case "png" -> TipoArchivo.PNG;
            case "jpg", "jpeg" -> TipoArchivo.JPG;
            case "docx" -> TipoArchivo.DOCX;
            default -> throw tipoNoPermitido();
        };
    }

    public void validarTamano(long bytes) {
        if (bytes <= 0) {
            throw new ExcepcionNegocio("VALIDACION", "El archivo está vacío", HttpStatus.BAD_REQUEST);
        }
        if (bytes > TAMANO_MAXIMO_BYTES) {
            throw new ExcepcionNegocio("ARCHIVO_DEMASIADO_GRANDE", "El archivo supera el tamaño máximo de 10 MB",
                    HttpStatus.PAYLOAD_TOO_LARGE);
        }
    }

    /** Valida formato, tamaño y firma del contenido, y guarda el archivo en GridFS. */
    public ArchivoDTO guardar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ExcepcionNegocio("VALIDACION", "Debe adjuntar un archivo", HttpStatus.BAD_REQUEST);
        }
        String nombre = limpiarNombre(archivo.getOriginalFilename());
        TipoArchivo tipo = validarFormato(nombre);
        validarTamano(archivo.getSize());
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new ExcepcionNegocio("ERROR_ARCHIVO", "No se pudo leer el archivo enviado",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        validarFirma(tipo, contenido);
        String id = almacenamiento.guardar(nombre, tipoMime(tipo), new ByteArrayInputStream(contenido));
        BigDecimal megas = BigDecimal.valueOf(contenido.length)
                .divide(BigDecimal.valueOf(1024L * 1024), 2, RoundingMode.HALF_UP);
        return new ArchivoDTO(id, nombre, tipo, megas);
    }

    /** Devuelve el archivo si el usuario puede acceder según el contexto que lo referencia. */
    public ArchivoRecuperado recuperar(String archivoId, UsuarioAutenticado usuario) {
        ContextoArchivo contexto = contextos.stream().filter(c -> c.referencia(archivoId)).findFirst()
                .orElseThrow(() -> new ExcepcionNoEncontrado("Archivo no encontrado"));
        if (!contexto.puedeAcceder(archivoId, usuario)) {
            throw new ExcepcionAutorizacion("No tiene acceso a este archivo");
        }
        return almacenamiento.recuperar(archivoId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Archivo no encontrado"));
    }

    public void eliminar(String archivoId) {
        almacenamiento.eliminar(archivoId);
    }

    /** Tarea diaria (03:30). Un fallo se registra y no detiene el planificador. */
    @Scheduled(cron = "0 30 3 * * *")
    public void limpiezaProgramada() {
        try {
            int borrados = limpiarHuerfanos(Instant.now());
            log.info("Limpieza de archivos huérfanos: {} eliminados", borrados);
        } catch (RuntimeException e) {
            log.error("Falló la limpieza de archivos huérfanos", e);
        }
    }

    /**
     * Elimina los archivos subidos hace más de {@link #GRACIA_HUERFANOS} que ningún contexto referencia.
     * Si no hay contextos registrados no borra nada (evita vaciar el almacenamiento por una mala configuración).
     */
    public int limpiarHuerfanos(Instant ahora) {
        if (contextos.isEmpty()) {
            log.warn("Limpieza omitida: no hay contextos de archivos registrados");
            return 0;
        }
        int borrados = 0;
        for (ArchivoAlmacenado candidato : almacenamiento.listarAnterioresA(ahora.minus(GRACIA_HUERFANOS))) {
            if (contextos.stream().noneMatch(c -> c.referencia(candidato.id()))) {
                almacenamiento.eliminar(candidato.id());
                borrados++;
            }
        }
        return borrados;
    }

    private static void validarFirma(TipoArchivo tipo, byte[] c) {
        boolean valido = switch (tipo) {
            case PDF -> empieza(c, 0x25, 0x50, 0x44, 0x46);                   // %PDF
            case PNG -> empieza(c, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case JPG -> empieza(c, 0xFF, 0xD8, 0xFF);
            case DOCX -> empieza(c, 0x50, 0x4B, 0x03, 0x04);                  // contenedor ZIP
        };
        if (!valido) {
            throw tipoNoPermitido();
        }
    }

    private static boolean empieza(byte[] contenido, int... firma) {
        if (contenido.length < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if ((contenido[i] & 0xFF) != firma[i]) {
                return false;
            }
        }
        return true;
    }

    static String tipoMime(TipoArchivo tipo) {
        return switch (tipo) {
            case PDF -> "application/pdf";
            case PNG -> "image/png";
            case JPG -> "image/jpeg";
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        };
    }

    private static String limpiarNombre(String original) {
        String nombre = original == null ? "" : original.replace('\\', '/');
        nombre = nombre.substring(nombre.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (nombre.length() > LARGO_MAXIMO_NOMBRE) {
            int punto = nombre.lastIndexOf('.');
            String extension = punto >= 0 && nombre.length() - punto <= 6 ? nombre.substring(punto) : "";
            nombre = nombre.substring(0, LARGO_MAXIMO_NOMBRE - extension.length()) + extension;
        }
        return nombre;
    }

    private static ExcepcionNegocio tipoNoPermitido() {
        return new ExcepcionNegocio("TIPO_ARCHIVO_NO_PERMITIDO",
                "Tipo de archivo no permitido. Solo se admiten formatos seguros (PDF, PNG, JPG, DOCX)",
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }
}
