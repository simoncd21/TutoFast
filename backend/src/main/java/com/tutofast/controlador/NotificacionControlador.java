package com.tutofast.controlador;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tutofast.dto.ConteoNoLeidasDTO;
import com.tutofast.dto.NotificacionDTO;
import com.tutofast.dto.Pagina;
import com.tutofast.seguridad.UsuarioAutenticado;
import com.tutofast.servicio.ServicioNotificaciones;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionControlador {

    private final ServicioNotificaciones servicio;

    public NotificacionControlador(ServicioNotificaciones servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public Pagina<NotificacionDTO> consultar(UsuarioAutenticado usuario,
                                             @RequestParam(defaultValue = "false") boolean soloNoLeidas,
                                             @RequestParam(defaultValue = "0") int pagina,
                                             @RequestParam(defaultValue = "20") int tamano) {
        return servicio.consultar(usuario.id(), soloNoLeidas, pagina, tamano);
    }

    @GetMapping("/conteo-no-leidas")
    public ConteoNoLeidasDTO conteoNoLeidas(UsuarioAutenticado usuario) {
        return new ConteoNoLeidasDTO(servicio.contarNoLeidas(usuario.id()));
    }

    @PatchMapping("/{id}/leida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarLeida(UsuarioAutenticado usuario, @PathVariable String id) {
        servicio.marcarLeida(id, usuario.id());
    }

    @PatchMapping("/leer-todas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarTodasLeidas(UsuarioAutenticado usuario) {
        servicio.marcarTodasLeidas(usuario.id());
    }
}
