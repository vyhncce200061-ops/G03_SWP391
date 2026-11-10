package com.petshop.controller.customer;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.service.VnPayService;
import com.petshop.service.VNPayReturnWaitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class VnPayController {
    private final VnPayService vnPayService;
    private final VNPayReturnWaitService vnPayReturnWaitService;

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
    public DeferredResult<ModelAndView> paymentReturn(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails principal,
            RedirectAttributes attributes
    ) {
        if (principal == null) {
            return completed("redirect:/login");
        }

        Optional<Long> orderId = vnPayService.findOwnedOrderIdForReturn(
                request.getParameterMap(),
                principal.getId()
        );

        if (orderId.isEmpty()) {
            attributes.addFlashAttribute(
                    "errorMessage",
                    "Không thể xác minh kết quả thanh toán."
            );
            return completed("redirect:/customer/orders");
        }

        // Chỉ sử dụng mã giao dịch sau khi toàn bộ callback
        // đã qua bước xác minh chữ ký và quyền sở hữu bên trên.
        String transactionCode = request.getParameter("vnp_TxnRef");

        return vnPayReturnWaitService.waitForResult(
                orderId.get(),
                principal.getId(),
                transactionCode
        );
    }

    private DeferredResult<ModelAndView> completed(String viewName) {
        DeferredResult<ModelAndView> result = new DeferredResult<>();
        result.setResult(new ModelAndView(viewName));
        return result;
    }


}
