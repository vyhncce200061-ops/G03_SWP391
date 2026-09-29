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
    Optional<PaymentTransaction> findByProviderOrderReference(String providerOrderReference);
    List<PaymentTransaction> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    @Query("SELECT pt FROM PaymentTransaction pt " +
           "JOIN FETCH pt.order o " +
           "WHERE pt.transactionStatus = com.petshop.entity.enums.TransactionStatus.PENDING AND pt.expiresAt < :now")
    List<PaymentTransaction> findExpiredPendingTransactions(@Param("now") LocalDateTime now);
}
