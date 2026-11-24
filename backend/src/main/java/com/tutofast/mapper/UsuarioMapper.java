package com.tutofast.mapper;

import java.util.ArrayList;
import java.util.List;

import com.tutofast.dto.PerfilCompletoDTO;
import com.tutofast.dto.PerfilEstudianteDTO;
import com.tutofast.dto.PerfilTutorDTO;
import com.tutofast.dto.PerfilTutorVistaDTO;
import com.tutofast.dto.UsuarioDTO;
import com.tutofast.modelo.NivelVerificacion;
import com.tutofast.modelo.PerfilEstudiante;
import com.tutofast.modelo.PerfilTutor;
import com.tutofast.modelo.Usuario;

/** Conversiones Usuario ↔ DTO. */
public final class UsuarioMapper {

    private UsuarioMapper() {
    }

    public static UsuarioDTO aUsuarioDTO(Usuario u) {
        return new UsuarioDTO(u.getId(), u.getNombre(), u.getCorreo(), List.copyOf(u.getRoles()), u.getEstadoCuenta());
    }

    public static PerfilCompletoDTO aPerfilCompletoDTO(Usuario u) {
        return new PerfilCompletoDTO(u.getId(), u.getNombre(), u.getCorreo(), u.getDocumento(),
                List.copyOf(u.getRoles()), u.getEstadoCuenta(), u.getFechaRegistro(),
                aPerfilEstudianteDTO(u.getPerfilEstudiante()), aPerfilTutorVistaDTO(u.getPerfilTutor()));
    }

    public static PerfilEstudianteDTO aPerfilEstudianteDTO(PerfilEstudiante p) {
        return p == null ? null : new PerfilEstudianteDTO(p.getProgramaId(), p.getSemestre());
    }

    public static PerfilTutorVistaDTO aPerfilTutorVistaDTO(PerfilTutor p) {
        if (p == null) {
            return null;
        }
        return new PerfilTutorVistaDTO(p.getPresentacion(), p.getAreasDeInteres(), p.getFormacion(),
                p.getTipoTutor(), p.getExperiencia(), p.getModalidades(), p.getDisponibilidad(),
                p.getAsignaturasDominadasIds(), p.getCuentaBancariaCobro(), p.getNivelVerificacion(),
                etiqueta(p.getNivelVerificacion()), p.getServiciosCompletados());
    }

    /** Copia los datos declarados del DTO al perfil; no toca la verificación. */
    public static void aplicar(PerfilTutorDTO dto, PerfilTutor perfil) {
        perfil.setPresentacion(dto.presentacion());
        perfil.setAreasDeInteres(copiar(dto.areasDeInteres()));
        perfil.setFormacion(dto.formacion());
        perfil.setTipoTutor(dto.tipoTutor());
        perfil.setExperiencia(dto.experiencia());
        perfil.setModalidades(copiar(dto.modalidades()));
        perfil.setDisponibilidad(dto.disponibilidad());
        perfil.setAsignaturasDominadasIds(copiar(dto.asignaturasDominadasIds()));
        perfil.setCuentaBancariaCobro(dto.cuentaBancariaCobro());
    }

    private static String etiqueta(NivelVerificacion nivel) {
        return switch (nivel) {
            case VERIFICADO -> "Tutor Verificado";
            case EN_REVISION -> "Tutor en revisión";
            case NO_VERIFICADO -> "Tutor Registrado (No Verificado)";
        };
    }

    private static <T> List<T> copiar(List<T> origen) {
        return origen == null ? new ArrayList<>() : new ArrayList<>(origen);
    }
}
