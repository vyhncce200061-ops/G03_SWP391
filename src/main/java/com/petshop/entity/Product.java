package com.petshop.entity;

import com.petshop.entity.enums.PetType;
import com.petshop.entity.enums.ProductStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Product catalog root entity.
 * Mapped to table dbo.Products.
 */
@Entity
@Table(name = "Products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CategoryId", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BrandId")
    private Brand brand;

    @Enumerated(EnumType.STRING)
    @Column(name = "PetType", nullable = false, length = 10)
    @Builder.Default
    private PetType petType = PetType.BOTH;

    @Column(name = "Name", nullable = false, length = 250)
    private String name;

    @Column(name = "Slug", nullable = false, unique = true, length = 300)
    private String slug;

    @Column(name = "ShortDescription", length = 1000)
    private String shortDescription;

    @Column(name = "Description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "UsageInstructions", columnDefinition = "NVARCHAR(MAX)")
    private String usageInstructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    @Column(name = "IsFeatured", nullable = false)
    @Builder.Default
    private Boolean isFeatured = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CreatedByUserId", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UpdatedByUserId")
    private User updatedBy;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    @Builder.Default
    private List<ProductVariant> variants = new ArrayList<>();

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
        if (this.petType == null) {
            this.petType = PetType.BOTH;
        }
        if (this.status == null) {
            this.status = ProductStatus.ACTIVE;
        }
        if (this.isFeatured == null) {
            this.isFeatured = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
