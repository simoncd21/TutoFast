package com.tutofast.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Almacenamiento GridFS (bucket "archivos") y gestor de transacciones.
 * Con tutofast.mongo.transacciones=true (Atlas) se usa MongoTransactionManager;
 * en otro caso, un gestor no-op para que @Transactional no falle en un Mongo sin replica set.
 */
@Configuration
public class GridFsConfig {

    public static final String BUCKET_ARCHIVOS = "archivos";

    @Bean
    public GridFsTemplate gridFsTemplate(MongoDatabaseFactory factory, MongoConverter converter) {
        return new GridFsTemplate(factory, converter, BUCKET_ARCHIVOS);
    }

    @Bean
    public PlatformTransactionManager transactionManager(
            MongoDatabaseFactory factory,
            @Value("${tutofast.mongo.transacciones:false}") boolean transaccionesActivas) {
        if (transaccionesActivas) {
            return new MongoTransactionManager(factory);
        }
        return new GestorTransaccionesNoOp();
    }
}
