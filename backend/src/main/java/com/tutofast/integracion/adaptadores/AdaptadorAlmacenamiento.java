package com.tutofast.integracion.adaptadores;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Component;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.tutofast.integracion.puertos.ArchivoAlmacenado;
import com.tutofast.integracion.puertos.ArchivoRecuperado;
import com.tutofast.integracion.puertos.IAlmacenamiento;

/** Almacenamiento sobre GridFS (bucket {@code archivos}, configurado en {@code GridFsConfig}). */
@Component
public class AdaptadorAlmacenamiento implements IAlmacenamiento {

    private final GridFsTemplate gridFs;

    public AdaptadorAlmacenamiento(GridFsTemplate gridFs) {
        this.gridFs = gridFs;
    }

    @Override
    public String guardar(String nombre, String contentType, InputStream contenido) {
        ObjectId id = gridFs.store(contenido, nombre, contentType, new Document());
        return id.toHexString();
    }

    @Override
    public Optional<ArchivoRecuperado> recuperar(String id) {
        if (!ObjectId.isValid(id)) {
            return Optional.empty();
        }
        GridFSFile archivo = gridFs.findOne(query(where("_id").is(new ObjectId(id))));
        if (archivo == null) {
            return Optional.empty();
        }
        GridFsResource recurso = gridFs.getResource(archivo);
        try {
            return Optional.of(new ArchivoRecuperado(id, archivo.getFilename(), recurso.getContentType(),
                    archivo.getLength(), recurso.getInputStream()));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir el archivo " + id, e);
        }
    }

    @Override
    public void eliminar(String id) {
        if (ObjectId.isValid(id)) {
            gridFs.delete(query(where("_id").is(new ObjectId(id))));
        }
    }

    @Override
    public List<ArchivoAlmacenado> listarAnterioresA(Instant limite) {
        List<ArchivoAlmacenado> resultado = new ArrayList<>();
        gridFs.find(query(where("uploadDate").lt(Date.from(limite)))).forEach(f ->
                resultado.add(new ArchivoAlmacenado(f.getObjectId().toHexString(), f.getFilename(),
                        f.getUploadDate().toInstant())));
        return resultado;
    }
}
