package com.petshop.entity;

import com.petshop.entity.enums.DiscountType;
import com.petshop.entity.enums.VoucherStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Promotional discount voucher entity.
 * Mapped to table dbo.Vouchers.
 */
@Entity
@Table(name = "Vouchers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "Code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "Title", nullable = false, length = 150)
    private String title;

    @Column(name = "Description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "DiscountType", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "DiscountValue", nullable = false, precision = 18, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "MinOrderAmount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Column(name = "MaxDiscountAmount", precision = 18, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "StartDate", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "EndDate", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "TotalUsageLimit", nullable = false)
    private Integer totalUsageLimit;

    @Column(name = "UsedCount", nullable = false)
    @Builder.Default
    private Integer usedCount = 0;

    @Column(name = "PerCustomerLimit", nullable = false)
    @Builder.Default
    private Integer perCustomerLimit = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private VoucherStatus status = VoucherStatus.ACTIVE;

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
        if (this.usedCount == null) {
            this.usedCount = 0;
        }
        if (this.perCustomerLimit == null) {
            this.perCustomerLimit = 1;
        }
        if (this.minOrderAmount == null) {
            this.minOrderAmount = BigDecimal.ZERO;
        }
        if (this.status == null) {
            this.status = VoucherStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
