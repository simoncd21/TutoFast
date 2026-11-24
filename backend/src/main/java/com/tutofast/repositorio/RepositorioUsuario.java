package com.tutofast.repositorio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tutofast.modelo.EstadoSoporte;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.Usuario;

public interface RepositorioUsuario extends MongoRepository<Usuario, String>, RepositorioUsuarioPersonalizado {

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    boolean existsByDocumento(String documento);

    boolean existsByRolesContaining(Rol rol);

    /** Usuario cuyo perfil de tutor referencia ese archivo de GridFS como soporte de verificación. */
    Optional<Usuario> findByPerfilTutorSoportesRutaArchivo(String archivoId);

    /** Usuarios con al menos un soporte en ese estado (alimenta la cola de verificación). */
    List<Usuario> findByPerfilTutorSoportesEstado(EstadoSoporte estado);
}
