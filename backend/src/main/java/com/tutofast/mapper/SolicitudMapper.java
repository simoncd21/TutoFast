package com.tutofast.mapper;

import java.util.List;

import com.tutofast.dto.ArchivoDTO;
import com.tutofast.dto.SolicitudDetalleDTO;
import com.tutofast.dto.SolicitudResumenDTO;
import com.tutofast.modelo.Archivo;
import com.tutofast.modelo.SolicitudTutoria;
import com.tutofast.modelo.Usuario;

/** Conversiones de SolicitudTutoria hacia DTO. Los nombres de catálogo se resuelven en el servicio y se pasan aquí. */
public final class SolicitudMapper {

    private SolicitudMapper() {
    }

    public static SolicitudResumenDTO aResumenDTO(SolicitudTutoria s, String asignaturaNombre,
                                                  String programaNombre, Usuario estudiante) {
        var perfil = estudiante == null ? null : estudiante.getPerfilEstudiante();
        return new SolicitudResumenDTO(s.getId(), s.getTema(), asignaturaNombre, programaNombre, s.getModalidad(),
                s.getDuracion(), s.getPrecioInicialCOP(), s.getFechaHora(), s.getEstado(), s.getTotalPropuestas(),
                estudiante == null ? null : estudiante.getNombre(),
                perfil == null ? null : perfil.getIndicadorConfiabilidad());
    }

    /** {@code servicioId} es {@code null} mientras la solicitud no esté formalizada. */
    public static SolicitudDetalleDTO aDetalleDTO(SolicitudTutoria s, String servicioId) {
        return new SolicitudDetalleDTO(s.getId(), s.getEstudianteId(), s.getUniversidadId(), s.getFacultadId(),
                s.getProgramaId(), s.getAsignaturaId(), s.getTema(), s.getDescripcion(), s.getTipoServicio(),
                s.getFechaHora(), s.getModalidad(), s.getZonaEncuentro(), s.getDuracion(), s.getPrecioInicialCOP(),
                s.getEstado(), s.getTotalPropuestas(), s.getFechaCreacion(), aArchivosDTO(s.getArchivos()), servicioId);
    }

    public static ArchivoDTO aDTO(Archivo a) {
        return new ArchivoDTO(a.getGridFsId(), a.getNombre(), a.getTipo(), a.getTamanoMB());
    }

    public static List<ArchivoDTO> aArchivosDTO(List<Archivo> archivos) {
        return archivos == null ? List.of() : archivos.stream().map(SolicitudMapper::aDTO).toList();
    }
}
