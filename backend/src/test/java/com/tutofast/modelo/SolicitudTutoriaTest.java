package com.tutofast.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.tutofast.excepcion.ExcepcionNegocio;

class SolicitudTutoriaTest {

    private static SolicitudTutoria valida() {
        SolicitudTutoria s = new SolicitudTutoria();
        s.setTema("Ecuaciones diferenciales de primer orden y variables separables");
        s.setDescripcion("Requiero explicación detallada del método de separación de variables y resolución "
                + "guiada de 3 ejercicios preparatorios para el taller");
        s.setTipoServicio(TipoServicio.RESOLUCION_GUIADA_EJERCICIOS);
        s.setModalidad(Modalidad.PRESENCIAL);
        s.setZonaEncuentro("Biblioteca central, sala de estudio 3");
        s.setDuracion(Duracion.MIN_60);
        s.setPrecioInicialCOP(new BigDecimal("25000"));
        s.setFechaHora(Instant.parse("2026-12-05T19:00:00Z"));
        return s;
    }

    private static void assertRechazo(SolicitudTutoria s, String codigo, HttpStatus estado) {
        assertThatThrownBy(s::validarIntegridad).isInstanceOfSatisfying(ExcepcionNegocio.class, e -> {
            assertThat(e.getCodigo()).isEqualTo(codigo);
            assertThat(e.getEstado()).isEqualTo(estado);
        });
    }

    // ---- validarIntegridad ----

    @Test
    void aceptaUnaSolicitudDetallada() {
        assertThatCode(valida()::validarIntegridad).doesNotThrowAnyException();
    }

    @Test
    void rechazaDescripcionGenericaAunqueSuperaLos20Caracteres() {
        SolicitudTutoria s = valida();
        s.setDescripcion("necesito ayuda con matemáticas");
        assertRechazo(s, "DESCRIPCION_INSUFICIENTE", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void rechazaDescripcionDeMenosDe20Caracteres() {
        SolicitudTutoria s = valida();
        s.setDescripcion("Integrales dobles");
        assertRechazo(s, "DESCRIPCION_INSUFICIENTE", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void unaFraseGenericaDentroDeUnTextoLargoYEspecificoSiSeAcepta() {
        SolicitudTutoria s = valida();
        s.setDescripcion("Necesito ayuda con la demostración del teorema de Green y su aplicación al cálculo de áreas "
                + "en regiones planas con frontera suave");
        assertThatCode(s::validarIntegridad).doesNotThrowAnyException();
    }

    @Test
    void rechazaFraudeAcademicoEnLaDescripcion() {
        SolicitudTutoria s = valida();
        s.setDescripcion("Quiero que me ayuden a resolver examen parcial en vivo de ecuaciones diferenciales");
        assertRechazo(s, "FRAUDE_ACADEMICO", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void rechazaFraudeEnElTemaIgnorandoTildesYMayusculas() {
        SolicitudTutoria s = valida();
        s.setTema("SUPLANTACIÓN de quiz de cálculo");
        assertRechazo(s, "FRAUDE_ACADEMICO", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void elFraudeSeEvaluaAntesQueLaDescripcionVaga() {
        SolicitudTutoria s = valida();
        s.setDescripcion("hacer evaluación virtual por mí");
        assertRechazo(s, "FRAUDE_ACADEMICO", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void aceptaListasConfigurables() {
        SolicitudTutoria s = valida();
        s.setTema("Repaso del bloque oscuro de termodinámica");
        assertThatThrownBy(() -> s.validarIntegridad(java.util.List.of(), java.util.List.of("bloque oscuro")))
                .isInstanceOfSatisfying(ExcepcionNegocio.class, e -> assertThat(e.getCodigo()).isEqualTo("FRAUDE_ACADEMICO"));
        assertThatCode(() -> s.validarIntegridad(java.util.List.of(), java.util.List.of())).doesNotThrowAnyException();
    }

    @Test
    void rechazaDuracionDe90MinutosEnLaSolicitud() {
        SolicitudTutoria s = valida();
        s.setDuracion(Duracion.MIN_90);
        assertRechazo(s, "DATOS_INVALIDOS", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void rechazaPrecioNoPositivo() {
        SolicitudTutoria s = valida();
        s.setPrecioInicialCOP(BigDecimal.ZERO);
        assertRechazo(s, "DATOS_INVALIDOS", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void presencialExigeZonaDeEncuentro() {
        SolicitudTutoria s = valida();
        s.setZonaEncuentro(" ");
        assertRechazo(s, "DATOS_INVALIDOS", HttpStatus.UNPROCESSABLE_ENTITY);
        s.setModalidad(Modalidad.VIRTUAL);
        assertThatCode(s::validarIntegridad).doesNotThrowAnyException();
    }

    // ---- ciclo de vida ----

    @Test
    void recorreElCicloHastaFormalizada() {
        SolicitudTutoria s = valida();
        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.PUBLICADA);

        s.registrarPropuesta();
        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.PROPUESTAS_RECIBIDAS);
        s.registrarPropuesta();
        assertThat(s.getTotalPropuestas()).isEqualTo(2);

        s.iniciarNegociacion();
        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.NEGOCIACION_EN_CURSO);
        s.registrarPropuesta();
        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.NEGOCIACION_EN_CURSO);

        s.seleccionarTutor();
        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.TUTOR_SELECCIONADO);
        s.confirmar();
        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.FORMALIZADA);
    }

    @Test
    void noSeSeleccionaTutorSinPropuestas() {
        assertThatThrownBy(() -> valida().seleccionarTutor())
                .isInstanceOfSatisfying(ExcepcionNegocio.class, e -> assertThat(e.getEstado()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void noSeConfirmaSinTutorSeleccionado() {
        SolicitudTutoria s = valida();
        s.registrarPropuesta();
        assertThatThrownBy(s::confirmar).isInstanceOf(ExcepcionNegocio.class);
    }

    @Test
    void cancelarAntesDeConfirmarEsValido() {
        for (EstadoSolicitud estado : new EstadoSolicitud[] {EstadoSolicitud.PUBLICADA, EstadoSolicitud.PROPUESTAS_RECIBIDAS,
                EstadoSolicitud.NEGOCIACION_EN_CURSO, EstadoSolicitud.TUTOR_SELECCIONADO}) {
            SolicitudTutoria s = valida();
            s.setEstado(estado);
            s.cancelar();
            assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.CANCELADA);
        }
    }

    @Test
    void cancelarDespuesDeFormalizadaDevuelve409() {
        SolicitudTutoria s = valida();
        s.setEstado(EstadoSolicitud.FORMALIZADA);
        assertThatThrownBy(s::cancelar).isInstanceOfSatisfying(ExcepcionNegocio.class, e -> {
            assertThat(e.getEstado()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(e.getMessage()).contains("módulo de reportes y soporte");
        });
        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.FORMALIZADA);
    }

    @Test
    void unaSolicitudAcordadaOCanceladaNoAdmiteNuevasPropuestas() {
        for (EstadoSolicitud estado : new EstadoSolicitud[] {EstadoSolicitud.TUTOR_SELECCIONADO,
                EstadoSolicitud.FORMALIZADA, EstadoSolicitud.CANCELADA}) {
            SolicitudTutoria s = valida();
            s.setEstado(estado);
            assertThatThrownBy(s::registrarPropuesta).isInstanceOfSatisfying(ExcepcionNegocio.class,
                    e -> assertThat(e.getEstado()).isEqualTo(HttpStatus.CONFLICT));
        }
    }

    @Test
    void noSeEditaTrasLaConfirmacionBilateral() {
        SolicitudTutoria s = valida();
        assertThatCode(s::validarEditable).doesNotThrowAnyException();
        s.setEstado(EstadoSolicitud.FORMALIZADA);
        assertThatThrownBy(s::validarEditable).isInstanceOfSatisfying(ExcepcionNegocio.class,
                e -> assertThat(e.getEstado()).isEqualTo(HttpStatus.CONFLICT));
    }
}
