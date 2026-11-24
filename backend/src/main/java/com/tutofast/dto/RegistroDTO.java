package com.tutofast.dto;

import com.tutofast.modelo.Rol;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/auth/registro}. */
public record RegistroDTO(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String nombre,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 120, message = "El correo no puede superar 120 caracteres")
        String correo,

        @NotBlank(message = "El documento es obligatorio")
        @Pattern(regexp = "^[0-9]{5,15}$", message = "El documento debe tener entre 5 y 15 dígitos")
        String documento,

        @NotBlank(message = "La clave es obligatoria")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$",
                message = "La clave debe tener entre 8 y 64 caracteres, con al menos una letra y un número")
        String clave,

        @NotNull(message = "El rol es obligatorio")
        Rol rol,

        @NotNull(message = "Debe indicar si acepta el tratamiento de datos")
        Boolean aceptaTratamientoDatos,

        @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "programaId debe ser un identificador de 24 caracteres hexadecimales")
        String programaId,

        @Min(value = 1, message = "El semestre mínimo es 1")
        @Max(value = 14, message = "El semestre máximo es 14")
        Integer semestre) {
}
