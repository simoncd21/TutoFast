package com.tutofast.repositorio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.modelo.Mensaje;

public interface RepositorioMensaje extends MongoRepository<Mensaje, String> {

    List<Mensaje> findBySolicitudIdAndTutorIdOrderByMarcaTemporalAsc(String solicitudId, String tutorId);

    /** Polling: solo los mensajes posteriores a {@code desde} (exclusivo). */
    List<Mensaje> findBySolicitudIdAndTutorIdAndMarcaTemporalAfterOrderByMarcaTemporalAsc(
            String solicitudId, String tutorId, Instant desde);

    /** Polling acotado: los {@code limite} mensajes más antiguos posteriores a {@code desde} (paginación = tope). */
    List<Mensaje> findBySolicitudIdAndTutorIdAndMarcaTemporalAfterOrderByMarcaTemporalAsc(
            String solicitudId, String tutorId, Instant desde, Pageable tope);

    /** Carga inicial: los mensajes más recientes del hilo (el servicio los devuelve en orden ascendente). */
    List<Mensaje> findBySolicitudIdAndTutorIdOrderByMarcaTemporalDesc(String solicitudId, String tutorId, Pageable tope);

    /** Mensajes del hilo que el usuario todavía no ha leído (los que envió la otra parte). */
    List<Mensaje> findBySolicitudIdAndTutorIdAndEmisorIdNotAndLeidoFalse(String solicitudId, String tutorId, String lectorId);

    long countBySolicitudIdAndTutorIdAndEmisorIdNotAndLeidoFalse(String solicitudId, String tutorId, String lectorId);

    /** Mensaje que adjunta ese archivo de GridFS (control de acceso y limpieza de huérfanos). */
    Optional<Mensaje> findByArchivoGridFsId(String gridFsId);

    List<Mensaje> findByReportadoTrueOrderByMarcaTemporalDesc();

    /** Cola de moderación: reportados que ningún administrador ha resuelto. */
    List<Mensaje> findByReportadoTrueAndModeracionIsNullOrderByMarcaTemporalDesc();
}
