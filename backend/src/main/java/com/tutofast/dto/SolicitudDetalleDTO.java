package com.tutofast.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.tutofast.modelo.Duracion;
import com.tutofast.modelo.EstadoSolicitud;
import com.tutofast.modelo.Modalidad;
import com.tutofast.modelo.TipoServicio;

public record SolicitudDetalleDTO(String id, String estudianteId, String universidadId, String facultadId,
                                  String programaId, String asignaturaId, String tema, String descripcion,
                                  TipoServicio tipoServicio, Instant fechaHora, Modalidad modalidad,
                                  String zonaEncuentro, Duracion duracion, BigDecimal precioInicialCOP,
                                  EstadoSolicitud estado, int totalPropuestas, Instant fechaCreacion,
                                  List<ArchivoDTO> archivos, String servicioId) {
}
