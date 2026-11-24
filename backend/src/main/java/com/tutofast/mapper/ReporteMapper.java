package com.tutofast.mapper;

import java.util.List;
import java.util.Map;

import com.tutofast.dto.ApelacionDTO;
import com.tutofast.dto.ParticipanteDTO;
import com.tutofast.dto.ReporteDTO;
import com.tutofast.dto.ResolucionVistaDTO;
import com.tutofast.dto.SancionDTO;
import com.tutofast.dto.VigenciaDTO;
import com.tutofast.modelo.Apelacion;
import com.tutofast.modelo.EstadoSancion;
import com.tutofast.modelo.Reporte;
import com.tutofast.modelo.Sancion;
import com.tutofast.modelo.Usuario;

/** Conversiones de Reporte, Sancion y Apelacion hacia DTO. */
public final class ReporteMapper {

    private ReporteMapper() {
    }

    public static ParticipanteDTO participante(String id, Map<String, Usuario> personas) {
        if (id == null) {
            return null;
        }
        Usuario u = personas.get(id);
        return new ParticipanteDTO(id, u == null ? null : u.getNombre());
    }

    /** Vista completa (quien radicó el reporte y el administrador). */
    public static ReporteDTO aDTO(Reporte r, Map<String, Usuario> personas) {
        return new ReporteDTO(r.getId(), r.getTipo(), r.categoria(), r.getMotivo(), r.getEstado(),
                participante(r.getReportanteId(), personas), participante(r.getDenunciadoId(), personas),
                r.getServicioId(), List.copyOf(r.getEvidencias()), r.getFechaRadicacion(), resolucion(r, personas));
    }

    /** Vista del denunciado: sin la identidad del reportante ni sus evidencias. */
    public static ReporteDTO aDTORecibido(Reporte r, Map<String, Usuario> personas) {
        return new ReporteDTO(r.getId(), r.getTipo(), r.categoria(), r.getMotivo(), r.getEstado(), null,
                participante(r.getDenunciadoId(), personas), r.getServicioId(), List.of(), r.getFechaRadicacion(),
                resolucion(r, personas));
    }

    private static ResolucionVistaDTO resolucion(Reporte r, Map<String, Usuario> personas) {
        Reporte.Resolucion x = r.getResolucion();
        return x == null ? null : new ResolucionVistaDTO(x.getDecision(), x.getAccionPago(), x.getObservacion(),
                participante(x.getResueltoPor(), personas), x.getFecha());
    }

    /** {@code reporte} puede ser nulo si el expediente ya no existe. */
    public static SancionDTO aDTO(Sancion s, Reporte reporte, Map<String, Usuario> personas) {
        return new SancionDTO(s.getId(), s.getReporteId(), participante(s.getUsuarioId(), personas), s.getTipo(),
                s.getVigencia() == null ? null : new VigenciaDTO(s.getVigencia().getInicio(), s.getVigencia().getFin()),
                s.getEstado(), reporte == null ? null : reporte.getTipo(), reporte == null ? null : reporte.getMotivo(),
                s.getEstado() == EstadoSancion.VIGENTE,
                s.getApelaciones().stream().map(ReporteMapper::aDTO).toList());
    }

    public static ApelacionDTO aDTO(Apelacion a) {
        return new ApelacionDTO(a.getId(), a.getDescargo(), List.copyOf(a.getEvidencias()), a.getResultado(),
                a.getFechaPresentacion(), a.getFechaResolucion(), a.getObservacionResolucion());
    }
}
