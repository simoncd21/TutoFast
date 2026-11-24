package com.tutofast.repositorio;

import java.util.List;
import java.util.Optional;

import com.tutofast.modelo.EstadoPago;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.modelo.Pago;

public interface RepositorioPago extends MongoRepository<Pago, String> {

    Optional<Pago> findByServicioId(String servicioId);

    Optional<Pago> findByReferenciaPasarela(String referenciaPasarela);

    List<Pago> findByEstado(EstadoPago estado);
}
