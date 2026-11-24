package com.tutofast;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.tutofast.modelo.RegistroAuditoria;
import com.tutofast.repositorio.RepositorioRegistroAuditoria;

/** Repositorio de auditoría de prueba: guarda los registros en una lista (sin MongoDB). */
public final class RepositorioAuditoriaEnMemoria {

    private RepositorioAuditoriaEnMemoria() {
    }

    public static RepositorioRegistroAuditoria crear(List<RegistroAuditoria> bitacora) {
        RepositorioRegistroAuditoria repo = mock(RepositorioRegistroAuditoria.class);
        when(repo.save(any(RegistroAuditoria.class))).thenAnswer(inv -> {
            RegistroAuditoria registro = inv.getArgument(0);
            registro.setId(String.valueOf(bitacora.size() + 1));
            bitacora.add(registro);
            return registro;
        });
        return repo;
    }
}
