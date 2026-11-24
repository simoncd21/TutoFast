package com.tutofast.repositorio;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.modelo.Rol;
import com.tutofast.modelo.Usuario;

public interface RepositorioUsuario extends MongoRepository<Usuario, String> {

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    boolean existsByDocumento(String documento);

    boolean existsByRolesContaining(Rol rol);

    /** Usuario cuyo perfil de tutor referencia ese archivo de GridFS como soporte de verificación. */
    Optional<Usuario> findByPerfilTutorSoportesRutaArchivo(String archivoId);
}
