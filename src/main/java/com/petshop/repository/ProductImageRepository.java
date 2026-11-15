package com.petshop.repository;

import com.petshop.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    interface Thumbnail {
        Long getOwnerId();
        String getImageUrl();
    }

    @Query("SELECT i.product.id AS ownerId, i.imageUrl AS imageUrl FROM ProductImage i " +
           "WHERE i.product.id IN :ids ORDER BY i.isPrimary DESC, i.sortOrder ASC, i.id ASC")
    List<Thumbnail> productThumbnails(@Param("ids") java.util.Collection<Long> ids);

    @Query("SELECT item.id AS ownerId, image.imageUrl AS imageUrl FROM OrderItem item " +
           "LEFT JOIN item.variant.product.images image WHERE item.id IN :ids " +
           "ORDER BY image.isPrimary DESC, image.sortOrder ASC, image.id ASC")
    List<Thumbnail> orderItemThumbnails(@Param("ids") java.util.Collection<Long> ids);

    List<ProductImage> findByProductIdOrderBySortOrderAsc(Long productId);
    Optional<ProductImage> findByProductIdAndIsPrimaryTrue(Long productId);

    @Modifying
    @Query("UPDATE ProductImage pi SET pi.isPrimary = false WHERE pi.product.id = :productId")
    void resetPrimaryImage(@Param("productId") Long productId);
}
