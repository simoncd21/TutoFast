package com.tutofast.dto;

import java.math.BigDecimal;
import java.util.List;

import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.TipoTutor;

/** Tutor tal como aparece en la búsqueda: sin datos privados (documento, correo, cuenta bancaria). */
public record TutorResumenDTO(String id, String nombre, TipoTutor tipoTutor, boolean verificado,
                              BigDecimal promedioCalificacion, int serviciosCompletados,
                              List<Modalidad> modalidades, List<String> areasDeInteres) {
}
