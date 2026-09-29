package com.petshop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Line items of an order containing historical product snapshots and a computed line total.
 * Mapped to table dbo.OrderItems.
 * 
 * CRITICAL INVARIANT:
 * LineTotal is a persisted computed column in SQL Server (CAST(UnitPrice * Quantity AS DECIMAL(18,2))).
 * It MUST be marked insertable = false, updatable = false.
 */
@Entity
@Table(name = "OrderItems", uniqueConstraints = {
    @UniqueConstraint(name = "UQ_OrderItems_OrderVariant", columnNames = {"OrderId", "VariantId"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "OrderId", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VariantId", nullable = false)
    private ProductVariant variant;

    @Column(name = "ProductNameSnapshot", nullable = false, length = 250)
    private String productNameSnapshot;

    @Column(name = "SkuCodeSnapshot", nullable = false, length = 80)
    private String skuCodeSnapshot;

    @Column(name = "VariantNameSnapshot", nullable = false, length = 200)
    private String variantNameSnapshot;

    @Column(name = "UnitPrice", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "Quantity", nullable = false)
    private Integer quantity;

    @Column(name = "LineTotal", insertable = false, updatable = false)
    private BigDecimal lineTotal;

    /**
     * Fallback calculated line total for pre-insert / in-memory inspections.
     */
    public BigDecimal getEffectiveLineTotal() {
        if (lineTotal != null) {
            return lineTotal;
        }
        if (unitPrice != null && quantity != null) {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
        return BigDecimal.ZERO;
    }
}
