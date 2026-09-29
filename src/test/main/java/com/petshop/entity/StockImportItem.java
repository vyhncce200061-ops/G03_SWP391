package com.petshop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Detailed line items for an inbound stock shipment.
 * Mapped to table dbo.StockImportItems.
 */
@Entity
@Table(name = "StockImportItems", uniqueConstraints = {
    @UniqueConstraint(name = "UQ_SII_ImportVariant", columnNames = {"ImportId", "VariantId"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockImportItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ImportId", nullable = false)
    private StockImport stockImport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VariantId", nullable = false)
    private ProductVariant variant;

    @Column(name = "Quantity", nullable = false)
    private Integer quantity;

    @Column(name = "UnitCost", precision = 18, scale = 2)
    private BigDecimal unitCost;
}
