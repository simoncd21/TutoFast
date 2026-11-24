package com.tutofast.dto;

import java.math.BigDecimal;

import com.tutofast.modelo.TipoArchivo;

public record ArchivoDTO(String archivoId, String nombre, TipoArchivo tipo, BigDecimal tamanoMB) {
}
