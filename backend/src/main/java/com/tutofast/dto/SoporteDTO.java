package com.tutofast.dto;

import java.time.Instant;

import com.tutofast.modelo.EstadoSoporte;
import com.tutofast.modelo.TipoSoporte;

public record SoporteDTO(String id, TipoSoporte tipo, String archivoId, String nombreArchivo, EstadoSoporte estado,
                         Instant fechaEnvio, Instant fechaRevision, String observacion) {
}
