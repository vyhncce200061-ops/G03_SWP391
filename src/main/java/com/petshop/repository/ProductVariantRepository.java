package com.petshop.repository;

import com.petshop.entity.ProductVariant;
import com.petshop.entity.enums.ProductVariantStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    Optional<ProductVariant> findBySkuCode(String skuCode);
    boolean existsBySkuCode(String skuCode);

    List<ProductVariant> findByProductIdAndStatus(Long productId, ProductVariantStatus status);
    List<ProductVariant> findByProductId(Long productId);

    @Query("SELECT pv FROM ProductVariant pv " +
           "JOIN FETCH pv.product p " +
           "WHERE pv.status = com.petshop.entity.enums.ProductVariantStatus.ACTIVE " +
           "AND p.status <> com.petshop.entity.enums.ProductStatus.DISCONTINUED " +
           "AND pv.stockQuantity <= pv.lowStockThreshold")
    List<ProductVariant> findLowStockVariants();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.id = :id")
    Optional<ProductVariant> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("UPDATE ProductVariant pv SET pv.stockQuantity = pv.stockQuantity - :quantity, pv.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE pv.id = :id AND pv.status = com.petshop.entity.enums.ProductVariantStatus.ACTIVE AND pv.stockQuantity >= :quantity")
    int deductStockAtomic(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying
    @Query("UPDATE ProductVariant pv SET pv.stockQuantity = pv.stockQuantity + :quantity, pv.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE pv.id = :id")
    int restoreStock(@Param("id") Long id, @Param("quantity") int quantity);
}
