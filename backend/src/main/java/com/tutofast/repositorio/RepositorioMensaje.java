package com.tutofast.repositorio;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.modelo.Mensaje;

public interface RepositorioMensaje extends MongoRepository<Mensaje, String> {

    List<Mensaje> findBySolicitudIdAndTutorIdOrderByMarcaTemporalAsc(String solicitudId, String tutorId);

    /** Polling: solo los mensajes posteriores a {@code desde} (exclusivo). */
    List<Mensaje> findBySolicitudIdAndTutorIdAndMarcaTemporalAfterOrderByMarcaTemporalAsc(
            String solicitudId, String tutorId, Instant desde);

    List<Mensaje> findByReportadoTrueOrderByMarcaTemporalDesc();
}
