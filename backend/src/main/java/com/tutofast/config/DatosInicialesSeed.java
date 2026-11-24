package com.tutofast.config;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import com.tutofast.integracion.adaptadores.DocumentoNotificacion;
import com.tutofast.modelo.Mensaje;
import com.tutofast.modelo.ParametroComision;
import com.tutofast.modelo.Propuesta;
import com.tutofast.servicio.ServicioCatalogo;
import com.tutofast.servicio.ServicioIdentidad;

/**
 * Datos iniciales al arrancar: índices que no se pueden declarar con anotaciones (parciales y TTL), administrador
 * inicial si se definen ADMIN_CORREO y ADMIN_CLAVE y aún no hay ninguno, y catálogo de demostración con la
 * comisión inicial del 10 % (idempotente).
 */
@Component
public class DatosInicialesSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosInicialesSeed.class);

    /** Las notificaciones se conservan 180 días. */
    static final Duration RETENCION_NOTIFICACIONES = Duration.ofDays(180);

    private final ServicioIdentidad servicioIdentidad;
    private final ServicioCatalogo servicioCatalogo;
    private final MongoTemplate mongoTemplate;
    private final String adminCorreo;
    private final String adminClave;

    public DatosInicialesSeed(ServicioIdentidad servicioIdentidad,
                              ServicioCatalogo servicioCatalogo,
                              MongoTemplate mongoTemplate,
                              @Value("${tutofast.admin.correo:}") String adminCorreo,
                              @Value("${tutofast.admin.clave:}") String adminClave) {
        this.servicioIdentidad = servicioIdentidad;
        this.servicioCatalogo = servicioCatalogo;
        this.mongoTemplate = mongoTemplate;
        this.adminCorreo = adminCorreo;
        this.adminClave = adminClave;
    }

    @Override
    public void run(ApplicationArguments args) {
        crearIndicesEspeciales(mongoTemplate);
        sembrarAdministrador();
        sembrarCatalogo();
    }

    private void sembrarAdministrador() {
        if (adminCorreo.isBlank() || adminClave.isBlank()) {
            log.info("Seed de administrador omitido: ADMIN_CORREO/ADMIN_CLAVE no definidos");
            return;
        }
        if (servicioIdentidad.crearAdministradorSiNoExiste(adminCorreo, adminClave)) {
            log.info("Administrador inicial creado: {}", adminCorreo);
        }
    }

    /** Un fallo aquí se registra pero no impide arrancar la aplicación. */
    private void sembrarCatalogo() {
        try {
            servicioCatalogo.sembrarDatosIniciales();
        } catch (RuntimeException e) {
            log.error("No se pudo sembrar el catálogo inicial: {}", e.getMessage());
        }
    }

    /**
     * Crea los índices parciales y el TTL (idempotente). Un fallo se registra como error pero no impide arrancar:
     * los demás índices y el seed siguen su curso.
     */
    static void crearIndicesEspeciales(MongoTemplate plantilla) {
        // Una sola propuesta activa por tutor y solicitud (HU-05).
        asegurar(plantilla, Propuesta.class, new Index()
                .named("uk_propuesta_activa")
                .on("solicitudId", Direction.ASC).on("tutorId", Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("activa").is(true))));

        // Una sola comisión vigente (HU-02).
        asegurar(plantilla, ParametroComision.class, new Index()
                .named("uk_comision_vigente")
                .on("vigente", Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("vigente").is(true))));

        // Cola de mensajes reportados para el administrador (HU-10).
        asegurar(plantilla, Mensaje.class, new Index()
                .named("ix_mensajes_reportados")
                .on("reportado", Direction.ASC).on("marcaTemporal", Direction.DESC)
                .partial(PartialIndexFilter.of(Criteria.where("reportado").is(true))));

        // TTL de notificaciones: 180 días.
        asegurar(plantilla, DocumentoNotificacion.class, new Index()
                .named("ttl_notificaciones")
                .on("fecha", Direction.ASC)
                .expire(RETENCION_NOTIFICACIONES));
    }

    private static void asegurar(MongoTemplate plantilla, Class<?> entidad, Index indice) {
        String nombre = (String) indice.getIndexOptions().get("name");
        try {
            plantilla.indexOps(entidad).ensureIndex(indice);
            log.info("Índice {} asegurado en {}", nombre, entidad.getSimpleName());
        } catch (RuntimeException e) {
            log.error("No se pudo crear el índice {} en {}: {}", nombre, entidad.getSimpleName(), e.getMessage());
        }
    }
}
