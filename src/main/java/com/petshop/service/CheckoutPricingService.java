package com.petshop.service;

import com.petshop.dto.cart.CartDto;
import com.petshop.dto.cart.CartItemDto;
import com.petshop.dto.order.CheckoutQuoteDto;
import com.petshop.dto.voucher.VoucherApplyResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/** Server-side preview. Final prices and availability are checked again in the order transaction. */
@Service
@RequiredArgsConstructor
public class CheckoutPricingService {
    private final VoucherService voucherService;
    private final com.petshop.config.ShippingFeeConfig shippingFeeConfig;

    public CheckoutQuoteDto quote(CartDto cart, Long customerId, String voucherCode) {
        List<CartItemDto> selected = cart.getItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsSelected())).toList();
        BigDecimal subtotal = selected.stream().map(CartItemDto::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal fee = selected.isEmpty() ? BigDecimal.ZERO : shippingFeeConfig.getShippingFee();
        boolean allowed = cart.isCheckoutAllowed();
        String message = selected.isEmpty() ? "Vui lòng chọn ít nhất một sản phẩm trong giỏ hàng."
                : allowed ? null : "Có sản phẩm không còn bán hoặc không đủ tồn. Vui lòng kiểm tra lại giỏ hàng.";
        String appliedCode = null;
        if (voucherCode != null && !voucherCode.isBlank() && !selected.isEmpty()) {
            if (voucherCode.length() > 50) {
                allowed = false;
                message = "Mã giảm giá không được quá 50 ký tự.";
            } else {
                VoucherApplyResultDto result = voucherService.validateAndCalculateDiscount(
                        voucherCode, customerId, subtotal);
                if (result.isValid()) {
                    discount = result.getDiscountAmount();
                    appliedCode = result.getVoucherCode();
                } else {
                    allowed = false;
                    message = result.getMessage();
                }
            }
        }
        return CheckoutQuoteDto.builder().items(selected).subtotal(subtotal)
                .discountAmount(discount).shippingFee(fee)
                .totalAmount(subtotal.subtract(discount).add(fee))
                .selectedQuantity(cart.getSelectedQuantity())
                .checkoutAllowed(allowed).voucherCode(appliedCode).message(message).build();
    }
}
