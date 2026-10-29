package com.petshop.dto.cart;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartDto {
    private Long id;
    private Long userId;
    @Builder.Default
    private List<CartItemDto> items = new ArrayList<>();
    private BigDecimal subtotal;
    private int totalQuantity;
    private int selectedQuantity;
    private int selectedItemCount;
    private boolean checkoutAllowed;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;
}
