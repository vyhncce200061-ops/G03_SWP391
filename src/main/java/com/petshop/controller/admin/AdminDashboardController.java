package com.petshop.controller.admin;

import com.petshop.repository.OrderRepository;
import com.petshop.repository.ProductRepository;
import com.petshop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("totalOrders", orderRepository.count());
        model.addAttribute("totalProducts", productRepository.count());
        return "admin/dashboard";
    }
}
