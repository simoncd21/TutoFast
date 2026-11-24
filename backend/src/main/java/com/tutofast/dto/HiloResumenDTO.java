package com.tutofast.dto;

import java.time.Instant;

/** Un hilo de la solicitud (uno por tutor con propuesta) con su último mensaje y los no leídos del lector. */
public record HiloResumenDTO(String tutorId, String tutorNombre, String ultimoMensaje, Instant ultimaActividad,
                             long noLeidos, boolean soloLectura) {
}
