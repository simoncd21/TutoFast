package com.tutofast;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.tutofast.modelo.EstadoSoporte;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.Usuario;
import com.tutofast.repositorio.RepositorioUsuario;

/** Repositorio de prueba respaldado por un mapa: evita necesitar MongoDB en las pruebas. */
public final class RepositorioUsuarioEnMemoria {

    private RepositorioUsuarioEnMemoria() {
    }

    public static RepositorioUsuario crear(Map<String, Usuario> almacen) {
        RepositorioUsuario repo = mock(RepositorioUsuario.class);
        when(repo.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 24));
            }
            almacen.put(u.getId(), u);
            return u;
        });
        when(repo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(almacen.get(inv.<String>getArgument(0))));
        when(repo.findByCorreo(anyString())).thenAnswer(inv -> almacen.values().stream()
                .filter(u -> u.getCorreo().equals(inv.getArgument(0))).findFirst());
        when(repo.existsByCorreo(anyString())).thenAnswer(inv -> almacen.values().stream()
                .anyMatch(u -> u.getCorreo().equals(inv.getArgument(0))));
        when(repo.existsByDocumento(anyString())).thenAnswer(inv -> almacen.values().stream()
                .anyMatch(u -> u.getDocumento().equals(inv.getArgument(0))));
        when(repo.existsByRolesContaining(any(Rol.class))).thenAnswer(inv -> almacen.values().stream()
                .anyMatch(u -> u.getRoles().contains(inv.<Rol>getArgument(0))));
        when(repo.findByRolesContaining(any(Rol.class))).thenAnswer(inv -> almacen.values().stream()
                .filter(u -> u.getRoles().contains(inv.<Rol>getArgument(0))).toList());
        when(repo.findByPerfilTutorSoportesRutaArchivo(anyString())).thenAnswer(inv -> almacen.values().stream()
                .filter(u -> u.getPerfilTutor() != null && u.getPerfilTutor().getSoportes().stream()
                        .anyMatch(s -> inv.getArgument(0).equals(s.getRutaArchivo())))
                .findFirst());
        when(repo.findByPerfilTutorSoportesEstado(any(EstadoSoporte.class))).thenAnswer(inv -> almacen.values().stream()
                .filter(u -> u.getPerfilTutor() != null && u.getPerfilTutor().getSoportes().stream()
                        .anyMatch(s -> s.getEstado() == inv.<EstadoSoporte>getArgument(0)))
                .toList());
        return repo;
    }

    public static RepositorioUsuario crear() {
        return crear(new LinkedHashMap<>());
    }
}
