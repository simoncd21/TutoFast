package com.tutofast.config;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Gestor que no hace nada: permite usar @Transactional con un MongoDB local standalone. */
public class GestorTransaccionesNoOp implements PlatformTransactionManager {

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
        return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
        // sin transacción real
    }

    @Override
    public void rollback(TransactionStatus status) {
        // sin transacción real
    }
}
