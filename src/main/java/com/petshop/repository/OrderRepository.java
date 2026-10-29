package com.petshop.repository;

import com.petshop.entity.Order;
import com.petshop.entity.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"customer"})
    Page<Order> findAll(org.springframework.data.jpa.domain.Specification<Order> spec, Pageable pageable);
    Optional<Order> findByOrderCode(String orderCode);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id IN :ids")
    List<Order> findWithItemsByIdIn(@Param("ids") List<Long> ids);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"customer"})
    Page<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"customer"})
    Page<Order> findByCustomerIdAndOrderStatusOrderByCreatedAtDesc(Long customerId, OrderStatus status, Pageable pageable);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"customer"})
    Page<Order> findByOrderStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"customer"})
    Page<Order> findAll(Pageable pageable);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.customer.id = :customerId AND o.voucher.id = :voucherId AND o.orderStatus <> com.petshop.entity.enums.OrderStatus.CANCELLED")
    long countVoucherUsageByCustomer(@Param("customerId") Long customerId, @Param("voucherId") Long voucherId);

    @Query("SELECT o FROM Order o " +
           "LEFT JOIN FETCH o.customer " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.variant v " +
           "LEFT JOIN FETCH v.product " +
           "WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT o FROM Order o " +
           "LEFT JOIN FETCH o.customer " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.variant v " +
           "WHERE o.orderCode = :orderCode")
    Optional<Order> findByOrderCodeWithDetails(@Param("orderCode") String orderCode);

    @Query("SELECT CAST(o.completedAt AS LocalDate) as revDate, COUNT(o) as orderCount, " +
           "SUM(o.subtotalAmount) as goodsTotal, SUM(o.discountAmount) as discountTotal, SUM(o.subtotalAmount - o.discountAmount) as netTotal, SUM(o.shippingFee), SUM(o.totalAmount) " +
           "FROM Order o WHERE o.orderStatus = com.petshop.entity.enums.OrderStatus.COMPLETED AND o.paymentStatus = com.petshop.entity.enums.PaymentStatus.PAID AND o.completedAt >= :startDate AND o.completedAt < :endDate " +
           "GROUP BY CAST(o.completedAt AS LocalDate) ORDER BY revDate ASC")
    List<Object[]> getRevenueStatistics(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(o.subtotalAmount - o.discountAmount), 0) FROM Order o WHERE o.orderStatus = :status")
    java.math.BigDecimal sumTotalAmountByOrderStatus(@Param("status") OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.subtotalAmount - o.discountAmount), 0) FROM Order o WHERE o.orderStatus = :status AND o.completedAt >= :start AND o.completedAt < :end")
    java.math.BigDecimal sumRevenueBetween(@Param("status") OrderStatus status, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    long countByOrderStatus(OrderStatus status);

    long countByCustomerId(Long customerId);

    long countByCustomerIdAndOrderStatus(Long customerId, OrderStatus status);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"customer"})
    List<Order> findTop5ByOrderByCreatedAtDesc();
}
