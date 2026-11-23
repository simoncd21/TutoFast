package com.tutofast.excepcion;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.tutofast.dto.ErrorDTO;

@RestControllerAdvice
public class ManejadorGlobalExcepciones {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalExcepciones.class);

    @ExceptionHandler(ExcepcionNegocio.class)
    public ResponseEntity<ErrorDTO> negocio(ExcepcionNegocio e) {
        return respuesta(e.getEstado(), e.getCodigo(), e.getMessage());
    }

    @ExceptionHandler(ExcepcionNoEncontrado.class)
    public ResponseEntity<ErrorDTO> noEncontrado(ExcepcionNoEncontrado e) {
        return respuesta(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorDTO> rutaInexistente(NoResourceFoundException e) {
        return respuesta(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Recurso no encontrado");
    }

    @ExceptionHandler(ExcepcionAutorizacion.class)
    public ResponseEntity<ErrorDTO> autorizacion(ExcepcionAutorizacion e) {
        return respuesta(HttpStatus.FORBIDDEN, "NO_AUTORIZADO", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDTO> validacion(MethodArgumentNotValidException e) {
        List<ErrorDTO.Detalle> detalles = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErrorDTO.Detalle(f.getField(), f.getDefaultMessage()))
                .toList();
        ErrorDTO cuerpo = new ErrorDTO("VALIDACION", "Los datos enviados no son válidos",
                detalles, Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorDTO> solicitudMalFormada(Exception e) {
        return respuesta(HttpStatus.BAD_REQUEST, "VALIDACION", "La solicitud está mal formada o le faltan datos");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorDTO> duplicado(DuplicateKeyException e) {
        return respuesta(HttpStatus.CONFLICT, "DUPLICADO", "Ya existe un registro con esos datos");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorDTO> archivoGrande(MaxUploadSizeExceededException e) {
        return respuesta(HttpStatus.PAYLOAD_TOO_LARGE, "ARCHIVO_DEMASIADO_GRANDE",
                "El archivo supera el tamaño máximo de 10 MB");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorDTO> tipoNoSoportado(HttpMediaTypeNotSupportedException e) {
        return respuesta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "TIPO_ARCHIVO_NO_PERMITIDO",
                "Tipo de contenido no soportado");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorDTO> metodoNoPermitido(HttpRequestMethodNotSupportedException e) {
        return respuesta(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO", "Método HTTP no permitido");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDTO> inesperado(Exception e) {
        log.error("Error inesperado", e);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Ocurrió un error inesperado");
    }

    private ResponseEntity<ErrorDTO> respuesta(HttpStatus estado, String codigo, String mensaje) {
        return ResponseEntity.status(estado).body(ErrorDTO.de(codigo, mensaje));
    }
}
