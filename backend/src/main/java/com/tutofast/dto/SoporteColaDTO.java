package com.tutofast.dto;

import com.tutofast.modelo.PrioridadVerificacion;

/** Elemento de la cola de verificación de tutores. */
public record SoporteColaDTO(String usuarioId, String nombre, String correo, PrioridadVerificacion prioridad,
                             String motivoPrioridad, SoporteDTO soporte) {
}
