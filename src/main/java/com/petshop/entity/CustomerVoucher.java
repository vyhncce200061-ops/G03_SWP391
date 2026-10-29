package com.petshop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Customer Voucher wallet ledger entity.
 * Mapped to table dbo.CustomerVouchers (UC-15).
 */
@Entity
@Table(name = "CustomerVouchers", uniqueConstraints = {
    @UniqueConstraint(name = "UQ_CV_Customer_Voucher", columnNames = {"CustomerId", "VoucherId"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CustomerId", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VoucherId", nullable = false)
    private Voucher voucher;

    @Column(name = "SavedAt", nullable = false, updatable = false)
    private LocalDateTime savedAt;

    @Column(name = "IsUsed", nullable = false)
    @Builder.Default
    private Boolean isUsed = false;

    @Column(name = "UsedAt")
    private LocalDateTime usedAt;

    @Transient
    private Integer remainingUses;

    @PrePersist
    protected void onCreate() {
        if (this.savedAt == null) {
            this.savedAt = LocalDateTime.now();
        }
        if (this.isUsed == null) {
            this.isUsed = false;
        }
    }
}
