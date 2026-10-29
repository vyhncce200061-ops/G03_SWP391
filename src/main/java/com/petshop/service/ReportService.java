package com.petshop.service;

import com.petshop.dto.admin.StockReportRow;
import com.petshop.repository.ProductVariantRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {
    private final EntityManager em;
    private final ProductVariantRepository variants;
    private final com.petshop.repository.OrderItemRepository items;

    public org.springframework.data.domain.Page<Object[]> productSales(LocalDateTime start, LocalDateTime end, org.springframework.data.domain.Pageable page) {
        return items.productSales(start,end,page);
    }

    public List<StockReportRow> stockReconciliation() {
        Map<Long,Object[]> imported = new HashMap<>();
        for (Object[] row : em.createQuery("SELECT i.variant.id, SUM(i.quantity), SUM(i.unitCost * i.quantity), SUM(CASE WHEN i.unitCost IS NULL THEN 1 ELSE 0 END) FROM StockImportItem i GROUP BY i.variant.id", Object[].class).getResultList())
            imported.put(((Number)row[0]).longValue(), row);
        Map<Long,Long> held = new HashMap<>();
        for (Object[] row : em.createQuery("SELECT i.variant.id, SUM(i.quantity) FROM OrderItem i WHERE i.order.orderStatus <> com.petshop.entity.enums.OrderStatus.CANCELLED GROUP BY i.variant.id", Object[].class).getResultList())
            held.put(((Number)row[0]).longValue(), ((Number)row[1]).longValue());
        List<StockReportRow> rows = new ArrayList<>();
        for (var v : variants.findAll()) {
            Object[] in = imported.get(v.getId());
            long quantity = in == null ? 0 : ((Number)in[1]).longValue();
            long sold = held.getOrDefault(v.getId(),0L);
            BigDecimal value = null;
            if (quantity > 0 && v.getStockQuantity() == quantity - sold && ((Number)in[3]).longValue() == 0 && in[2] != null)
                value = new BigDecimal(in[2].toString()).multiply(BigDecimal.valueOf(v.getStockQuantity())).divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
            rows.add(new StockReportRow(v.getSkuCode(),v.getProduct().getName(),v.getStockQuantity(),quantity,sold,quantity-sold,v.getStockQuantity()-(quantity-sold),value));
        }
        rows.sort(Comparator.comparing(StockReportRow::getSku));
        return rows;
    }

    public List<Object[]> topSelling(LocalDateTime start, LocalDateTime end) {
        return em.createQuery("SELECT i.variant.product.name, i.variant.product.id, SUM(i.quantity), SUM(i.unitPrice*i.quantity) FROM OrderItem i WHERE i.order.orderStatus = com.petshop.entity.enums.OrderStatus.COMPLETED AND i.order.paymentStatus = com.petshop.entity.enums.PaymentStatus.PAID AND i.order.completedAt >= :start AND i.order.completedAt < :end GROUP BY i.variant.product.name, i.variant.product.id ORDER BY SUM(i.quantity) DESC, i.variant.product.id ASC",Object[].class)
                .setParameter("start",start).setParameter("end",end).setMaxResults(10).getResultList();
    }

    public List<Object[]> reviewsByProduct(LocalDateTime start, LocalDateTime end) {
        return em.createQuery("SELECT r.orderItem.variant.product.name, COUNT(r), AVG(r.rating * 1.0), SUM(CASE WHEN r.status = com.petshop.entity.enums.ReviewStatus.VISIBLE THEN 1 ELSE 0 END), SUM(CASE WHEN r.status = com.petshop.entity.enums.ReviewStatus.HIDDEN THEN 1 ELSE 0 END) FROM Review r WHERE r.createdAt >= :start AND r.createdAt < :end GROUP BY r.orderItem.variant.product.name, r.orderItem.variant.product.id ORDER BY COUNT(r) DESC",Object[].class)
                .setParameter("start",start).setParameter("end",end).getResultList();
    }
}
