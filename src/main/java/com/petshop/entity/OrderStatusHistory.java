package com.petshop.entity;

import com.petshop.entity.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable audit trail tracking order lifecycle status transitions.
 * Mapped to table dbo.OrderStatusHistories.
 */
@Entity
@Table(name = "OrderStatusHistories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "OrderId", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "OldStatus", length = 30)
    private OrderStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "NewStatus", nullable = false, length = 30)
    private OrderStatus newStatus;

    @Column(name = "Reason", length = 1000)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ChangedByUserId")
    private User changedBy;

    @Column(name = "ChangedAt", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @PrePersist
    protected void onCreate() {
        if (this.changedAt == null) {
            this.changedAt = LocalDateTime.now();
        }
    }
}
