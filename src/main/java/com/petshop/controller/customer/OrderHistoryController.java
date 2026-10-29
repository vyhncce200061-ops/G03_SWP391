package com.petshop.controller.customer;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.entity.Order;
import com.petshop.entity.OrderStatusHistory;
import com.petshop.entity.enums.OrderStatus;
import com.petshop.entity.enums.PaymentMethod;
import com.petshop.entity.enums.PaymentStatus;
import com.petshop.service.OrderService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/customer/orders")
@RequiredArgsConstructor
public class OrderHistoryController {

    private final OrderService orderService;

    @ModelAttribute("orderStatuses")
    public OrderStatus[] orderStatuses() {
        return OrderStatus.values();
    }

    @ModelAttribute("orderStatusLabels")
    public Map<String, String> orderStatusLabels() {
        return Map.of(
                "PENDING_CONFIRMATION", "Chờ xác nhận",
                "PENDING_PAYMENT", "Chờ thanh toán",
                "CONFIRMED", "Đã xác nhận",
                "PREPARING", "Đang chuẩn bị",
                "SHIPPING", "Đang giao hàng",
                "COMPLETED", "Đã hoàn thành",
                "CANCELLED", "Đã hủy");
    }

    @ModelAttribute("paymentStatusLabels")
    public Map<String, String> paymentStatusLabels() {
        return Map.of("UNPAID", "Chưa thanh toán", "PAID", "Đã thanh toán",
                "FAILED", "Thanh toán thất bại", "CANCELLED", "Đã hủy thanh toán");
    }

    @GetMapping
    public String listOrders(@AuthenticationPrincipal CustomUserDetails userDetails,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) String fromDate,
                             @RequestParam(required = false) String toDate,
                             @RequestParam(defaultValue = "0") String page,
                             @RequestParam(defaultValue = "10") String size,
                             Model model) {
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        try {
            OrderStatus orderStatus = parseStatus(status);
            LocalDate from = parseDate(fromDate);
            LocalDate to = parseDate(toDate);
            if (from != null && to != null && from.isAfter(to)) {
                throw new IllegalArgumentException("Ngày bắt đầu không được sau ngày kết thúc.");
            }
            int pageNumber = parseNumber(page, 0, Integer.MAX_VALUE, "Số trang không hợp lệ.");
            int pageSize = parseNumber(size, 1, 100, "Số đơn mỗi trang phải từ 1 đến 100.");
            if ((long) pageNumber * pageSize > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Số trang vượt quá giới hạn cho phép.");
            }
            Page<Order> orders = orderService.getCustomerOrders(userDetails.getId(), orderStatus,
                    from, to, PageRequest.of(pageNumber, pageSize));
            model.addAttribute("orders", orders);
            model.addAttribute("currentStatus", orderStatus);
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("orders", Page.empty(PageRequest.of(0, 10)));
        }
        return "customer/order-list";
    }

    @GetMapping("/{id}")
    public String orderDetail(@AuthenticationPrincipal CustomUserDetails userDetails,
                              @PathVariable Long id,
                              Model model,
                              HttpServletResponse response) {
        Order order;
        try {
            order = orderService.getOrderDetailForCustomer(id, userDetails.getId());
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "error/404";
        }
        model.addAttribute("order", order);
        model.addAttribute("currentCustomerId", userDetails.getId());
        model.addAttribute("statusHistory", order.getStatusHistories().stream()
                .sorted(Comparator.comparing(OrderStatusHistory::getChangedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(OrderStatusHistory::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList());
        boolean canCancelCod = order.getPaymentMethod() == PaymentMethod.COD
                && order.getOrderStatus() == OrderStatus.PENDING_CONFIRMATION
                && order.getPaymentStatus() == PaymentStatus.UNPAID;
        boolean canCancelVnpay = order.getPaymentMethod() == PaymentMethod.VNPAY
                && order.getOrderStatus() == OrderStatus.PENDING_PAYMENT
                && (order.getPaymentStatus() == PaymentStatus.UNPAID
                    || order.getPaymentStatus() == PaymentStatus.FAILED);
        model.addAttribute("canCancel", canCancelCod || canCancelVnpay);
        return "customer/order-detail";
    }

    @PostMapping("/{id}/cancel")
    public String cancelOrder(@AuthenticationPrincipal CustomUserDetails userDetails,
                              @PathVariable Long id,
                              @RequestParam(defaultValue = "") String reason,
                              RedirectAttributes redirectAttributes,
                              HttpServletResponse response) {
        try {
            orderService.getOrderDetailForCustomer(id, userDetails.getId());
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "error/404";
        }
        String cancelReason = reason.trim();
        if (cancelReason.isEmpty()) {
            cancelReason = "Khách hàng yêu cầu hủy đơn";
        }
        if (cancelReason.length() > 1000) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lý do hủy không được vượt quá 1000 ký tự.");
            return "redirect:/customer/orders/" + id;
        }
        try {
            orderService.customerCancelOrder(id, userDetails.getId(), cancelReason);
            redirectAttributes.addFlashAttribute("successMessage", "Hủy đơn hàng thành công!");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/customer/orders/" + id;
    }

    private OrderStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trạng thái đơn hàng không hợp lệ.");
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(value);
            // SQL Server datetime2 supports calendar years 1 through 9999.
            if (date.getYear() < 1 || date.getYear() > 9999) {
                throw new IllegalArgumentException("Ngày lọc phải thuộc khoảng năm 0001 đến 9999.");
            }
            return date;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Ngày lọc không hợp lệ. Vui lòng chọn ngày theo định dạng năm-tháng-ngày.");
        }
    }

    private int parseNumber(String value, int min, int max, String message) {
        try {
            int number = Integer.parseInt(value);
            if (number < min || number > max) {
                throw new IllegalArgumentException(message);
            }
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
    }
}
