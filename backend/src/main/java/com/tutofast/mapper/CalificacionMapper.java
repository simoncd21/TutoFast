package com.tutofast.mapper;

import java.util.LinkedHashMap;
import java.util.Map;

import com.tutofast.dto.CalificacionDTO;
import com.tutofast.dto.ResenaDTO;
import com.tutofast.modelo.Calificacion;
import com.tutofast.modelo.CalificacionAlEstudiante;
import com.tutofast.modelo.CalificacionAlTutor;

/** Conversión de Calificacion (ambas subclases) a CalificacionDTO. */
public final class CalificacionMapper {

    private CalificacionMapper() {
    }

    public static CalificacionDTO aDTO(Calificacion c) {
        Map<String, Integer> criterios = new LinkedHashMap<>();
        if (c instanceof CalificacionAlTutor t) {
            criterios.put("claridadPedagogica", t.getClaridadPedagogica());
            criterios.put("dominioTematico", t.getDominioTematico());
            criterios.put("puntualidad", t.getPuntualidad());
            criterios.put("cumplimiento", t.getCumplimiento());
            criterios.put("trato", t.getTrato());
            criterios.put("relacionCalidadPrecio", t.getRelacionCalidadPrecio());
        } else if (c instanceof CalificacionAlEstudiante e) {
            criterios.put("puntualidad", e.getPuntualidad());
            criterios.put("comunicacion", e.getComunicacion());
            criterios.put("cumplimiento", e.getCumplimiento());
        }
        return new CalificacionDTO(c.getId(), c.getTipo(), c.getServicioId(), c.getAutorId(), c.getDestinatarioId(),
                c.getComentario(), c.calcularPromedio(), criterios, c.getFechaEmision(), c.getFechaLimite(),
                c.isVisible());
    }

    public static ResenaDTO aResenaDTO(Calificacion c, String autorNombre) {
        CalificacionDTO d = aDTO(c);
        return new ResenaDTO(c.getTipo(), autorNombre, c.getComentario(), d.promedio(), d.criterios(),
                c.getFechaEmision());
    }
}
