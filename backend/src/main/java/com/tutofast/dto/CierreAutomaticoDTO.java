package com.tutofast.dto;

import java.util.List;

/** Resultado del cierre automático (tarea programada o simulación del administrador). */
public record CierreAutomaticoDTO(int revisados, int cerrados, int omitidosPorReporte, int omitidosPorPlazo,
                                  int fallidos, List<String> serviciosCerrados) {
}
