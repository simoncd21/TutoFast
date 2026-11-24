package com.tutofast.mapper;

import com.tutofast.dto.AsignaturaDTO;
import com.tutofast.dto.ComisionDTO;
import com.tutofast.dto.FacultadDTO;
import com.tutofast.dto.ProgramaDTO;
import com.tutofast.dto.UniversidadDTO;
import com.tutofast.modelo.Asignatura;
import com.tutofast.modelo.Facultad;
import com.tutofast.modelo.ParametroComision;
import com.tutofast.modelo.ProgramaAcademico;
import com.tutofast.modelo.Universidad;

/** Conversiones del catálogo académico y de la comisión hacia DTO. */
public final class CatalogoMapper {

    private CatalogoMapper() {
    }

    public static UniversidadDTO aDTO(Universidad u) {
        return new UniversidadDTO(u.getId(), u.getNombre());
    }

    public static FacultadDTO aDTO(Facultad f) {
        return new FacultadDTO(f.getId(), f.getUniversidadId(), f.getNombre());
    }

    public static ProgramaDTO aDTO(ProgramaAcademico p) {
        return new ProgramaDTO(p.getId(), p.getFacultadId(), p.getNombre());
    }

    public static AsignaturaDTO aDTO(Asignatura a) {
        return new AsignaturaDTO(a.getId(), a.getProgramaId(), a.getCodigo(), a.getNombre());
    }

    public static ComisionDTO aDTO(ParametroComision c) {
        return new ComisionDTO(c.getId(), c.getPorcentaje(), c.getVigenteDesde(), c.getVigenteHasta());
    }
}
