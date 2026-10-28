package com.petshop.repository;

import com.petshop.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT i FROM OrderItem i WHERE i.id = :id")
    Optional<OrderItem> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    @org.springframework.data.jpa.repository.Query(value="SELECT i.variant.product.name, i.variant.product.id, SUM(i.quantity), SUM(i.unitPrice*i.quantity), COUNT(DISTINCT i.order.id) FROM OrderItem i WHERE i.order.orderStatus = com.petshop.entity.enums.OrderStatus.COMPLETED AND i.order.paymentStatus = com.petshop.entity.enums.PaymentStatus.PAID AND i.order.completedAt >= :start AND i.order.completedAt < :end GROUP BY i.variant.product.name, i.variant.product.id ORDER BY SUM(i.quantity) DESC, i.variant.product.id ASC",
        countQuery="SELECT COUNT(DISTINCT i.variant.product.id) FROM OrderItem i WHERE i.order.orderStatus = com.petshop.entity.enums.OrderStatus.COMPLETED AND i.order.paymentStatus = com.petshop.entity.enums.PaymentStatus.PAID AND i.order.completedAt >= :start AND i.order.completedAt < :end")
    org.springframework.data.domain.Page<Object[]> productSales(@org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
        @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end, org.springframework.data.domain.Pageable pageable);
    List<OrderItem> findByOrderId(Long orderId);
    Optional<OrderItem> findByOrderIdAndVariantId(Long orderId, Long variantId);
}
