package com.petshop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Inbound stock shipment header entity from vendor.
 * Mapped to table dbo.StockImports.
 */
@Entity
@Table(name = "StockImports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "ImportCode", nullable = false, unique = true, length = 40)
    private String importCode;

    @Column(name = "SupplierName", length = 200)
    private String supplierName;

    @Column(name = "Note", length = 1000)
    private String note;

    @Column(name = "ImportedAt", nullable = false)
    private LocalDateTime importedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CreatedByUserId", nullable = false)
    private User createdBy;

    @OneToMany(mappedBy = "stockImport", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StockImportItem> items = new ArrayList<>();

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.importedAt == null) {
            this.importedAt = now;
        }
    }
}
