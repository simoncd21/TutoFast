package com.tutofast.repositorio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.tutofast.modelo.TipoCalificacion;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.modelo.Calificacion;

public interface RepositorioCalificacion extends MongoRepository<Calificacion, String> {

    List<Calificacion> findByServicioId(String servicioId);

    Optional<Calificacion> findByServicioIdAndTipo(String servicioId, TipoCalificacion tipo);

    boolean existsByServicioIdAndTipo(String servicioId, TipoCalificacion tipo);

    List<Calificacion> findByDestinatarioIdAndVisibleTrueOrderByFechaEmisionDesc(String destinatarioId);

    /** Reseñas reportadas que ningún administrador ha resuelto (cola de moderación). */
    List<Calificacion> findByReportadaTrueAndModeracionIsNullOrderByFechaReporteDesc();

    /** Reseñas aún ocultas cuyo plazo de 14 días ya venció (se publican solas); las ocultadas por moderación no. */
    List<Calificacion> findByVisibleFalseAndOcultaPorModeracionFalseAndFechaLimiteLessThanEqual(Instant ahora);
}
