package com.petshop.entity;

import com.petshop.entity.enums.ProductVariantStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Sellable Stock Keeping Unit (SKU) entity.
 * Mapped to table dbo.ProductVariants.
 */
@Entity
@Table(name = "ProductVariants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ProductId", nullable = false)
    private Product product;

    @Column(name = "SkuCode", nullable = false, unique = true, length = 80)
    private String skuCode;

    @Column(name = "VariantName", nullable = false, length = 200)
    private String variantName;

    @Column(name = "Price", nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @Column(name = "OriginalPrice", precision = 18, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "StockQuantity", nullable = false)
    @Builder.Default
    private Integer stockQuantity = 0;

    @Column(name = "LowStockThreshold", nullable = false)
    @Builder.Default
    private Integer lowStockThreshold = 5;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private ProductVariantStatus status = ProductVariantStatus.ACTIVE;

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
        if (this.stockQuantity == null) {
            this.stockQuantity = 0;
        }
        if (this.lowStockThreshold == null) {
            this.lowStockThreshold = 5;
        }
        if (this.status == null) {
            this.status = ProductVariantStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
