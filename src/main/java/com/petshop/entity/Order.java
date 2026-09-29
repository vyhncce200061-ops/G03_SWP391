package com.petshop.entity;

import com.petshop.entity.enums.CancelType;
import com.petshop.entity.enums.OrderStatus;
import com.petshop.entity.enums.PaymentMethod;
import com.petshop.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Sales order entity containing price totals, shipping snapshot, and state machine.
 * Mapped to table dbo.Orders.
 */
@Entity
@Table(name = "Orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "OrderCode", nullable = false, unique = true, length = 40)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CustomerId", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VoucherId")
    private Voucher voucher;

    @Column(name = "VoucherCodeSnapshot", length = 50)
    private String voucherCodeSnapshot;

    @Column(name = "RecipientName", nullable = false, length = 150)
    private String recipientName;

    @Column(name = "RecipientPhone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(name = "ShippingProvince", nullable = false, length = 100)
    private String shippingProvince;

    @Column(name = "ShippingDistrict", nullable = false, length = 100)
    private String shippingDistrict;

    @Column(name = "ShippingWard", nullable = false, length = 100)
    private String shippingWard;

    @Column(name = "ShippingAddressLine", nullable = false, length = 300)
    private String shippingAddressLine;

    @Column(name = "CustomerNote", length = 1000)
    private String customerNote;

    @Column(name = "SubtotalAmount", nullable = false, precision = 18, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(name = "DiscountAmount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "ShippingFee", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal shippingFee = new BigDecimal("30000");

    @Column(name = "TotalAmount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "OrderStatus", nullable = false, length = 30)
    private OrderStatus orderStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "PaymentMethod", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "PaymentStatus", nullable = false, length = 30)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Enumerated(EnumType.STRING)
    @Column(name = "CancelType", length = 30)
    private CancelType cancelType;

    @Column(name = "CancelReason", length = 1000)
    private String cancelReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CancelledByUserId")
    private User cancelledBy;

    @Column(name = "CancelledAt")
    private LocalDateTime cancelledAt;

    @Column(name = "CompletedAt")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @Builder.Default
    private List<OrderStatusHistory> statusHistories = new ArrayList<>();

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
        if (this.discountAmount == null) {
            this.discountAmount = BigDecimal.ZERO;
        }
        if (this.shippingFee == null) {
            this.shippingFee = new BigDecimal("30000");
        }
        if (this.paymentStatus == null) {
            this.paymentStatus = PaymentStatus.UNPAID;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
