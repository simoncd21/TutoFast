package com.tutofast.controlador;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tutofast.dto.Pagina;
import com.tutofast.dto.PerfilPublicoTutorDTO;
import com.tutofast.dto.ResenaDTO;
import com.tutofast.dto.TutorResumenDTO;
import com.tutofast.modelo.Modalidad;
import com.tutofast.servicio.ServicioIdentidad;

/** Búsqueda y perfil público de tutores y reseñas (docs/arquitectura/diseno-api.md, secciones 4 y 5). Sin lógica de negocio. */
@RestController
@RequestMapping("/api")
public class TutorControlador {

    private final ServicioIdentidad servicio;

    public TutorControlador(ServicioIdentidad servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/tutores")
    public Pagina<TutorResumenDTO> buscar(
            @RequestParam(required = false) String programaId,
            @RequestParam(required = false) String asignaturaId,
            @RequestParam(required = false) Modalidad modalidad,
            @RequestParam(required = false) String disponibilidad,
            @RequestParam(required = false) BigDecimal calificacionMin,
            @RequestParam(defaultValue = "false") boolean soloVerificados,
            @RequestParam(required = false) String orden,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "12") int tamano) {
        return servicio.buscarTutores(programaId, asignaturaId, modalidad, disponibilidad, calificacionMin,
                soloVerificados, orden, pagina, tamano);
    }

    @GetMapping("/tutores/{id}")
    public PerfilPublicoTutorDTO perfilPublico(@PathVariable String id) {
        return servicio.obtenerPerfilPublicoTutor(id);
    }

    @GetMapping("/usuarios/{id}/resenas")
    public List<ResenaDTO> resenas(@PathVariable String id) {
        return servicio.listarResenas(id);
    }
}
