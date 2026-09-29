package com.petshop.controller.staff;

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
@RequestMapping("/staff/orders")
@RequiredArgsConstructor
public class OrderProcessController {

    private final OrderService orderService;

    @GetMapping
    public String listOrders(@RequestParam(required = false) OrderStatus status,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "10") int size,
                             Model model) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderService.getOrdersForStaff(status, pageable);

        model.addAttribute("orders", orders);
        model.addAttribute("currentStatus", status);
        return "staff/order-list";
    }

    @GetMapping("/{id}")
    public String orderDetail(@PathVariable Long id, Model model) {
        Order order = orderService.getOrderDetailForStaff(id);
        model.addAttribute("order", order);
        return "staff/order-detail";
    }

    @PostMapping("/{id}/confirm")
    public String confirmOrder(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @PathVariable Long id,
                               RedirectAttributes redirectAttributes) {
        orderService.confirmOrder(id, userDetails.getId());
        redirectAttributes.addFlashAttribute("successMessage", "Đã xác nhận đơn hàng thành công!");
        return "redirect:/staff/orders/" + id;
    }

    @PostMapping("/{id}/prepare")
    public String markPreparing(@AuthenticationPrincipal CustomUserDetails userDetails,
                                @PathVariable Long id,
                                RedirectAttributes redirectAttributes) {
        orderService.markPreparing(id, userDetails.getId());
        redirectAttributes.addFlashAttribute("successMessage", "Đã chuyển đơn hàng sang trạng thái đóng gói!");
        return "redirect:/staff/orders/" + id;
    }

    @PostMapping("/{id}/ship")
    public String markShipping(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @PathVariable Long id,
                               RedirectAttributes redirectAttributes) {
        orderService.markShipping(id, userDetails.getId());
        redirectAttributes.addFlashAttribute("successMessage", "Đã bàn giao đơn hàng cho đơn vị vận chuyển!");
        return "redirect:/staff/orders/" + id;
    }

    @PostMapping("/{id}/complete")
    public String markCompleted(@AuthenticationPrincipal CustomUserDetails userDetails,
                                @PathVariable Long id,
                                RedirectAttributes redirectAttributes) {
        orderService.markCompleted(id, userDetails.getId());
        redirectAttributes.addFlashAttribute("successMessage", "Đã hoàn tất đơn hàng và thu tiền COD thành công!");
        return "redirect:/staff/orders/" + id;
    }

    @PostMapping("/{id}/reject")
    public String rejectOrder(@AuthenticationPrincipal CustomUserDetails userDetails,
                              @PathVariable Long id,
                              @RequestParam String reason,
                              RedirectAttributes redirectAttributes) {
        try {
            orderService.staffRejectOrder(id, userDetails.getId(), reason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã từ chối đơn hàng và hoàn tồn kho!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/staff/orders/" + id;
    }
}
