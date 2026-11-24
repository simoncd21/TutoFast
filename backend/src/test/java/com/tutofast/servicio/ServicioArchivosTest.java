package com.tutofast.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.tutofast.AlmacenamientoEnMemoria;
import com.tutofast.ArchivosDePrueba;
import com.tutofast.dto.ArchivoDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.TipoArchivo;
import com.tutofast.seguridad.UsuarioAutenticado;

class ServicioArchivosTest {

    private static final UsuarioAutenticado DUENO = new UsuarioAutenticado("u1", "d@u.co", "Dueño", Set.of(Rol.TUTOR));
    private static final UsuarioAutenticado OTRO = new UsuarioAutenticado("u2", "o@u.co", "Otro", Set.of(Rol.TUTOR));
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado("a1", "a@u.co", "Admin",
            Set.of(Rol.ADMINISTRADOR));

    private final AlmacenamientoEnMemoria almacenamiento = new AlmacenamientoEnMemoria();
    /** Contexto de prueba: los archivos "referenciados" pertenecen a u1; los administradores también acceden. */
    private final Set<String> referenciados = new java.util.HashSet<>();
    private ServicioArchivos servicio;

    @BeforeEach
    void preparar() {
        ContextoArchivo contexto = new ContextoArchivo() {
            @Override
            public boolean referencia(String archivoId) {
                return referenciados.contains(archivoId);
            }

            @Override
            public boolean puedeAcceder(String archivoId, UsuarioAutenticado u) {
                return u.id().equals("u1") || u.tieneAlgunRol(Rol.ADMINISTRADOR);
            }
        };
        servicio = new ServicioArchivos(almacenamiento, List.of(contexto));
    }

    private static ExcepcionNegocio fallo(Runnable accion) {
        return assertThrows(ExcepcionNegocio.class, accion::run);
    }

    // ---- formato ----

    @Test
    void aceptaPdfPngJpgYDocx() {
        assertEquals(TipoArchivo.PDF, servicio.guardar(ArchivosDePrueba.pdf()).tipo());
        assertEquals(TipoArchivo.PNG, servicio.guardar(ArchivosDePrueba.png()).tipo());
        assertEquals(TipoArchivo.JPG, servicio.guardar(ArchivosDePrueba.jpg()).tipo());
        assertEquals(TipoArchivo.DOCX, servicio.guardar(ArchivosDePrueba.docx()).tipo());
        assertEquals(4, almacenamiento.cantidad());
    }

    @Test
    void aceptaJpegYExtensionesEnMayusculas() {
        assertEquals(TipoArchivo.JPG, servicio.validarFormato("FOTO.JPEG"));
        assertEquals(TipoArchivo.PDF, servicio.validarFormato("Informe.PDF"));
    }

    @Test
    void exeSeRechazaCon415() {
        ExcepcionNegocio e = fallo(() -> servicio.guardar(
                ArchivosDePrueba.archivo("virus.exe", "application/octet-stream", new byte[] {0x4D, 0x5A, 0x00})));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getEstado());
        assertEquals("TIPO_ARCHIVO_NO_PERMITIDO", e.getCodigo());
        assertEquals(0, almacenamiento.cantidad());
    }

    @Test
    void sinExtensionOExtensionDobleSeRechaza() {
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, fallo(() -> servicio.validarFormato("documento")).getEstado());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, fallo(() -> servicio.validarFormato("a.pdf.exe")).getEstado());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, fallo(() -> servicio.validarFormato(null)).getEstado());
    }

    @Test
    void rechazaUnEjecutableDisfrazadoConExtensionPdf() {
        ExcepcionNegocio e = fallo(() -> servicio.guardar(
                ArchivosDePrueba.archivo("falso.pdf", "application/pdf", new byte[] {0x4D, 0x5A, (byte) 0x90, 0x00, 0x03})));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getEstado());
        assertEquals(0, almacenamiento.cantidad());
    }

    // ---- tamaño ----

    @Test
    void archivoDe11MbSeRechazaCon413() {
        ExcepcionNegocio e = fallo(() -> servicio.guardar(ArchivosDePrueba.pdfDeTamano(11L * 1024 * 1024)));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, e.getEstado());
        assertEquals("ARCHIVO_DEMASIADO_GRANDE", e.getCodigo());
        assertEquals(0, almacenamiento.cantidad());
    }

    @Test
    void archivoDeExactamente10MbSeAcepta() {
        ArchivoDTO dto = servicio.guardar(ArchivosDePrueba.pdfDeTamano(10L * 1024 * 1024));
        assertEquals(0, dto.tamanoMB().compareTo(new java.math.BigDecimal("10.00")));
    }

    @Test
    void archivoVacioSeRechazaCon400() {
        ExcepcionNegocio e = fallo(() -> servicio.guardar(new MockMultipartFile("archivo", "vacio.pdf", "application/pdf", new byte[0])));
        assertEquals(HttpStatus.BAD_REQUEST, e.getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, fallo(() -> servicio.validarTamano(0)).getEstado());
    }

    @Test
    void nombreConRutaSeReduceAlNombreBase() {
        ArchivoDTO dto = servicio.guardar(
                ArchivosDePrueba.archivo("..\\..\\etc/pasajero.pdf", "application/pdf", new byte[] {0x25, 0x50, 0x44, 0x46}));
        assertEquals("pasajero.pdf", dto.nombre());
    }

    // ---- control de acceso ----

    @Test
    void duenoYAdministradorPuedenDescargar() throws IOException {
        ArchivoDTO dto = servicio.guardar(ArchivosDePrueba.pdf());
        referenciados.add(dto.archivoId());

        var propio = servicio.recuperar(dto.archivoId(), DUENO);
        assertEquals("certificado.pdf", propio.nombre());
        assertEquals("application/pdf", propio.contentType());
        assertEquals(8, propio.contenido().readAllBytes().length);
        assertEquals(dto.nombre(), servicio.recuperar(dto.archivoId(), ADMIN).nombre());
    }

    @Test
    void otroUsuarioRecibe403() {
        ArchivoDTO dto = servicio.guardar(ArchivosDePrueba.pdf());
        referenciados.add(dto.archivoId());
        assertThrows(ExcepcionAutorizacion.class, () -> servicio.recuperar(dto.archivoId(), OTRO));
    }

    @Test
    void archivoInexistenteOSinContextoDa404() {
        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.recuperar("0123456789abcdef01234567", DUENO));
        ArchivoDTO suelto = servicio.guardar(ArchivosDePrueba.pdf());
        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.recuperar(suelto.archivoId(), ADMIN));
    }

    @Test
    void referenciaSinBinarioDa404() {
        referenciados.add("0123456789abcdef01234567");
        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.recuperar("0123456789abcdef01234567", DUENO));
    }

    // ---- limpieza de huérfanos ----

    @Test
    void limpiezaBorraSoloHuerfanosAntiguos() {
        Instant ahora = Instant.parse("2026-12-01T12:00:00Z");
        String enUso = servicio.guardar(ArchivosDePrueba.pdf()).archivoId();
        String huerfanoViejo = servicio.guardar(ArchivosDePrueba.png()).archivoId();
        String huerfanoReciente = servicio.guardar(ArchivosDePrueba.jpg()).archivoId();
        referenciados.add(enUso);
        almacenamiento.fijarFecha(enUso, ahora.minus(java.time.Duration.ofDays(5)));
        almacenamiento.fijarFecha(huerfanoViejo, ahora.minus(java.time.Duration.ofHours(25)));
        almacenamiento.fijarFecha(huerfanoReciente, ahora.minus(java.time.Duration.ofHours(2)));

        int borrados = servicio.limpiarHuerfanos(ahora);

        assertEquals(1, borrados);
        assertTrue(almacenamiento.contiene(enUso));
        assertFalse(almacenamiento.contiene(huerfanoViejo));
        assertTrue(almacenamiento.contiene(huerfanoReciente));
    }

    @Test
    void limpiezaSinContextosRegistradosNoBorraNada() {
        ServicioArchivos sinContextos = new ServicioArchivos(almacenamiento, List.of());
        String id = servicio.guardar(ArchivosDePrueba.pdf()).archivoId();
        almacenamiento.fijarFecha(id, Instant.parse("2020-01-01T00:00:00Z"));

        assertEquals(0, sinContextos.limpiarHuerfanos(Instant.now()));
        assertTrue(almacenamiento.contiene(id));
    }

    @Test
    void eliminarQuitaElArchivo() {
        String id = servicio.guardar(ArchivosDePrueba.pdf()).archivoId();
        servicio.eliminar(id);
        assertFalse(almacenamiento.contiene(id));
        assertEquals(0, almacenamiento.cantidad());
    }
}
