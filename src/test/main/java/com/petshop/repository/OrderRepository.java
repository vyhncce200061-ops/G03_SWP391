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
    Optional<Order> findByOrderCode(String orderCode);

    Page<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);
    Page<Order> findByCustomerIdAndOrderStatusOrderByCreatedAtDesc(Long customerId, OrderStatus status, Pageable pageable);
    Page<Order> findByOrderStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.customer.id = :customerId AND o.voucher.id = :voucherId AND o.orderStatus <> com.petshop.entity.enums.OrderStatus.CANCELLED")
    long countVoucherUsageByCustomer(@Param("customerId") Long customerId, @Param("voucherId") Long voucherId);

    @Query("SELECT o FROM Order o " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.variant v " +
           "LEFT JOIN FETCH v.product " +
           "WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT o FROM Order o " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.variant v " +
           "WHERE o.orderCode = :orderCode")
    Optional<Order> findByOrderCodeWithDetails(@Param("orderCode") String orderCode);

    @Query("SELECT CAST(o.completedAt AS LocalDate) as revDate, COUNT(o) as orderCount, " +
           "SUM(o.subtotalAmount) as goodsTotal, SUM(o.discountAmount) as discountTotal, SUM(o.totalAmount) as netTotal " +
           "FROM Order o WHERE o.orderStatus = com.petshop.entity.enums.OrderStatus.COMPLETED AND o.completedAt BETWEEN :startDate AND :endDate " +
           "GROUP BY CAST(o.completedAt AS LocalDate) ORDER BY revDate ASC")
    List<Object[]> getRevenueStatistics(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}
