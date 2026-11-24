package com.tutofast.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.tutofast.servicio.ServicioIdentidad;

/** Crea el administrador inicial si se definen ADMIN_CORREO y ADMIN_CLAVE y aún no hay ninguno. */
@Component
public class DatosInicialesSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosInicialesSeed.class);

    private final ServicioIdentidad servicioIdentidad;
    private final String adminCorreo;
    private final String adminClave;

    public DatosInicialesSeed(ServicioIdentidad servicioIdentidad,
                              @Value("${tutofast.admin.correo:}") String adminCorreo,
                              @Value("${tutofast.admin.clave:}") String adminClave) {
        this.servicioIdentidad = servicioIdentidad;
        this.adminCorreo = adminCorreo;
        this.adminClave = adminClave;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminCorreo.isBlank() || adminClave.isBlank()) {
            log.info("Seed de administrador omitido: ADMIN_CORREO/ADMIN_CLAVE no definidos");
            return;
        }
        if (servicioIdentidad.crearAdministradorSiNoExiste(adminCorreo, adminClave)) {
            log.info("Administrador inicial creado: {}", adminCorreo);
        }
    }
}
