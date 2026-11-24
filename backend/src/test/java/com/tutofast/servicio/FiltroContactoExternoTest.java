package com.tutofast.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FiltroContactoExternoTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Escríbeme al 3001234567 y hablamos",
            "mi celular es 300 123 45 67",
            "llámame al +57 300 123 4567",
            "es el (300) 123-4567",
            "3 0 0 1 2 3 4 5 6 7",
            "cel: 3101234567",
            "tel 6012345",
            "Mi WhatsApp es el mismo de la universidad"})
    void detectaTelefonosYNumerosDeContacto(String texto) {
        assertTrue(FiltroContactoExterno.detectar(texto).isPresent(), texto);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "mi correo es laura@gmail.com",
            "escribe a laura (at) gmail punto com",
            "laura arroba gmail punto com",
            "Mi Correo: LAURA@UNIVERSIDAD.EDU.CO"})
    void detectaCorreos(String texto) {
        assertEquals(Optional.of("un correo electrónico"), FiltroContactoExterno.detectar(texto), texto);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "agrégame, soy @laura_tutora",
            "búscame por telegram",
            "te paso mi instagram",
            "hablemos por Whats App",
            "entra a https://wa.me/573001234567",
            "mira https://instagram.com/laura",
            "wa.me/573001234567",
            "t.me/laura",
            "https://miweb.com/contacto",
            "www.miweb.co"})
    void detectaRedesSocialesYEnlacesExternos(String texto) {
        assertTrue(FiltroContactoExterno.detectar(texto).isPresent(), texto);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "mejor pago directo y te hago un descuento",
            "pagame por fuera de la plataforma",
            "podemos arreglar fuera de TutoFast",
            "sin pasar por la plataforma te sale más barato",
            "te pago por Nequi",
            "te consigno por daviplata",
            "hagamos una transferencia directa",
            "así no pagas comisión, sin comisión"})
    void detectaLaEvasionDePagos(String texto) {
        assertTrue(FiltroContactoExterno.detectar(texto).isPresent(), texto);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Hola Laura, ¿puedes explicarme la separación de variables?",
            "La sesión cuesta $1.500.000 en total? No, son 36.000 pesos",
            "El precio es 1.500.000",
            "Nos vemos el 2026-12-05 a las 19:00",
            "Resolví los ejercicios 12, 13 y 14 de la página 245",
            "Aquí está el enlace de la sesión: https://meet.google.com/abc-defg-hij",
            "Entra a https://zoom.us/j/123456789 a la hora acordada",
            "https://teams.microsoft.com/l/meetup-join/abc",
            "Me quedan 35 minutos antes de la clase y llevo 3 horas estudiando",
            "Gracias, quedó muy claro"})
    void noBloqueaConversacionesNormalesPreciosFechasNiVideollamadas(String texto) {
        assertEquals(Optional.empty(), FiltroContactoExterno.detectar(texto), texto);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void textoVacioONuloNoSeBloquea(String texto) {
        assertEquals(Optional.empty(), FiltroContactoExterno.detectar(texto));
    }

    @Test
    void ignoraTildesYMayusculas() {
        assertTrue(FiltroContactoExterno.detectar("ESCRÍBEME POR WHATSAPP").isPresent());
        assertTrue(FiltroContactoExterno.detectar("Llámame al número 3001234567").isPresent());
    }

    @Test
    void unEnlaceDeVideollamadaMezcladoConUnoExternoSiSeBloquea() {
        assertEquals(Optional.of("un enlace externo"),
                FiltroContactoExterno.detectar("https://meet.google.com/abc y también https://miweb.com/x"));
    }
}
