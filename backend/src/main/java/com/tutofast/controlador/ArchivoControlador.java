package com.tutofast.controlador;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tutofast.integracion.puertos.ArchivoRecuperado;
import com.tutofast.seguridad.UsuarioAutenticado;
import com.tutofast.servicio.ServicioArchivos;

@RestController
@RequestMapping("/api/archivos")
public class ArchivoControlador {

    private final ServicioArchivos servicio;

    public ArchivoControlador(ServicioArchivos servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/{id}")
    public ResponseEntity<InputStreamResource> descargar(UsuarioAutenticado usuario, @PathVariable String id) {
        ArchivoRecuperado archivo = servicio.recuperar(id, usuario);
        MediaType tipo = archivo.contentType() != null
                ? MediaType.parseMediaType(archivo.contentType()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(tipo)
                .contentLength(archivo.tamanoBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.nombre(), java.nio.charset.StandardCharsets.UTF_8)
                                .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(archivo.contenido()));
    }
}
