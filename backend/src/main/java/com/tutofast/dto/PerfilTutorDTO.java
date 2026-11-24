package com.tutofast.dto;

import java.util.List;

import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.TipoTutor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Entrada de {@code PUT /api/usuarios/yo/perfil-tutor} (información declarada, no verificada). */
public record PerfilTutorDTO(
        @NotBlank(message = "La presentación es obligatoria")
        @Size(max = 1000, message = "La presentación no puede superar 1000 caracteres")
        String presentacion,

        @Size(max = 20, message = "Máximo 20 áreas de interés")
        List<@NotBlank(message = "Las áreas de interés no pueden estar vacías") @Size(max = 80) String> areasDeInteres,

        @Size(max = 200, message = "La formación no puede superar 200 caracteres")
        String formacion,

        @NotNull(message = "El tipo de tutor es obligatorio")
        TipoTutor tipoTutor,

        @Size(max = 500, message = "La experiencia no puede superar 500 caracteres")
        String experiencia,

        @NotEmpty(message = "Debe indicar al menos una modalidad")
        List<@NotNull Modalidad> modalidades,

        @Size(max = 200, message = "La disponibilidad no puede superar 200 caracteres")
        String disponibilidad,

        List<@NotBlank @jakarta.validation.constraints.Pattern(regexp = "^[0-9a-fA-F]{24}$",
                message = "Cada asignatura debe ser un identificador de 24 caracteres hexadecimales") String> asignaturasDominadasIds,

        @Size(max = 120, message = "La cuenta de cobro no puede superar 120 caracteres")
        String cuentaBancariaCobro) {
}
