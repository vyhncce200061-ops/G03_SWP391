package com.petshop.repository;

import com.petshop.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByTransactionCode(String transactionCode);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentTransaction p WHERE p.id = :id")
    Optional<PaymentTransaction> findByIdForUpdate(@Param("id") Long id);
    Optional<PaymentTransaction> findByProviderOrderReference(String providerOrderReference);
    List<PaymentTransaction> findByOrderIdOrderByCreatedAtDesc(Long orderId);
    boolean existsByProviderTransactionIdAndIdNot(String providerTransactionId, Long id);

    // Read scalar IDs before locking the order, without caching stale mutable entities.
    @Query("SELECT pt.order.id FROM PaymentTransaction pt WHERE pt.transactionCode = :code")
    Optional<Long> findOrderIdByTransactionCode(@Param("code") String code);

    @Query("SELECT DISTINCT pt.order.id FROM PaymentTransaction pt " +
           "WHERE pt.transactionStatus = com.petshop.entity.enums.TransactionStatus.PENDING " +
           "AND pt.expiresAt <= :now ORDER BY pt.order.id")
    List<Long> findExpiredPendingOrderIds(@Param("now") LocalDateTime now);

    @Query("SELECT pt FROM PaymentTransaction pt " +
           "JOIN FETCH pt.order o " +
           "WHERE pt.transactionStatus = com.petshop.entity.enums.TransactionStatus.PENDING AND pt.expiresAt < :now")
    List<PaymentTransaction> findExpiredPendingTransactions(@Param("now") LocalDateTime now);
}
