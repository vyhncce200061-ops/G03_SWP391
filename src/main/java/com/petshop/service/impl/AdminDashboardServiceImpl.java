package com.petshop.service.impl;

import com.petshop.dto.admin.AdminDashboardDto;
import com.petshop.entity.Order;
import com.petshop.entity.enums.OrderStatus;
import com.petshop.repository.OrderRepository;
import com.petshop.repository.ProductRepository;
import com.petshop.repository.ProductVariantRepository;
import com.petshop.repository.UserRepository;
import com.petshop.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardDto getDashboardStats() {
        LocalDateTime startOfToday = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime endOfToday = LocalDate.now().plusDays(1).atStartOfDay();

        BigDecimal totalRevenue = orderRepository.sumTotalAmountByOrderStatus(OrderStatus.COMPLETED);
        if (totalRevenue == null) {
            totalRevenue = BigDecimal.ZERO;
        }

        BigDecimal todayRevenue = orderRepository.sumRevenueBetween(OrderStatus.COMPLETED, startOfToday, endOfToday);
        if (todayRevenue == null) {
            todayRevenue = BigDecimal.ZERO;
        }

        long totalOrders = orderRepository.count();
        long totalCustomers = userRepository.countByRoleCode("CUSTOMER");
        long totalProducts = productRepository.count();

        long todayOrders = orderRepository.countByCreatedAtBetween(startOfToday, endOfToday);
        long todayNewUsers = userRepository.countByCreatedAtBetween(startOfToday, endOfToday);
        long pendingOrdersCount = orderRepository.countByOrderStatus(OrderStatus.PENDING_CONFIRMATION);
        long lowStockCount = productVariantRepository.findLowStockVariants().size();

        List<Order> recentOrders = orderRepository.findTop5ByOrderByCreatedAtDesc();

        // 7-day revenue trend
        LocalDateTime sevenDaysAgo = LocalDateTime.of(LocalDate.now().minusDays(6), LocalTime.MIN);
        List<Object[]> stats = orderRepository.getRevenueStatistics(sevenDaysAgo, endOfToday);
        Map<String, BigDecimal> revMap = new HashMap<>();
        for (Object[] row : stats) {
            if (row[0] != null && row[4] != null) {
                BigDecimal val = (row[4] instanceof BigDecimal) ? (BigDecimal) row[4] : new BigDecimal(row[4].toString());
                revMap.put(row[0].toString(), val);
            }
        }

        List<String> chartLabels = new ArrayList<>();
        List<BigDecimal> chartData = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        for (int i = 6; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            chartLabels.add(d.format(fmt));
            BigDecimal rev = revMap.getOrDefault(d.toString(), BigDecimal.ZERO);
            chartData.add(rev);
        }

        return AdminDashboardDto.builder()
                .totalRevenue(totalRevenue)
                .totalOrders(totalOrders)
                .totalCustomers(totalCustomers)
                .totalProducts(totalProducts)
                .todayRevenue(todayRevenue)
                .todayOrders(todayOrders)
                .todayNewUsers(todayNewUsers)
                .pendingOrdersCount(pendingOrdersCount)
                .lowStockCount(lowStockCount)
                .recentOrders(recentOrders)
                .chartLabels(chartLabels)
                .chartData(chartData)
                .build();
    }
}
