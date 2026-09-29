package com.petshop.repository;

import com.petshop.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    Optional<Review> findByOrderItemId(Long orderItemId);
    boolean existsByOrderItemId(Long orderItemId);

    @Query("SELECT r FROM Review r " +
           "JOIN r.orderItem oi " +
           "JOIN oi.variant pv " +
           "WHERE pv.product.id = :productId AND r.status = com.petshop.entity.enums.ReviewStatus.VISIBLE " +
           "ORDER BY r.createdAt DESC")
    Page<Review> findVisibleReviewsByProductId(@Param("productId") Long productId, Pageable pageable);

    Page<Review> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    @Query("SELECT CAST(AVG(CAST(r.rating AS double)) AS double) FROM Review r " +
           "JOIN r.orderItem oi JOIN oi.variant pv " +
           "WHERE pv.product.id = :productId AND r.status = com.petshop.entity.enums.ReviewStatus.VISIBLE")
    Double calculateAverageRating(@Param("productId") Long productId);

    @Query("SELECT COUNT(r) FROM Review r JOIN r.orderItem oi JOIN oi.variant pv " +
           "WHERE pv.product.id = :productId AND r.status = com.petshop.entity.enums.ReviewStatus.VISIBLE")
    Long countVisibleReviews(@Param("productId") Long productId);
}
