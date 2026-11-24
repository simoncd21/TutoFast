package com.tutofast.repositorio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.util.TypeInformation;

import com.tutofast.config.MongoConfig;
import com.tutofast.integracion.adaptadores.DocumentoNotificacion;
import com.tutofast.modelo.Asignatura;
import com.tutofast.modelo.Calificacion;
import com.tutofast.modelo.CalificacionAlEstudiante;
import com.tutofast.modelo.CalificacionAlTutor;
import com.tutofast.modelo.Facultad;
import com.tutofast.modelo.Mensaje;
import com.tutofast.modelo.Pago;
import com.tutofast.modelo.ParametroComision;
import com.tutofast.modelo.ProgramaAcademico;
import com.tutofast.modelo.Propuesta;
import com.tutofast.modelo.RegistroAuditoria;
import com.tutofast.modelo.Reporte;
import com.tutofast.modelo.Sancion;
import com.tutofast.modelo.ServicioTutoria;
import com.tutofast.modelo.SolicitudTutoria;
import com.tutofast.modelo.TipoCalificacion;
import com.tutofast.modelo.Universidad;
import com.tutofast.modelo.Usuario;

/** Valida el mapeo a MongoDB (índices anotados, Decimal128 y discriminador) sin conectarse a Atlas. */
class MapeoMongoTest {

    private static final List<Class<?>> RAICES = List.of(Usuario.class, Universidad.class, Facultad.class,
            ProgramaAcademico.class, Asignatura.class, ParametroComision.class, SolicitudTutoria.class,
            Propuesta.class, ServicioTutoria.class, Pago.class, Mensaje.class, Calificacion.class,
            CalificacionAlTutor.class, CalificacionAlEstudiante.class, Reporte.class, Sancion.class,
            RegistroAuditoria.class, DocumentoNotificacion.class);

    private MongoMappingContext contexto;
    private MappingMongoConverter convertidor;

    @BeforeEach
    void preparar() {
        MongoCustomConversions conversiones = new MongoConfig().mongoCustomConversions();
        contexto = new MongoMappingContext();
        contexto.setSimpleTypeHolder(conversiones.getSimpleTypeHolder());
        contexto.setInitialEntitySet(Set.copyOf(RAICES));
        contexto.afterPropertiesSet();
        convertidor = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, contexto);
        convertidor.setCustomConversions(conversiones);
        convertidor.afterPropertiesSet();
    }

    private List<String> nombresDeIndices(Class<?> entidad) {
        MongoPersistentEntityIndexResolver resolvedor = new MongoPersistentEntityIndexResolver(contexto);
        List<String> nombres = new ArrayList<>();
        resolvedor.resolveIndexFor(TypeInformation.of(entidad)).forEach(
                h -> nombres.add((String) h.getIndexDefinition().getIndexOptions().get("name")));
        return nombres;
    }

    @Test
    void declaraLosIndicesAnotadosDelDiseno() {
        assertThat(nombresDeIndices(Usuario.class)).contains("uk_correo", "uk_documento", "ix_roles",
                "ix_tutor_busqueda", "ix_tutor_asignaturas", "ix_soportes_estado", "ix_est_programa");
        assertThat(nombresDeIndices(Universidad.class)).contains("uk_universidad_nombre");
        assertThat(nombresDeIndices(Facultad.class)).contains("uk_facultad_nombre");
        assertThat(nombresDeIndices(ProgramaAcademico.class)).contains("uk_programa_nombre");
        assertThat(nombresDeIndices(Asignatura.class)).contains("uk_asignatura_codigo", "ix_asignatura_activa");
        assertThat(nombresDeIndices(ParametroComision.class)).contains("ix_comision_vigente_desde");
        assertThat(nombresDeIndices(SolicitudTutoria.class)).contains("ix_busqueda", "ix_programa", "ix_mis_solicitudes");
        assertThat(nombresDeIndices(Propuesta.class)).contains("ix_propuesta_solicitud_estado", "ix_propuesta_tutor");
        assertThat(nombresDeIndices(ServicioTutoria.class)).contains("uk_servicio_solicitud", "uk_servicio_propuesta",
                "ix_servicio_estudiante", "ix_servicio_tutor", "ix_servicio_programador");
        assertThat(nombresDeIndices(Pago.class)).contains("uk_pago_servicio", "ix_pago_estado", "uk_pago_referencia");
        assertThat(nombresDeIndices(Mensaje.class)).contains("ix_hilo_marca");
        assertThat(nombresDeIndices(Calificacion.class)).contains("uk_calificacion_servicio_tipo",
                "ix_calificacion_destinatario");
        assertThat(nombresDeIndices(Reporte.class)).contains("ix_reporte_estado_fecha", "ix_reporte_reportante",
                "ix_reporte_denunciado", "ix_reporte_servicio");
        assertThat(nombresDeIndices(Sancion.class)).contains("ix_sancion_usuario_estado", "ix_sancion_reporte",
                "ix_sancion_apelacion_resultado");
        assertThat(nombresDeIndices(RegistroAuditoria.class)).contains("ix_auditoria_fecha",
                "ix_auditoria_usuario_fecha", "ix_auditoria_entidad_fecha");
        assertThat(nombresDeIndices(DocumentoNotificacion.class)).contains("ix_notificacion_usuario");
    }

    @Test
    void laSolicitudTieneUnIndiceDeTextoEnEspanol() {
        MongoPersistentEntityIndexResolver resolvedor = new MongoPersistentEntityIndexResolver(contexto);
        List<Document> textuales = new ArrayList<>();
        resolvedor.resolveIndexFor(TypeInformation.of(SolicitudTutoria.class)).forEach(h -> {
            Document claves = h.getIndexDefinition().getIndexKeys();
            if (claves.containsKey("tema") && claves.containsKey("descripcion") && "text".equals(claves.get("tema"))) {
                assertThat(h.getIndexDefinition().getIndexOptions()).containsEntry("default_language", "spanish");
                textuales.add(claves);
            }
        });
        assertThat(textuales).hasSize(1);
    }

    @Test
    void guardaLosMontosComoDecimal128() {
        Pago pago = Pago.retener("srv1", new BigDecimal("40000"), "SIM-1", Instant.parse("2026-12-05T21:00:00Z"));
        pago.liberar(new BigDecimal("10.00"), Instant.parse("2026-12-05T22:00:00Z"));

        Document doc = new Document();
        convertidor.write(pago, doc);

        assertThat(doc.get("montoRetenidoCOP")).isInstanceOf(Decimal128.class);
        assertThat(doc.get("comisionCOP")).isInstanceOf(Decimal128.class);
        assertThat(doc.get("montoNetoCOP")).isInstanceOf(Decimal128.class);

        Pago leido = convertidor.read(Pago.class, doc);
        assertThat(leido.getMontoNetoCOP()).isEqualByComparingTo("36000");
        assertThat(leido.getHistorial()).hasSize(2);
    }

    @Test
    void laHerenciaDeCalificacionUsaElTypeAlias() {
        CalificacionAlTutor c = new CalificacionAlTutor();
        c.setServicioId("srv1");
        c.setClaridadPedagogica(5);
        Document doc = new Document();
        convertidor.write(c, doc);

        assertThat(doc.get("_class")).isEqualTo("AL_TUTOR");
        assertThat(doc.get("tipo")).isEqualTo(TipoCalificacion.AL_TUTOR.name());

        assertThat(convertidor.read(Calificacion.class, doc)).isInstanceOf(CalificacionAlTutor.class);

        Document docEstudiante = new Document();
        convertidor.write(new CalificacionAlEstudiante(), docEstudiante);
        assertThat(docEstudiante.get("_class")).isEqualTo("AL_ESTUDIANTE");
        assertThat(convertidor.read(Calificacion.class, docEstudiante)).isInstanceOf(CalificacionAlEstudiante.class);
    }

    @Test
    void lasSubclasesComparteLaColeccionDeLaBase() {
        assertThat(contexto.getRequiredPersistentEntity(CalificacionAlTutor.class).getCollection())
                .isEqualTo("calificaciones");
        assertThat(contexto.getRequiredPersistentEntity(CalificacionAlEstudiante.class).getCollection())
                .isEqualTo("calificaciones");
    }

    @Test
    void laPropuestaYLaComisionGuardanSuBanderaDerivada() {
        Propuesta p = Propuesta.nueva("s", "t", com.tutofast.modelo.TipoPropuesta.ACEPTA_PRECIO, BigDecimal.TEN,
                com.tutofast.modelo.Duracion.MIN_60, com.tutofast.modelo.Modalidad.VIRTUAL, null, Instant.now());
        p.retirar(Instant.now());
        Document docPropuesta = new Document();
        convertidor.write(p, docPropuesta);
        assertThat(docPropuesta.get("activa")).isEqualTo(false);

        ParametroComision c = ParametroComision.nuevo(BigDecimal.TEN, Instant.now(), "adm");
        Document docVigente = new Document();
        convertidor.write(c, docVigente);
        assertThat(docVigente.get("vigente")).isEqualTo(true);
        c.cerrar(Instant.now());
        Document docCerrado = new Document();
        convertidor.write(c, docCerrado);
        assertThat(docCerrado.get("vigente")).isEqualTo(false);
    }
}
