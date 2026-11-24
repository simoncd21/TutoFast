package com.tutofast.servicio;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.tutofast.dto.NotificacionDTO;
import com.tutofast.dto.Pagina;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.adaptadores.DocumentoNotificacion;
import com.tutofast.integracion.puertos.INotificador;
import com.tutofast.integracion.puertos.TipoNotificacion;
import com.tutofast.mapper.NotificacionMapper;
import com.tutofast.repositorio.RepositorioNotificacion;

/** Emisión y consulta de notificaciones internas (solo MongoDB; sin correo). */
@Service
public class ServicioNotificaciones {

    static final int TAMANO_MAXIMO_PAGINA = 100;

    private final INotificador notificador;
    private final RepositorioNotificacion repositorio;

    public ServicioNotificaciones(INotificador notificador, RepositorioNotificacion repositorio) {
        this.notificador = notificador;
        this.repositorio = repositorio;
    }

    public void notificar(String usuarioId, TipoNotificacion tipo, String titulo, String mensaje, String enlace) {
        if (usuarioId == null || usuarioId.isBlank() || tipo == null || titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("La notificación requiere destinatario, tipo y título");
        }
        notificador.notificar(usuarioId, tipo, titulo, mensaje, enlace);
    }

    public Pagina<NotificacionDTO> consultar(String usuarioId, boolean soloNoLeidas, int pagina, int tamano) {
        PageRequest paginacion = PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), TAMANO_MAXIMO_PAGINA));
        Page<DocumentoNotificacion> resultado = soloNoLeidas
                ? repositorio.findByUsuarioIdAndLeidaFalseOrderByFechaDesc(usuarioId, paginacion)
                : repositorio.findByUsuarioIdOrderByFechaDesc(usuarioId, paginacion);
        return Pagina.de(resultado, NotificacionMapper::aDTO);
    }

    public long contarNoLeidas(String usuarioId) {
        return repositorio.countByUsuarioIdAndLeidaFalse(usuarioId);
    }

    /** Solo el destinatario puede marcarla; para cualquier otro la notificación "no existe". */
    public void marcarLeida(String notificacionId, String usuarioId) {
        DocumentoNotificacion n = repositorio.findById(notificacionId)
                .filter(x -> x.getUsuarioId().equals(usuarioId))
                .orElseThrow(() -> new ExcepcionNoEncontrado("Notificación no encontrada"));
        if (!n.isLeida()) {
            n.setLeida(true);
            repositorio.save(n);
        }
    }

    /** Marca todas las pendientes del usuario y devuelve cuántas cambió. */
    public int marcarTodasLeidas(String usuarioId) {
        List<DocumentoNotificacion> pendientes = repositorio.findByUsuarioIdAndLeidaFalse(usuarioId);
        pendientes.forEach(n -> n.setLeida(true));
        repositorio.saveAll(pendientes);
        return pendientes.size();
    }
}
