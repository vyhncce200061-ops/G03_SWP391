package com.petshop.repository;

import com.petshop.entity.OrderStatusHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
    List<OrderStatusHistory> findByOrderIdOrderByChangedAtAsc(Long orderId);

    @Query("SELECT COUNT(DISTINCT h.order.id) FROM OrderStatusHistory h WHERE h.changedBy.id = :staffId")
    long countDistinctOrdersByStaffId(@Param("staffId") Long staffId);

    long countByChangedById(Long staffId);

    @Query("SELECT h FROM OrderStatusHistory h JOIN FETCH h.order o WHERE h.changedBy.id = :staffId ORDER BY h.changedAt DESC")
    List<OrderStatusHistory> findRecentActivitiesByStaffId(@Param("staffId") Long staffId, Pageable pageable);
}
