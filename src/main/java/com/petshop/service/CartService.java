package com.petshop.service;

import com.petshop.dto.cart.CartDto;
import java.util.Optional;

public interface CartService {
    CartDto getCartForUser(Long userId);
    CartDto addItemToCart(Long userId, Long variantId, int quantity);
    Optional<String> addItemForReorder(Long userId, Long variantId);
    CartDto updateItemQuantity(Long userId, Long cartItemId, int newQuantity);
    CartDto updateItemSelection(Long userId, Long cartItemId, boolean selected);
    CartDto removeItem(Long userId, Long cartItemId);
    void clearCart(Long userId);
    int getCartItemCount(Long userId);
    void selectItem(Long userId, Long itemId, boolean selected);
}
