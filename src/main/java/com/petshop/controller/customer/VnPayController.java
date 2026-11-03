package com.petshop.controller.customer;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.service.VnPayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class VnPayController {
    private final VnPayService vnPayService;

    @GetMapping("/payment/vnpay-ipn")
    @ResponseBody
    public Map<String, String> ipn(HttpServletRequest request) {
        try {
            return vnPayService.processIpn(request.getParameterMap());
        } catch (RuntimeException e) {
            // A failed database transaction rolls back; VNPay may safely retry this IPN.
            return Map.of("RspCode", "99", "Message", "Unable to process callback");
        }
    }

    @GetMapping("/payment/vnpay-return")
    public String paymentReturn(HttpServletRequest request,
                                @AuthenticationPrincipal CustomUserDetails principal,
                                RedirectAttributes attributes) {
        // /payment/** is public for VNPay's IPN; the browser page still requires an owner.
        if (principal == null) return "redirect:/login";
        Optional<Long> orderId = vnPayService.findOwnedOrderIdForReturn(request.getParameterMap(), principal.getId());
        if (orderId.isEmpty()) {
            attributes.addFlashAttribute("errorMessage", "Không thể xác minh kết quả thanh toán.");
            return "redirect:/customer/orders";
        }
        attributes.addFlashAttribute("successMessage",
                "Đã nhận kết quả từ VNPay. Trạng thái chính thức của đơn được cập nhật sau khi hệ thống nhận IPN hợp lệ.");
        return "redirect:/customer/orders/" + orderId.get();
    }
}
