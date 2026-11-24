package com.tutofast.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.tutofast.RepositorioAuditoriaEnMemoria;
import com.tutofast.modelo.RegistroAuditoria;
import com.tutofast.repositorio.RepositorioRegistroAuditoria;

class ServicioAuditoriaTest {

    private final List<RegistroAuditoria> bitacora = new ArrayList<>();
    private final ServicioAuditoria servicio = new ServicioAuditoria(RepositorioAuditoriaEnMemoria.crear(bitacora));

    @Test
    void registraLaAccionConFechaYDetalle() {
        Instant antes = Instant.now();

        RegistroAuditoria r = servicio.registrarAccion("u1", "ACTIVAR_ROL", "Usuario", Map.of("rol", "TUTOR"));

        assertEquals(1, bitacora.size());
        assertEquals("u1", r.getUsuarioId());
        assertEquals("ACTIVAR_ROL", r.getAccion());
        assertEquals("Usuario", r.getEntidadAfectada());
        assertEquals("TUTOR", r.getDetalle().get("rol"));
        assertNotNull(r.getFecha());
        assertTrue(!r.getFecha().isBefore(antes));
    }

    @Test
    void permiteAccionesDelSistemaSinUsuarioYSinDetalle() {
        RegistroAuditoria r = servicio.registrarAccion(null, "CIERRE_AUTOMATICO", "ServicioTutoria", null);

        assertNull(r.getUsuarioId());
        assertTrue(r.getDetalle().isEmpty());
    }

    @Test
    void copiaElDetalleParaQueNoSeAlterePosteriormente() {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("a", 1);

        RegistroAuditoria r = servicio.registrarAccion("u1", "X", "Y", detalle);
        detalle.put("b", 2);

        assertEquals(1, r.getDetalle().size());
    }

    @Test
    void rechazaAccionVacia() {
        assertThrows(IllegalArgumentException.class, () -> servicio.registrarAccion("u1", " ", "Usuario", null));
        assertThrows(IllegalArgumentException.class, () -> servicio.registrarAccion("u1", null, "Usuario", null));
        assertTrue(bitacora.isEmpty());
    }

    @Test
    void consultaDelegaLosFiltrosEnElRepositorio() {
        RepositorioRegistroAuditoria repo = mock(RepositorioRegistroAuditoria.class);
        Pageable pagina = PageRequest.of(0, 20);
        Instant desde = Instant.parse("2026-01-01T00:00:00Z");
        Instant hasta = Instant.parse("2026-12-31T00:00:00Z");
        Page<RegistroAuditoria> esperado = new PageImpl<>(List.of(new RegistroAuditoria()));
        when(repo.buscar("u1", "Usuario", desde, hasta, pagina)).thenReturn(esperado);

        Page<RegistroAuditoria> resultado = new ServicioAuditoria(repo)
                .consultar("u1", "Usuario", desde, hasta, pagina);

        assertSame(esperado, resultado);
        verify(repo).buscar("u1", "Usuario", desde, hasta, pagina);
    }
}
