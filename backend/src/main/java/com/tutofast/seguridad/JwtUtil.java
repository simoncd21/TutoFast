package com.tutofast.seguridad;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tutofast.excepcion.ExcepcionNoAutenticado;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Emite y valida tokens JWT (HS256) firmados con JWT_SECRET. El subject es el id del usuario. */
@Component
public class JwtUtil {

    private static final int LONGITUD_MINIMA_SECRETO = 32;

    private final SecretKey clave;
    private final Duration vigencia;

    @Autowired
    public JwtUtil(@Value("${tutofast.jwt.secreto}") String secreto,
                   @Value("${tutofast.jwt.expiracion-horas:8}") long expiracionHoras) {
        this(secreto, Duration.ofHours(expiracionHoras));
    }

    public JwtUtil(String secreto, Duration vigencia) {
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < LONGITUD_MINIMA_SECRETO) {
            throw new IllegalStateException(
                    "JWT_SECRET debe tener al menos " + LONGITUD_MINIMA_SECRETO + " caracteres");
        }
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.vigencia = vigencia;
    }

    /** Token emitido y su instante de expiración. */
    public record TokenEmitido(String token, Instant expiraEn) {
    }

    public TokenEmitido generar(String usuarioId, String correo) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(vigencia);
        String token = Jwts.builder()
                .subject(usuarioId)
                .claim("correo", correo)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expira))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
        return new TokenEmitido(token, expira);
    }

    /** Devuelve el id del usuario del token o lanza {@link ExcepcionNoAutenticado}. */
    public String extraerUsuarioId(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(clave).build().parseSignedClaims(token).getPayload();
            String id = claims.getSubject();
            if (id == null || id.isBlank()) {
                throw new ExcepcionNoAutenticado("Token inválido");
            }
            return id;
        } catch (JwtException | IllegalArgumentException e) {
            throw new ExcepcionNoAutenticado("Token inválido o expirado");
        }
    }
}
