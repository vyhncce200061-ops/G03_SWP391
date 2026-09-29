package com.petshop.entity;

import com.petshop.entity.enums.PaymentEnvironment;
import com.petshop.entity.enums.PaymentMethod;
import com.petshop.entity.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Audit log and event tracking for payment gateway transactions and COD collections.
 * Mapped to table dbo.PaymentTransactions.
 */
@Entity
@Table(name = "PaymentTransactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "TransactionCode", nullable = false, unique = true, length = 80)
    private String transactionCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "OrderId", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CustomerId", nullable = false)
    private User customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "PaymentMethod", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "PaymentEnvironment", nullable = false, length = 20)
    @Builder.Default
    private PaymentEnvironment paymentEnvironment = PaymentEnvironment.SANDBOX;

    @Column(name = "Amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "TransactionStatus", nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus transactionStatus = TransactionStatus.PENDING;

    @Column(name = "ProviderOrderReference", length = 100)
    private String providerOrderReference;

    @Column(name = "ProviderTransactionId", length = 100)
    private String providerTransactionId;

    @Column(name = "ResponseCode", length = 20)
    private String responseCode;

    @Column(name = "BankCode", length = 30)
    private String bankCode;

    @Column(name = "CardType", length = 30)
    private String cardType;

    @Column(name = "SignatureVerified")
    private Boolean signatureVerified;

    @Column(name = "FailureReason", length = 1000)
    private String failureReason;

    @Column(name = "ExpiresAt")
    private LocalDateTime expiresAt;

    @Column(name = "PaidAt")
    private LocalDateTime paidAt;

    @Column(name = "CallbackReceivedAt")
    private LocalDateTime callbackReceivedAt;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.transactionStatus == null) {
            this.transactionStatus = TransactionStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
