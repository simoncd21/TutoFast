package com.tutofast.servicio;

import com.tutofast.seguridad.UsuarioAutenticado;

/**
 * Un "contexto" es cualquier entidad que referencia archivos de GridFS (soporte de verificación, solicitud,
 * mensaje, reporte…). Cada módulo que adjunta archivos debe registrar un bean que implemente esta interfaz:
 * <ul>
 *   <li>{@link #referencia} evita que la limpieza diaria borre como huérfano un archivo que sí se usa;</li>
 *   <li>{@link #puedeAcceder} decide quién puede descargarlo.</li>
 * </ul>
 */
public interface ContextoArchivo {

    /** {@code true} si alguna entidad de este contexto referencia el archivo. */
    boolean referencia(String archivoId);

    /** Solo se consulta cuando {@link #referencia} es {@code true}. */
    boolean puedeAcceder(String archivoId, UsuarioAutenticado usuario);
}
