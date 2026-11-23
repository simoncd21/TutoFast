package com.tutofast.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Permite peticiones solo desde FRONTEND_URL y los orígenes locales de desarrollo. */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final List<String> ORIGENES_LOCALES = List.of(
            "http://localhost:5500", "http://127.0.0.1:5500");

    private final String frontendUrl;

    public CorsConfig(@Value("${tutofast.cors.frontend-url}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origenes = new ArrayList<>(ORIGENES_LOCALES);
        String origen = frontendUrl == null ? "" : frontendUrl.trim().replaceAll("/+$", "");
        if (!origen.isEmpty() && !origenes.contains(origen)) {
            origenes.add(origen);
        }
        registry.addMapping("/api/**")
                .allowedOrigins(origenes.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
