package com.tutofast.servicio;

import java.net.URI;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detecta intentos de sacar la conversación de la plataforma: teléfonos, correos, usuarios de redes, enlaces
 * externos y propuestas de pago directo. Es deliberadamente conservador con los números (precios como
 * {@code 1.500.000}, fechas y horas no se confunden con un teléfono) y deja pasar los enlaces de videollamada
 * habituales, que el tutor necesita compartir para dictar la sesión.
 *
 * <p>Sin estado ni acceso a datos: se usa en los mensajes del chat y en los comentarios de las reseñas.
 */
public final class FiltroContactoExterno {

    /** Anfitriones de videollamada que sí se admiten dentro del chat. */
    static final Set<String> ANFITRIONES_PERMITIDOS = Set.of(
            "meet.google.com", "zoom.us", "teams.microsoft.com", "teams.live.com");

    private static final Pattern CORREO = Pattern.compile(
            "[a-z0-9._%+-]+\\s*(?:@|\\(at\\)|\\[at\\]|\\barroba\\b)\\s*[a-z0-9-]+(?:\\s*(?:\\.|\\(dot\\)|\\bpunto\\b)\\s*[a-z0-9-]+)+");
    private static final Pattern USUARIO_DE_RED = Pattern.compile("(?<![a-z0-9_.])@[a-z0-9_.]{3,}");
    private static final Pattern ENLACE = Pattern.compile("(?:https?://|www\\.)[^\\s]+");
    private static final Pattern ACORTADOR_O_RED = Pattern.compile(
            "\\b(?:wa\\.me|t\\.me|bit\\.ly|tinyurl\\.com|instagram\\.com|facebook\\.com|fb\\.me|discord\\.gg|linktr\\.ee|m\\.me)\\b");
    /** Una secuencia de al menos 7 dígitos, admitiendo espacios, puntos, guiones y paréntesis entre ellos. */
    private static final Pattern SECUENCIA_NUMERICA = Pattern.compile("(?<![\\d])\\+?\\d(?:[\\s().-]*\\d){6,}(?![\\d])");
    private static final Pattern PRECIO_CON_MILES = Pattern.compile("^\\$?\\d{1,3}(?:[.,]\\d{3})+$");
    private static final Pattern FECHA_ISO = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern PISTA_DE_TELEFONO = Pattern.compile(
            "(?:\\bcel(?:ular)?\\b|\\bcell\\b|\\btel(?:efono)?\\b|\\bwhats?\\s?app\\b|\\bllama(?:me)?\\b|\\bnumero\\b|\\bnum\\b)\\W{0,15}\\+?\\d(?:[\\s().-]*\\d){5,}");

    private static final List<Frase> FRASES_DE_EVASION = List.of(
            frase("whatsapp|whats\\s?app|wasap|wsp|guasap", "WhatsApp"),
            frase("telegram", "Telegram"),
            frase("instagram", "Instagram"),
            frase("facebook|messenger", "Facebook"),
            frase("discord|snapchat", "otra red social"),
            frase("mi\\s+(?:numero|cel|celular|telefono|whats|correo|email|gmail|hotmail|instagram)", "datos de contacto personales"),
            frase("(?:escribeme|escribame|llamame|llameme|contactame|contacteme|buscame)\\s+(?:al|por|a)\\b", "contacto directo"),
            frase("(?:pago|pagos|pagame|pagarte|pagarme|pagale|cobro)\\s+(?:directo|por\\s+fuera|aparte|en\\s+efectivo)", "pago fuera de la plataforma"),
            frase("fuera\\s+de\\s+(?:la\\s+plataforma|tutofast|la\\s+app|la\\s+aplicacion)", "acuerdos fuera de la plataforma"),
            frase("sin\\s+(?:pasar\\s+por\\s+(?:la\\s+plataforma|tutofast|la\\s+app)|comision)", "acuerdos fuera de la plataforma"),
            frase("nequi|daviplata|breb|bre-b", "pago por billetera externa"),
            frase("transferencia\\s+directa|consigname|consignamelo|depositame", "pago directo"));

    private FiltroContactoExterno() {
    }

    /** Motivo legible si el texto parece compartir contacto o evadir la plataforma; vacío si está limpio. */
    public static Optional<String> detectar(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        String t = normalizar(texto);
        if (CORREO.matcher(t).find()) {
            return Optional.of("un correo electrónico");
        }
        if (USUARIO_DE_RED.matcher(t).find()) {
            return Optional.of("un usuario de red social");
        }
        Optional<String> enlace = enlaceNoPermitido(t);
        if (enlace.isPresent()) {
            return enlace;
        }
        if (PISTA_DE_TELEFONO.matcher(t).find() || contieneTelefono(t)) {
            return Optional.of("un número de teléfono");
        }
        for (Frase frase : FRASES_DE_EVASION) {
            Matcher m = frase.patron().matcher(t);
            if (m.find()) {
                return Optional.of(frase.etiqueta());
            }
        }
        return Optional.empty();
    }

    private static Optional<String> enlaceNoPermitido(String t) {
        Matcher m = ENLACE.matcher(t);
        while (m.find()) {
            if (!anfitrionPermitido(m.group())) {
                return Optional.of("un enlace externo");
            }
        }
        // Dominios sin esquema (wa.me/..., t.me/..., instagram.com/...) aunque no empiecen por http.
        Matcher a = ACORTADOR_O_RED.matcher(t);
        while (a.find()) {
            if (!anfitrionPermitido(a.group())) {
                return Optional.of("un enlace externo");
            }
        }
        return Optional.empty();
    }

    private static boolean anfitrionPermitido(String enlace) {
        String conEsquema = enlace.startsWith("http") ? enlace : "https://" + enlace;
        try {
            String host = URI.create(conEsquema.replaceAll("[\\s]+", "")).getHost();
            if (host == null) {
                return false;
            }
            String limpio = host.startsWith("www.") ? host.substring(4) : host;
            return ANFITRIONES_PERMITIDOS.stream().anyMatch(p -> limpio.equals(p) || limpio.endsWith("." + p));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean contieneTelefono(String t) {
        Matcher m = SECUENCIA_NUMERICA.matcher(t);
        while (m.find()) {
            String candidato = m.group().strip();
            if (PRECIO_CON_MILES.matcher(candidato).matches() || FECHA_ISO.matcher(candidato).matches()) {
                continue;
            }
            int digitos = candidato.replaceAll("\\D", "").length();
            if (digitos >= 10) {
                return true; // celulares y números con indicativo
            }
        }
        return false;
    }

    private static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinTildes.toLowerCase(Locale.ROOT);
    }

    private static Frase frase(String alternativas, String etiqueta) {
        return new Frase(Pattern.compile("\\b(?:" + alternativas + ")\\b"), etiqueta);
    }

    /** Patrón de evasión con la descripción que se muestra al usuario. */
    private record Frase(Pattern patron, String etiqueta) {
    }
}
