package com.tutofast;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;

import com.tutofast.dto.ComisionDTO;
import com.tutofast.integracion.adaptadores.AdaptadorPasarela;
import com.tutofast.modelo.EstadoPropuesta;
import com.tutofast.modelo.Pago;
import com.tutofast.modelo.Propuesta;
import com.tutofast.modelo.RegistroAuditoria;
import com.tutofast.modelo.ServicioTutoria;
import com.tutofast.modelo.SolicitudTutoria;
import com.tutofast.modelo.Usuario;
import com.tutofast.repositorio.RepositorioPago;
import com.tutofast.repositorio.RepositorioPropuesta;
import com.tutofast.repositorio.RepositorioServicioTutoria;
import com.tutofast.repositorio.RepositorioSolicitudTutoria;
import com.tutofast.repositorio.RepositorioUsuario;
import com.tutofast.servicio.ServicioAuditoria;
import com.tutofast.servicio.ServicioCatalogo;
import com.tutofast.servicio.ServicioNegociacion;
import com.tutofast.servicio.ServicioNotificaciones;
import com.tutofast.servicio.ServicioPagos;
import com.tutofast.servicio.ServicioPropuestas;

/**
 * Servicios de negociación y pagos cableados con repositorios en memoria (sin MongoDB) y la pasarela simulada real.
 * Las pruebas de servicio y de controlador comparten esta preparación; los mapas de usuarios y solicitudes pueden
 * venir de fuera para que otros servicios (identidad) vean los mismos datos.
 */
public class EscenarioNegociacion {

    public final Map<String, Usuario> usuarios;
    public final Map<String, SolicitudTutoria> solicitudes;
    public final Map<String, Propuesta> propuestas = new LinkedHashMap<>();
    public final Map<String, ServicioTutoria> servicios = new LinkedHashMap<>();
    public final Map<String, Pago> pagos = new LinkedHashMap<>();
    public final List<RegistroAuditoria> bitacora = new ArrayList<>();

    public final RepositorioSolicitudTutoria repoSolicitudes = mock(RepositorioSolicitudTutoria.class);
    public final RepositorioPropuesta repoPropuestas = mock(RepositorioPropuesta.class);
    public final RepositorioServicioTutoria repoServicios = mock(RepositorioServicioTutoria.class);
    public final RepositorioPago repoPagos = mock(RepositorioPago.class);
    public final ServicioNotificaciones notificaciones = mock(ServicioNotificaciones.class);
    public final AdaptadorPasarela pasarela = spy(new AdaptadorPasarela());
    public final ServicioCatalogo catalogo = mock(ServicioCatalogo.class);

    /** Comisión vigente que devuelve el catálogo simulado (cámbiela para simular una nueva configuración). */
    public BigDecimal comisionVigente = new BigDecimal("10");

    public final ServicioPropuestas servicioPropuestas;
    public final ServicioPagos servicioPagos;
    public final ServicioNegociacion servicioNegociacion;
    public final ServicioAuditoria auditoria;

    public EscenarioNegociacion(RepositorioUsuario repoUsuarios, Map<String, Usuario> usuarios,
                                Map<String, SolicitudTutoria> solicitudes) {
        this.usuarios = usuarios;
        this.solicitudes = solicitudes;
        prepararUsuarios(repoUsuarios);
        prepararSolicitudes();
        prepararPropuestas();
        prepararServicios();
        prepararPagos();
        when(catalogo.consultarComision()).thenAnswer(inv ->
                new ComisionDTO("comision-1", comisionVigente, Instant.now(), null));

        auditoria = RepositorioAuditoriaEnMemoria.servicio(bitacora);
        servicioPropuestas = new ServicioPropuestas(repoPropuestas, repoSolicitudes, repoUsuarios, auditoria, notificaciones);
        servicioPagos = new ServicioPagos(pasarela, repoPagos, repoServicios, auditoria);
        servicioNegociacion = new ServicioNegociacion(repoSolicitudes, repoPropuestas, repoServicios, repoUsuarios,
                servicioPropuestas, servicioPagos, catalogo, auditoria, notificaciones);
    }

    public EscenarioNegociacion() {
        this(mock(RepositorioUsuario.class), new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    public static String nuevoId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    private void prepararUsuarios(RepositorioUsuario repo) {
        // Con el repositorio simulado vacío de las pruebas de servicio solo hace falta lectura por id.
        when(repo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(usuarios.get(inv.<String>getArgument(0))));
        when(repo.findAllById(any())).thenAnswer(inv -> {
            List<Usuario> lista = new ArrayList<>();
            for (Object id : (Iterable<?>) inv.getArgument(0)) {
                if (usuarios.containsKey(id)) {
                    lista.add(usuarios.get(id));
                }
            }
            return lista;
        });
    }

    private void prepararSolicitudes() {
        when(repoSolicitudes.save(any(SolicitudTutoria.class))).thenAnswer(inv -> {
            SolicitudTutoria s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(nuevoId());
            }
            solicitudes.put(s.getId(), s);
            return s;
        });
        when(repoSolicitudes.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(solicitudes.get(inv.<String>getArgument(0))));
        when(repoSolicitudes.findAllById(any())).thenAnswer(inv -> {
            List<SolicitudTutoria> lista = new ArrayList<>();
            for (Object id : (Iterable<?>) inv.getArgument(0)) {
                if (solicitudes.containsKey(id)) {
                    lista.add(solicitudes.get(id));
                }
            }
            return lista;
        });
    }

    private void prepararPropuestas() {
        when(repoPropuestas.save(any(Propuesta.class))).thenAnswer(inv -> {
            Propuesta p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(nuevoId());
            }
            propuestas.put(p.getId(), p);
            return p;
        });
        when(repoPropuestas.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(propuestas.get(inv.<String>getArgument(0))));
        when(repoPropuestas.existsBySolicitudIdAndTutorIdAndActivaTrue(anyString(), anyString())).thenAnswer(inv ->
                propuestas.values().stream().anyMatch(p -> p.isActiva() && p.getSolicitudId().equals(inv.getArgument(0))
                        && p.getTutorId().equals(inv.getArgument(1))));
        when(repoPropuestas.findBySolicitudIdAndActivaTrue(anyString())).thenAnswer(inv -> propuestas.values().stream()
                .filter(p -> p.isActiva() && p.getSolicitudId().equals(inv.getArgument(0))).toList());
        when(repoPropuestas.findFirstBySolicitudIdAndEstado(anyString(), any(EstadoPropuesta.class))).thenAnswer(inv ->
                propuestas.values().stream().filter(p -> p.getSolicitudId().equals(inv.getArgument(0))
                        && p.getEstado() == inv.<EstadoPropuesta>getArgument(1)).findFirst());
        when(repoPropuestas.findBySolicitudIdOrderByFechaCreacionDesc(anyString())).thenAnswer(inv -> propuestas.values()
                .stream().filter(p -> p.getSolicitudId().equals(inv.getArgument(0))).toList());
        when(repoPropuestas.findBySolicitudIdAndTutorIdOrderByFechaCreacionDesc(anyString(), anyString())).thenAnswer(inv ->
                propuestas.values().stream().filter(p -> p.getSolicitudId().equals(inv.getArgument(0))
                        && p.getTutorId().equals(inv.getArgument(1))).toList());
        when(repoPropuestas.findByTutorIdOrderByFechaCreacionDesc(anyString())).thenAnswer(inv -> propuestas.values()
                .stream().filter(p -> p.getTutorId().equals(inv.getArgument(0))).toList());
    }

    private void prepararServicios() {
        when(repoServicios.save(any(ServicioTutoria.class))).thenAnswer(inv -> {
            ServicioTutoria s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(nuevoId());
            }
            // Simula los índices únicos uk_servicio_solicitud y uk_servicio_propuesta.
            boolean duplicado = servicios.values().stream().anyMatch(o -> !o.getId().equals(s.getId())
                    && (o.getSolicitudId().equals(s.getSolicitudId()) || o.getPropuestaId().equals(s.getPropuestaId())));
            if (duplicado) {
                throw new DuplicateKeyException("uk_servicio_solicitud");
            }
            servicios.put(s.getId(), s);
            return s;
        });
        when(repoServicios.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(servicios.get(inv.<String>getArgument(0))));
        when(repoServicios.findBySolicitudId(anyString())).thenAnswer(inv -> servicios.values().stream()
                .filter(s -> s.getSolicitudId().equals(inv.getArgument(0))).findFirst());
        when(repoServicios.findByEstudianteIdOrTutorIdOrderByFechaConfirmacionDesc(anyString(), anyString())).thenAnswer(inv ->
                servicios.values().stream().filter(s -> s.getEstudianteId().equals(inv.getArgument(0))
                                || s.getTutorId().equals(inv.getArgument(1)))
                        .sorted(Comparator.comparing(ServicioTutoria::getFechaConfirmacion).reversed()).toList());
        doAnswer(inv -> {
            servicios.remove(inv.<String>getArgument(0));
            return null;
        }).when(repoServicios).deleteById(anyString());
    }

    private void prepararPagos() {
        when(repoPagos.save(any(Pago.class))).thenAnswer(inv -> {
            Pago p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(nuevoId());
            }
            boolean duplicado = pagos.values().stream().anyMatch(o -> !o.getId().equals(p.getId())
                    && o.getServicioId().equals(p.getServicioId()));
            if (duplicado) {
                throw new DuplicateKeyException("uk_pago_servicio");
            }
            pagos.put(p.getId(), p);
            return p;
        });
        when(repoPagos.findByServicioId(anyString())).thenAnswer(inv -> pagos.values().stream()
                .filter(p -> p.getServicioId().equals(inv.getArgument(0))).findFirst());
        when(repoPagos.findByServicioIdIn(anyCollection())).thenAnswer(inv -> pagos.values().stream()
                .filter(p -> inv.<java.util.Collection<String>>getArgument(0).contains(p.getServicioId())).toList());
        doAnswer(inv -> {
            pagos.remove(inv.<String>getArgument(0));
            return null;
        }).when(repoPagos).deleteById(anyString());
    }
}
