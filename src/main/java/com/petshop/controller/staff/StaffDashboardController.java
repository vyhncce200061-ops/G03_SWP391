package com.petshop.controller.staff;

import com.petshop.entity.enums.OrderStatus;
import com.petshop.service.OrderService;
import com.petshop.service.StockImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffDashboardController {

    private final OrderService orderService;
    private final StockImportService stockImportService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("pendingOrders", orderService.getOrdersForStaff(OrderStatus.PENDING_CONFIRMATION, PageRequest.of(0, 5)));
        model.addAttribute("lowStockVariants", stockImportService.getLowStockVariants());
        return "staff/dashboard";
    }
}
