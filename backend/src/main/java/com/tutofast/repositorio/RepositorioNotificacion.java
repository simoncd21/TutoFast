package com.tutofast.repositorio;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.integracion.adaptadores.DocumentoNotificacion;

public interface RepositorioNotificacion extends MongoRepository<DocumentoNotificacion, String> {

    Page<DocumentoNotificacion> findByUsuarioIdOrderByFechaDesc(String usuarioId, Pageable paginacion);

    Page<DocumentoNotificacion> findByUsuarioIdAndLeidaFalseOrderByFechaDesc(String usuarioId, Pageable paginacion);

    List<DocumentoNotificacion> findByUsuarioIdAndLeidaFalse(String usuarioId);

    long countByUsuarioIdAndLeidaFalse(String usuarioId);
}
