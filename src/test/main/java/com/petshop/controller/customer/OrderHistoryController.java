package com.petshop.controller.customer;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.entity.Order;
import com.petshop.entity.enums.OrderStatus;
import com.petshop.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customer/orders")
@RequiredArgsConstructor
public class OrderHistoryController {

    private final OrderService orderService;

    @GetMapping
    public String listOrders(@AuthenticationPrincipal CustomUserDetails userDetails,
                             @RequestParam(required = false) OrderStatus status,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "10") int size,
                             Model model) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderService.getCustomerOrders(userDetails.getId(), status, pageable);

        model.addAttribute("orders", orders);
        model.addAttribute("currentStatus", status);
        return "customer/order-list";
    }

    @GetMapping("/{id}")
    public String orderDetail(@AuthenticationPrincipal CustomUserDetails userDetails,
                              @PathVariable Long id,
                              Model model) {
        Order order = orderService.getOrderDetailForCustomer(id, userDetails.getId());
        model.addAttribute("order", order);
        return "customer/order-detail";
    }

    @PostMapping("/{id}/cancel")
    public String cancelOrder(@AuthenticationPrincipal CustomUserDetails userDetails,
                              @PathVariable Long id,
                              @RequestParam(defaultValue = "Khách hàng yêu cầu hủy đơn") String reason,
                              RedirectAttributes redirectAttributes) {
        try {
            orderService.customerCancelOrder(id, userDetails.getId(), reason);
            redirectAttributes.addFlashAttribute("successMessage", "Hủy đơn hàng thành công!");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/customer/orders/" + id;
    }
}
