package com.tutofast.servicio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tutofast.EscenarioNegociacion;
import com.tutofast.modelo.Apelacion;
import com.tutofast.modelo.Reporte;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.Sancion;
import com.tutofast.modelo.TipoReporte;
import com.tutofast.modelo.TipoSancion;
import com.tutofast.modelo.Usuario;
import com.tutofast.seguridad.UsuarioAutenticado;

/** Control de acceso y referencias de las evidencias de reportes y apelaciones (GridFS). */
class ContextoReportesTest {

    private EscenarioNegociacion e;
    private ContextoReportes contexto;
    private Usuario carlos;
    private Usuario laura;
    private Usuario tercero;
    private Usuario admin;

    @BeforeEach
    void preparar() {
        e = new EscenarioNegociacion();
        contexto = new ContextoReportes(e.repoReportes, e.repoSanciones);
        carlos = e.crearUsuario("Carlos Gómez", Rol.ESTUDIANTE);
        laura = e.crearUsuario("Laura Martínez", Rol.TUTOR);
        tercero = e.crearUsuario("Pedro Tercero", Rol.TUTOR);
        admin = e.crearUsuario("Administrador", Rol.ADMINISTRADOR);

        Reporte reporte = Reporte.radicar(carlos.getId(), laura.getId(), null, TipoReporte.ACOSO, "Mensajes insistentes",
                List.of("archivo-reporte"), Instant.now());
        e.repoReportes.save(reporte);
        Sancion sancion = Sancion.aplicar(reporte.getId(), laura.getId(), TipoSancion.ADVERTENCIA, null);
        Apelacion apelacion = sancion.apelar("Descargo suficientemente largo", List.of("archivo-apelacion"), Instant.now());
        e.repoSanciones.save(sancion);
        assertTrue(apelacion.estaPendiente());
    }

    private static UsuarioAutenticado como(Usuario u) {
        return EscenarioNegociacion.autenticado(u);
    }

    @Test
    void referenciaLasEvidenciasDeReportesYDeApelaciones() {
        assertTrue(contexto.referencia("archivo-reporte"));
        assertTrue(contexto.referencia("archivo-apelacion"));
        assertFalse(contexto.referencia("archivo-huerfano"));
    }

    @Test
    void laEvidenciaDelReporteLaVenElReportanteYElAdministrador() {
        assertTrue(contexto.puedeAcceder("archivo-reporte", como(carlos)));
        assertTrue(contexto.puedeAcceder("archivo-reporte", como(admin)));
        assertFalse(contexto.puedeAcceder("archivo-reporte", como(laura)), "el denunciado no ve la evidencia en su contra");
        assertFalse(contexto.puedeAcceder("archivo-reporte", como(tercero)));
    }

    @Test
    void laEvidenciaDeLaApelacionLaVenElSancionadoYElAdministrador() {
        assertTrue(contexto.puedeAcceder("archivo-apelacion", como(laura)));
        assertTrue(contexto.puedeAcceder("archivo-apelacion", como(admin)));
        assertFalse(contexto.puedeAcceder("archivo-apelacion", como(carlos)));
        assertFalse(contexto.puedeAcceder("archivo-apelacion", como(tercero)));
    }

    @Test
    void unArchivoDesconocidoNoSePuedeAbrir() {
        assertFalse(contexto.puedeAcceder("archivo-huerfano", como(carlos)));
        assertFalse(contexto.puedeAcceder("archivo-huerfano", new UsuarioAutenticado("x", "x@u.co", "X", Set.of(Rol.ESTUDIANTE))));
    }
}
