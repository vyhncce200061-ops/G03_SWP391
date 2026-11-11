package com.petshop.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/** Retries only a complete, rolled-back SQL Server deadlock transaction. */
@Component
@RequiredArgsConstructor
public class RetryingTransactions {
    private static final int MAX_ATTEMPTS = 3;
    private final PlatformTransactionManager transactionManager;

    public <T> T execute(Supplier<T> action) {
        return execute(action, false);
    }

    public void run(Runnable action) {
        execute(() -> { action.run(); return null; });
    }

    public void runNew(Runnable action) {
        execute(() -> { action.run(); return null; }, true);
    }

    private <T> T execute(Supplier<T> action, boolean requiresNew) {
        boolean ownsTransaction = requiresNew || !TransactionSynchronizationManager.isActualTransactionActive();
        var template = new TransactionTemplate(transactionManager);
        if (requiresNew) template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        for (int attempt = 1; ; attempt++) {
            try {
                return template.execute(status -> action.get());
            } catch (RuntimeException error) {
                // A participant cannot retry inside its parent's rollback-only transaction.
                if (!ownsTransaction || !isDeadlock(error)) throw error;
                if (attempt >= MAX_ATTEMPTS) {
                    throw new IllegalStateException("Hệ thống đang bận xử lý đơn hàng. Vui lòng thử lại sau ít giây.", error);
                }
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(25L, 76L) * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw error;
                }
            }
        }
    }

    private boolean isDeadlock(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                for (SQLException next = sql; next != null; next = next.getNextException()) {
                    if (next.getErrorCode() == 1205) return true;
                }
            }
        }
        return false;
    }
}
