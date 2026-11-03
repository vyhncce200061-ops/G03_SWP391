package com.petshop.controller.admin;

import com.petshop.entity.ProductVariant;
import com.petshop.repository.OrderRepository;
import com.petshop.service.StockImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Controller
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class ReportController {

    private final OrderRepository orderRepository;
    private final StockImportService stockImportService;
    private final com.petshop.service.ReportService reportService;

    @GetMapping("/revenue")
    public String revenueReport(@RequestParam(required = false) String preset,
                                @RequestParam(defaultValue = "day") String groupBy,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                Model model) {
        LocalDate today = LocalDate.now();

        // Handle quick presets
        if ("today".equalsIgnoreCase(preset)) {
            startDate = today;
            endDate = today;
        } else if ("7days".equalsIgnoreCase(preset)) {
            startDate = today.minusDays(6);
            endDate = today;
        } else if ("30days".equalsIgnoreCase(preset)) {
            startDate = today.minusDays(29);
            endDate = today;
        } else if ("this_month".equalsIgnoreCase(preset)) {
            startDate = today.with(TemporalAdjusters.firstDayOfMonth());
            endDate = today;
        } else {
            if (startDate == null) {
                startDate = today.minusDays(29);
            }
            if (endDate == null) {
                endDate = today;
            }
        }

        LocalDate[] range = checkedRange(startDate, endDate, model);
        startDate = range[0]; endDate = range[1];

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        List<Object[]> stats = orderRepository.getRevenueStatistics(startDateTime, endDateTime);
        if (!java.util.Set.of("day", "month", "year").contains(groupBy)) groupBy = "day";
        Map<String,Object[]> grouped = new TreeMap<>();
        for (Object[] row : stats) {
            String date = row[0].toString();
            String key = "year".equals(groupBy) ? date.substring(0,4) : "month".equals(groupBy) ? date.substring(0,7) : date;
            Object[] sum = grouped.computeIfAbsent(key, k -> new Object[]{k,0L,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO});
            sum[1] = (Long)sum[1] + ((Number)row[1]).longValue();
            for (int i=2;i<7;i++) sum[i] = ((BigDecimal)sum[i]).add(new BigDecimal(row[i].toString()));
        }
        stats = new ArrayList<>(grouped.values());

        // Aggregate summary KPIs
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalGoods = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        long totalOrders = 0;

        Map<String, BigDecimal> dailyRevenueMap = new HashMap<>();

        for (Object[] row : stats) {
            if (row[0] != null) {
                String dateStr = row[0].toString();
                long count = (row[1] instanceof Number) ? ((Number) row[1]).longValue() : 0L;
                BigDecimal goods = (row[2] instanceof BigDecimal) ? (BigDecimal) row[2] : BigDecimal.ZERO;
                BigDecimal disc = (row[3] instanceof BigDecimal) ? (BigDecimal) row[3] : BigDecimal.ZERO;
                BigDecimal net = (row[4] instanceof BigDecimal) ? (BigDecimal) row[4] : BigDecimal.ZERO;

                totalOrders += count;
                totalGoods = totalGoods.add(goods);
                totalDiscount = totalDiscount.add(disc);
                totalRevenue = totalRevenue.add(net);

                dailyRevenueMap.put(dateStr, net);
            }
        }

        BigDecimal aov = (totalOrders > 0)
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Build continuous timeline for Chart.js
        List<String> chartLabels = new ArrayList<>();
        List<BigDecimal> chartData = new ArrayList<>();
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("dd/MM");

        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        if (daysBetween <= 90 && "day".equals(groupBy)) {
            for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                chartLabels.add(d.format(labelFmt));
                BigDecimal val = dailyRevenueMap.getOrDefault(d.toString(), BigDecimal.ZERO);
                chartData.add(val);
            }
        } else {
            // For longer ranges, only plot dates with stats
            for (Object[] row : stats) {
                if (row[0] != null) {
                    chartLabels.add(row[0].toString());
                    chartData.add((BigDecimal) row[4]);
                }
            }
        }

        model.addAttribute("revenueData", stats);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("preset", preset);

        // KPI metrics
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("totalGoods", totalGoods);
        model.addAttribute("totalDiscount", totalDiscount);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("aov", aov);
        model.addAttribute("groupBy", groupBy);
        model.addAttribute("totalShipping", stats.stream().map(r -> (BigDecimal)r[5]).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("totalCollected", stats.stream().map(r -> (BigDecimal)r[6]).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("topSelling", reportService.topSelling(startDateTime, endDate.plusDays(1).atStartOfDay()));

        // Chart data
        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartData", chartData);

        return "admin/report-revenue";
    }

    @GetMapping("/stock")
    public String stockReport(Model model) {
        List<ProductVariant> lowStockVariants = stockImportService.getLowStockVariants();

        long outOfStockCount = lowStockVariants.stream()
                .filter(v -> v.getStockQuantity() == null || v.getStockQuantity() <= 0)
                .count();

        long warningCount = lowStockVariants.stream()
                .filter(v -> v.getStockQuantity() != null && v.getStockQuantity() > 0 && v.getStockQuantity() <= v.getLowStockThreshold())
                .count();

        model.addAttribute("lowStockVariants", lowStockVariants);
        model.addAttribute("totalLowStock", lowStockVariants.size());
        model.addAttribute("outOfStockCount", outOfStockCount);
        model.addAttribute("warningCount", warningCount);
        var reconciliation = reportService.stockReconciliation();
        model.addAttribute("reconciliation", reconciliation);
        model.addAttribute("mismatchCount", reconciliation.stream().filter(r -> r.getDifference()!=0).count());

        return "admin/report-stock";
    }

    @GetMapping("/reviews")
    public String reviewReport(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate, Model model) {
        if (endDate==null) endDate=LocalDate.now();
        if (startDate==null) startDate=endDate.minusDays(29);
        LocalDate[] range = checkedRange(startDate,endDate,model);startDate=range[0];endDate=range[1];
        model.addAttribute("startDate",startDate); model.addAttribute("endDate",endDate);
        model.addAttribute("reviewStats",reportService.reviewsByProduct(startDate.atStartOfDay(),endDate.plusDays(1).atStartOfDay()));
        return "admin/report-reviews";
    }

    @GetMapping("/product-sales")
    public String productSales(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue="0") int page, Model model) {
        if (endDate == null) endDate=LocalDate.now();
        if (startDate == null) startDate=endDate.minusDays(29);
        LocalDate[] range=checkedRange(startDate,endDate,model);
        model.addAttribute("startDate",range[0]);model.addAttribute("endDate",range[1]);
        model.addAttribute("sales",reportService.productSales(range[0].atStartOfDay(),range[1].plusDays(1).atStartOfDay(),
                org.springframework.data.domain.PageRequest.of(Math.max(0,page),20)));
        return "admin/report-product-sales";
    }

    private LocalDate[] checkedRange(LocalDate start, LocalDate end, Model model) {
        if (start.getYear()<1 || end.getYear()<1 || start.getYear()>9999 || !end.isBefore(LocalDate.of(9999,12,31)) || start.isAfter(end)) {
            model.addAttribute("errorMessage","Khoảng ngày không hợp lệ. Ngày bắt đầu phải trước hoặc bằng ngày kết thúc, trong phạm vi năm 1–9999 (ngày cuối trước 31/12/9999). Đang hiển thị 30 ngày gần nhất.");
            end=LocalDate.now();start=end.minusDays(29);
        }
        return new LocalDate[]{start,end};
    }
}
