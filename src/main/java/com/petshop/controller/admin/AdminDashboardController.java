package com.petshop.controller.admin;

import com.petshop.dto.admin.AdminDashboardDto;
import com.petshop.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        AdminDashboardDto stats = adminDashboardService.getDashboardStats();
        model.addAttribute("stats", stats);
        return "admin/dashboard";
    }
}
