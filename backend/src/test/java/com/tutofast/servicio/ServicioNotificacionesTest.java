package com.tutofast.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.adaptadores.AdaptadorNotificaciones;
import com.tutofast.integracion.adaptadores.DocumentoNotificacion;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.repositorio.RepositorioNotificacion;

class ServicioNotificacionesTest {

    private final Map<String, DocumentoNotificacion> almacen = new LinkedHashMap<>();
    private ServicioNotificaciones servicio;

    @BeforeEach
    void preparar() {
        RepositorioNotificacion repo = mock(RepositorioNotificacion.class);
        when(repo.save(any(DocumentoNotificacion.class))).thenAnswer(inv -> {
            DocumentoNotificacion n = inv.getArgument(0);
            if (n.getId() == null) {
                n.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 24));
            }
            almacen.put(n.getId(), n);
            return n;
        });
        when(repo.saveAll(any(Iterable.class))).thenAnswer(inv -> {
            List<DocumentoNotificacion> lista = new ArrayList<>();
            ((Iterable<DocumentoNotificacion>) inv.getArgument(0)).forEach(n -> { almacen.put(n.getId(), n); lista.add(n); });
            return lista;
        });
        when(repo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(almacen.get(inv.<String>getArgument(0))));
        when(repo.countByUsuarioIdAndLeidaFalse(anyString())).thenAnswer(inv -> almacen.values().stream()
                .filter(n -> n.getUsuarioId().equals(inv.getArgument(0)) && !n.isLeida()).count());
        when(repo.findByUsuarioIdAndLeidaFalse(anyString())).thenAnswer(inv -> almacen.values().stream()
                .filter(n -> n.getUsuarioId().equals(inv.getArgument(0)) && !n.isLeida()).toList());
        when(repo.findByUsuarioIdOrderByFechaDesc(anyString(), any(Pageable.class))).thenAnswer(inv ->
                pagina(inv.getArgument(0), false, inv.getArgument(1)));
        when(repo.findByUsuarioIdAndLeidaFalseOrderByFechaDesc(anyString(), any(Pageable.class))).thenAnswer(inv ->
                pagina(inv.getArgument(0), true, inv.getArgument(1)));
        servicio = new ServicioNotificaciones(new AdaptadorNotificaciones(repo), repo);
    }

    private Page<DocumentoNotificacion> pagina(String usuarioId, boolean soloNoLeidas, Pageable p) {
        List<DocumentoNotificacion> todas = almacen.values().stream()
                .filter(n -> n.getUsuarioId().equals(usuarioId) && (!soloNoLeidas || !n.isLeida()))
                .sorted(Comparator.comparing(DocumentoNotificacion::getFecha).reversed()).toList();
        int desde = (int) Math.min(p.getOffset(), todas.size());
        int hasta = Math.min(desde + p.getPageSize(), todas.size());
        return new PageImpl<>(todas.subList(desde, hasta), p, todas.size());
    }

    private void notificar(String usuarioId, String titulo) {
        servicio.notificar(usuarioId, TipoNotificacion.SOPORTE_APROBADO, titulo, "Detalle", "perfil.html");
    }

    @Test
    void notificarGuardaLaNotificacionSinLeerParaElDestinatario() {
        notificar("u1", "Soporte aprobado");

        DocumentoNotificacion n = almacen.values().iterator().next();
        assertEquals("u1", n.getUsuarioId());
        assertEquals("SOPORTE_APROBADO", n.getTipo());
        assertEquals("perfil.html", n.getEnlace());
        assertFalse(n.isLeida());
        assertTrue(n.getFecha() != null);
    }

    @Test
    void notificarExigeDestinatarioTipoYTitulo() {
        assertThrows(IllegalArgumentException.class, () -> servicio.notificar(null, TipoNotificacion.NUEVO_MENSAJE, "t", "m", null));
        assertThrows(IllegalArgumentException.class, () -> servicio.notificar("u1", null, "t", "m", null));
        assertThrows(IllegalArgumentException.class, () -> servicio.notificar("u1", TipoNotificacion.NUEVO_MENSAJE, " ", "m", null));
        assertTrue(almacen.isEmpty());
    }

    @Test
    void consultaSoloLasDelUsuarioYFiltraNoLeidas() {
        notificar("u1", "a");
        notificar("u1", "b");
        notificar("u2", "ajena");
        almacen.values().stream().filter(n -> n.getTitulo().equals("a")).findFirst().orElseThrow().setLeida(true);

        assertEquals(2, servicio.consultar("u1", false, 0, 20).totalElementos());
        var noLeidas = servicio.consultar("u1", true, 0, 20);
        assertEquals(1, noLeidas.totalElementos());
        assertEquals("b", noLeidas.contenido().get(0).titulo());
        assertEquals(1, servicio.contarNoLeidas("u1"));
        assertEquals(1, servicio.contarNoLeidas("u2"));
    }

    @Test
    void consultaPaginaYAcotaElTamano() {
        for (int i = 0; i < 5; i++) {
            notificar("u1", "n" + i);
        }
        var pagina = servicio.consultar("u1", false, 1, 2);
        assertEquals(2, pagina.contenido().size());
        assertEquals(1, pagina.pagina());
        assertEquals(3, pagina.totalPaginas());
        assertEquals(ServicioNotificaciones.TAMANO_MAXIMO_PAGINA, servicio.consultar("u1", false, -4, 5000).tamano());
    }

    @Test
    void soloElDestinatarioMarcaLeida() {
        notificar("u1", "a");
        String id = almacen.keySet().iterator().next();

        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.marcarLeida(id, "u2"));
        assertFalse(almacen.get(id).isLeida());

        servicio.marcarLeida(id, "u1");
        assertTrue(almacen.get(id).isLeida());
        assertEquals(0, servicio.contarNoLeidas("u1"));
        servicio.marcarLeida(id, "u1"); // idempotente
    }

    @Test
    void marcarLeidaDeInexistenteDa404() {
        assertThrows(ExcepcionNoEncontrado.class, () -> servicio.marcarLeida("no-existe", "u1"));
    }

    @Test
    void marcarTodasLeidasNoTocaLasAjenas() {
        notificar("u1", "a");
        notificar("u1", "b");
        notificar("u2", "c");

        assertEquals(2, servicio.marcarTodasLeidas("u1"));
        assertEquals(0, servicio.contarNoLeidas("u1"));
        assertEquals(1, servicio.contarNoLeidas("u2"));
        assertEquals(0, servicio.marcarTodasLeidas("u1"));
    }

    @Test
    void ordenaLasMasRecientesPrimero() {
        DocumentoNotificacion vieja = new DocumentoNotificacion("u1", "NUEVO_MENSAJE", "vieja", "m", null,
                Instant.parse("2026-01-01T00:00:00Z"));
        DocumentoNotificacion nueva = new DocumentoNotificacion("u1", "NUEVO_MENSAJE", "nueva", "m", null,
                Instant.parse("2026-06-01T00:00:00Z"));
        vieja.setId("a".repeat(24));
        nueva.setId("b".repeat(24));
        almacen.put(vieja.getId(), vieja);
        almacen.put(nueva.getId(), nueva);

        assertEquals("nueva", servicio.consultar("u1", false, 0, 10).contenido().get(0).titulo());
    }
}
