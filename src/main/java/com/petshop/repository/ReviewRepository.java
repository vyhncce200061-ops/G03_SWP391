package com.petshop.repository;

import com.petshop.entity.Review;
import com.petshop.entity.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Review r WHERE r.id = :id")
    Optional<Review> findByIdForUpdate(@Param("id") Long id);
    Optional<Review> findByOrderItemId(Long orderItemId);
    boolean existsByOrderItemId(Long orderItemId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"orderItem", "orderItem.order"})
    @Query("SELECT r FROM Review r " +
           "JOIN r.orderItem oi " +
           "JOIN oi.variant pv " +
           "WHERE pv.product.id = :productId AND r.status = com.petshop.entity.enums.ReviewStatus.VISIBLE " +
           "ORDER BY r.createdAt DESC, r.id DESC")
    Page<Review> findVisibleReviewsByProductId(@Param("productId") Long productId, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"orderItem", "orderItem.order"})
    Page<Review> findByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId, Pageable pageable);

    @Query("SELECT AVG(r.rating * 1.0) FROM Review r " +
           "JOIN r.orderItem oi JOIN oi.variant pv " +
           "WHERE pv.product.id = :productId AND r.status = com.petshop.entity.enums.ReviewStatus.VISIBLE")
    Double calculateAverageRating(@Param("productId") Long productId);

    @Query("SELECT COUNT(r) FROM Review r JOIN r.orderItem oi JOIN oi.variant pv " +
           "WHERE pv.product.id = :productId AND r.status = com.petshop.entity.enums.ReviewStatus.VISIBLE")
    Long countVisibleReviews(@Param("productId") Long productId);

    @Query(value = "SELECT r FROM Review r " +
           "JOIN FETCH r.customer c " +
           "JOIN FETCH r.orderItem oi " +
           "JOIN FETCH oi.variant pv " +
           "JOIN FETCH pv.product p " +
           "WHERE (:rating IS NULL OR r.rating = :rating) " +
           "AND (:status IS NULL OR r.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(c.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(r.comment) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY r.createdAt DESC, r.id DESC",
           countQuery = "SELECT COUNT(r) FROM Review r " +
           "JOIN r.customer c " +
           "JOIN r.orderItem oi " +
           "JOIN oi.variant pv " +
           "JOIN pv.product p " +
           "WHERE (:rating IS NULL OR r.rating = :rating) " +
           "AND (:status IS NULL OR r.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(c.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(r.comment) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Review> searchReviewsAdmin(@Param("keyword") String keyword,
                                    @Param("rating") Integer rating,
                                    @Param("status") ReviewStatus status,
                                    Pageable pageable);

    long countByStatus(ReviewStatus status);

    @Query("SELECT COALESCE(AVG(r.rating * 1.0), 0.0) FROM Review r")
    Double getOverallAverageRating();
}
