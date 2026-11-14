package com.petshop.dto.order;

import com.petshop.dto.cart.CartItemDto;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class CheckoutQuoteDto {
    private List<CartItemDto> items;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;
    private int selectedQuantity;
    private boolean checkoutAllowed;
    private String voucherCode;
    private String message;
}
