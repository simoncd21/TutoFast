package com.tutofast.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Envoltorio de paginación de la API (docs/arquitectura/diseno-api.md, sección 1). */
public record Pagina<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {

    public static <E, T> Pagina<T> de(Page<E> pagina, Function<E, T> conversor) {
        return new Pagina<>(pagina.getContent().stream().map(conversor).toList(), pagina.getNumber(),
                pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }
}
