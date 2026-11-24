package com.tutofast.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.tutofast.modelo.Duracion;
import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.TipoServicio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Datos para publicar ({@code POST /api/solicitudes}) o editar ({@code PUT /api/solicitudes/{id}}) una solicitud. */
public record SolicitudEntradaDTO(
        @NotBlank(message = "La asignatura es obligatoria") String asignaturaId,
        @NotBlank(message = "El tema es obligatorio") @Size(max = 200, message = "El tema admite hasta 200 caracteres") String tema,
        @NotBlank(message = "La descripción es obligatoria") @Size(max = 2000, message = "La descripción admite hasta 2000 caracteres") String descripcion,
        @NotNull(message = "El tipo de servicio es obligatorio") TipoServicio tipoServicio,
        @NotNull(message = "La fecha y hora son obligatorias") Instant fechaHora,
        @NotNull(message = "La modalidad es obligatoria") Modalidad modalidad,
        @Size(max = 200, message = "La zona de encuentro admite hasta 200 caracteres") String zonaEncuentro,
        @NotNull(message = "La duración es obligatoria") Duracion duracion,
        @NotNull(message = "El precio inicial es obligatorio") BigDecimal precioInicialCOP) {
}
