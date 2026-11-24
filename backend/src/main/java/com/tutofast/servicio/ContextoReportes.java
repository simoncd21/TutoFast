package com.tutofast.servicio;

import org.springframework.stereotype.Component;

import com.tutofast.modelo.Rol;
import com.tutofast.repositorio.RepositorioReporte;
import com.tutofast.repositorio.RepositorioSancion;
import com.tutofast.seguridad.UsuarioAutenticado;

/**
 * Evidencias de reportes y apelaciones: las descarga quien las subió (el reportante o el sancionado) y cualquier
 * administrador. El denunciado no ve las evidencias del reporte en su contra.
 */
@Component
public class ContextoReportes implements ContextoArchivo {

    private final RepositorioReporte reportes;
    private final RepositorioSancion sanciones;

    public ContextoReportes(RepositorioReporte reportes, RepositorioSancion sanciones) {
        this.reportes = reportes;
        this.sanciones = sanciones;
    }

    @Override
    public boolean referencia(String archivoId) {
        return !reportes.buscarPorEvidencia(archivoId).isEmpty()
                || !sanciones.buscarPorEvidenciaDeApelacion(archivoId).isEmpty();
    }

    @Override
    public boolean puedeAcceder(String archivoId, UsuarioAutenticado usuario) {
        if (usuario.tieneAlgunRol(Rol.ADMINISTRADOR)) {
            return true;
        }
        return reportes.buscarPorEvidencia(archivoId).stream()
                .anyMatch(r -> usuario.id().equals(r.getReportanteId()))
                || sanciones.buscarPorEvidenciaDeApelacion(archivoId).stream()
                .anyMatch(s -> usuario.id().equals(s.getUsuarioId()));
    }
}
