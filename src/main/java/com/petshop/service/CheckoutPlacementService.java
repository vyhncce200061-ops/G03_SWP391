package com.petshop.service;

import com.petshop.dto.order.CheckoutSubmitDto;
import com.petshop.entity.Order;
import com.petshop.entity.enums.PaymentMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keep URL generation and order placement atomic if payment configuration/data is invalid. */
@Service
@RequiredArgsConstructor
public class CheckoutPlacementService {
    private final OrderService orderService;
    private final VnPayService vnPayService;

    @Transactional
    public Placement place(Long customerId, CheckoutSubmitDto form, String clientIp) {
        if (form.getPaymentMethod() == PaymentMethod.VNPAY && !vnPayService.isConfigured()) {
            throw new IllegalStateException("VNPay chưa được cấu hình. Vui lòng chọn COD.");
        }
        Order order = orderService.checkoutOrder(customerId, form);
        String paymentUrl = order.getPaymentMethod() == PaymentMethod.VNPAY
                ? vnPayService.createPaymentUrl(order, clientIp) : null;
        return new Placement(order, paymentUrl);
    }

    public record Placement(Order order, String paymentUrl) { }
}
