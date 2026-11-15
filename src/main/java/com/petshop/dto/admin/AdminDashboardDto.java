package com.petshop.dto.admin;

import com.petshop.entity.Order;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDashboardDto {

    private BigDecimal totalRevenue;
    private long totalOrders;
    private long totalCustomers;
    private long totalProducts;

    private BigDecimal todayRevenue;
    private long todayOrders;
    private long todayNewUsers;

    private long pendingOrdersCount;
    private long lowStockCount;

    private List<Order> recentOrders;
    private List<String> chartLabels;
    private List<BigDecimal> chartData;
}
