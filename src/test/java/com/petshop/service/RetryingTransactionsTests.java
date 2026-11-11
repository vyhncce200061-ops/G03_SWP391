package com.petshop.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RetryingTransactionsTests {
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final RetryingTransactions transactions = new RetryingTransactions(manager);

    @AfterEach void cleanup() { TransactionSynchronizationManager.clear(); Thread.interrupted(); }

    private RuntimeException deadlock() {
        return new DataAccessResourceFailureException("SQL deadlock", new SQLException("victim", "40001", 1205));
    }

    @Test void retriesWholeTransactionAfterRollbackAndCommitsOnlySuccessfulAttempt() {
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        var attempts = new AtomicInteger();
        assertEquals(3, transactions.execute(() -> { int n=attempts.incrementAndGet(); if(n<3)throw deadlock(); return n; }));
        verify(manager,times(2)).rollback(any());verify(manager).commit(any());verify(manager,times(3)).getTransaction(any());
    }

    @Test void neverRetriesBusinessValidation() {
        var error=new IllegalStateException("insufficient stock");
        assertSame(error,assertThrows(IllegalStateException.class,()->transactions.run(()->{throw error;})));
        verify(manager).rollback(any());verify(manager,times(1)).getTransaction(any());verify(manager,never()).commit(any());
    }

    @Test void stopsAfterThreeAttemptsAndReturnsFriendlyFailureWithOriginalCause() {
        var attempts=new AtomicInteger();var error=deadlock();
        var failure=assertThrows(IllegalStateException.class,()->transactions.run(()->{attempts.incrementAndGet();throw error;}));
        assertSame(error,failure.getCause());assertTrue(failure.getMessage().contains("thử lại"));
        assertEquals(3,attempts.get());verify(manager,times(3)).rollback(any());verify(manager,never()).commit(any());
    }

    @Test void nestedParticipantLeavesRetryToOwnerOfOuterTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);var error=deadlock();
        assertSame(error,assertThrows(RuntimeException.class,()->transactions.run(()->{throw error;})));
        verify(manager,times(1)).getTransaction(any());verify(manager,times(1)).rollback(any());
    }

    @Test void doesNotRetryDuplicateKeyOrOtherSqlFailures() {
        var error=new DataAccessResourceFailureException("duplicate",new SQLException("duplicate","23000",2627));
        assertSame(error,assertThrows(RuntimeException.class,()->transactions.run(()->{throw error;})));
        verify(manager,times(1)).getTransaction(any());
    }

    @Test void recognizesDeadlockInJdbcBatchExceptionChain() {
        var batch=new BatchUpdateException();batch.setNextException(new SQLException("victim","40001",1205));
        var attempts=new AtomicInteger();
        assertEquals("ok",transactions.execute(()->{if(attempts.incrementAndGet()==1)throw new DataAccessResourceFailureException("batch",batch);return "ok";}));
        assertEquals(2,attempts.get());
    }

    @Test void interruptedRetryPreservesInterruptAndFailure() {
        Thread.currentThread().interrupt();var error=deadlock();
        assertSame(error,assertThrows(RuntimeException.class,()->transactions.run(()->{throw error;})));
        assertTrue(Thread.currentThread().isInterrupted());verify(manager,times(1)).getTransaction(any());
    }
}
