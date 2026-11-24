package com.tutofast.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.tutofast.excepcion.ExcepcionNoAutenticado;

class JwtUtilTest {

    private static final String SECRETO = "secreto-de-prueba-con-mas-de-32-caracteres-ok";

    private final JwtUtil jwt = new JwtUtil(SECRETO, Duration.ofHours(8));

    @Test
    void generaYValidaUnToken() {
        var emitido = jwt.generar("abc123", "a@b.co");

        assertEquals("abc123", jwt.extraerUsuarioId(emitido.token()));
        assertTrue(emitido.expiraEn().isAfter(Instant.now().plus(Duration.ofHours(7))));
    }

    @Test
    void rechazaTokenExpirado() {
        var expirado = new JwtUtil(SECRETO, Duration.ofSeconds(-5)).generar("abc123", "a@b.co");

        assertThrows(ExcepcionNoAutenticado.class, () -> jwt.extraerUsuarioId(expirado.token()));
    }

    @Test
    void rechazaTokenManipulado() {
        String token = jwt.generar("abc123", "a@b.co").token();
        String[] partes = token.split("\\.");
        String payloadAlterado = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"otro\"}".getBytes());
        String manipulado = partes[0] + "." + payloadAlterado + "." + partes[2];

        assertThrows(ExcepcionNoAutenticado.class, () -> jwt.extraerUsuarioId(manipulado));
    }

    @Test
    void rechazaTokenFirmadoConOtroSecreto() {
        String ajeno = new JwtUtil("otro-secreto-distinto-de-mas-de-32-caracteres", Duration.ofHours(1))
                .generar("abc123", "a@b.co").token();

        assertThrows(ExcepcionNoAutenticado.class, () -> jwt.extraerUsuarioId(ajeno));
    }

    @Test
    void rechazaBasuraYTokenVacio() {
        assertThrows(ExcepcionNoAutenticado.class, () -> jwt.extraerUsuarioId("no-es-un-jwt"));
        assertThrows(ExcepcionNoAutenticado.class, () -> jwt.extraerUsuarioId(""));
    }

    @Test
    void exigeSecretoDeAlMenos32Caracteres() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil("corto", Duration.ofHours(1)));
        assertThrows(IllegalStateException.class, () -> new JwtUtil(null, Duration.ofHours(1)));
    }
}
