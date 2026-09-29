package com.petshop.service;

import com.petshop.dto.cart.CartDto;

public interface CartService {
    CartDto getCartForUser(Long userId);
    CartDto addItemToCart(Long userId, Long variantId, int quantity);
    CartDto updateItemQuantity(Long userId, Long cartItemId, int newQuantity);
    CartDto removeItem(Long userId, Long cartItemId);
    void clearCart(Long userId);
    int getCartItemCount(Long userId);
}
