package com.petshop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Product media image entity.
 * Mapped to table dbo.ProductImages.
 */
@Entity
@Table(name = "ProductImages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ProductId", nullable = false)
    private Product product;

    @Column(name = "ImageUrl", nullable = false, length = 1000)
    private String imageUrl;

    @Column(name = "AltText", length = 300)
    private String altText;

    @Column(name = "SortOrder", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "IsPrimary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.sortOrder == null) {
            this.sortOrder = 0;
        }
        if (this.isPrimary == null) {
            this.isPrimary = false;
        }
    }
}
