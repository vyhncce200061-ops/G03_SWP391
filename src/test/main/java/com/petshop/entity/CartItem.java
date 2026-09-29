package com.petshop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Line item in a customer shopping cart.
 * Mapped to table dbo.CartItems.
 */
@Entity
@Table(name = "CartItems", uniqueConstraints = {
    @UniqueConstraint(name = "UQ_CartItems_CartVariant", columnNames = {"CartId", "VariantId"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CartId", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VariantId", nullable = false)
    private ProductVariant variant;

    @Column(name = "Quantity", nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Column(name = "IsSelected", nullable = false)
    @Builder.Default
    private Boolean isSelected = true;

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
        if (this.quantity == null) {
            this.quantity = 1;
        }
        if (this.isSelected == null) {
            this.isSelected = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
