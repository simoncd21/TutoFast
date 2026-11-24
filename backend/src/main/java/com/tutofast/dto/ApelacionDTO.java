package com.tutofast.dto;

import java.time.Instant;
import java.util.List;

import com.tutofast.modelo.ResultadoApelacion;

public record ApelacionDTO(String id, String descargo, List<String> evidencias, ResultadoApelacion resultado,
                           Instant fechaPresentacion, Instant fechaResolucion, String observacionResolucion) {
}
