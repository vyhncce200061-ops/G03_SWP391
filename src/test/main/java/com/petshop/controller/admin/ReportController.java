package com.petshop.controller.admin;

import com.petshop.repository.OrderRepository;
import com.petshop.service.StockImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Controller
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class ReportController {

    private final OrderRepository orderRepository;
    private final StockImportService stockImportService;

    @GetMapping("/revenue")
    public String revenueReport(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                Model model) {
        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Object[]> stats = orderRepository.getRevenueStatistics(startDateTime, endDateTime);
        model.addAttribute("revenueData", stats);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        return "admin/report-revenue";
    }

    @GetMapping("/stock")
    public String stockReport(Model model) {
        model.addAttribute("lowStockVariants", stockImportService.getLowStockVariants());
        return "admin/report-stock";
    }
}
