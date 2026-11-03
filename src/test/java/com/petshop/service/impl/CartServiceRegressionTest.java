package com.petshop.service.impl;

import com.petshop.dto.cart.CartDto;
import com.petshop.entity.Cart;
import com.petshop.entity.CartItem;
import com.petshop.entity.Product;
import com.petshop.entity.ProductVariant;
import com.petshop.entity.User;
import com.petshop.entity.enums.ProductStatus;
import com.petshop.entity.enums.ProductVariantStatus;
import com.petshop.repository.CartItemRepository;
import com.petshop.repository.CartRepository;
import com.petshop.repository.ProductImageRepository;
import com.petshop.repository.ProductVariantRepository;
import com.petshop.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceRegressionTest {
    @Mock CartRepository carts;
    @Mock CartItemRepository items;
    @Mock ProductVariantRepository variants;
    @Mock ProductImageRepository images;
    @Mock UserRepository users;
    @Mock EntityManager entityManager;
    @Mock com.petshop.config.ShippingFeeConfig shippingFeeConfig;
    @InjectMocks CartServiceImpl service;

    User user;
    Cart cart;
    ProductVariant variant;
    CartItem item;

    @BeforeEach
    void setUp() {
        lenient().when(shippingFeeConfig.getShippingFee()).thenReturn(new BigDecimal("30000"));
        user = User.builder().id(1L).build();
        cart = Cart.builder().id(10L).user(user).build();
        variant = ProductVariant.builder().id(20L)
                .product(Product.builder().id(30L).name("Pet food").status(ProductStatus.ACTIVE).build())
                .status(ProductVariantStatus.ACTIVE).price(new BigDecimal("15000"))
                .stockQuantity(10).build();
        item = CartItem.builder().id(40L).cart(cart).variant(variant).quantity(2).isSelected(true).build();
        lenient().when(carts.findByUserId(1L)).thenReturn(Optional.of(cart));
        lenient().when(carts.findByUserIdForUpdate(1L)).thenReturn(Optional.of(cart));
        lenient().when(items.findById(40L)).thenReturn(Optional.of(item));
        lenient().when(items.findByCartId(10L)).thenReturn(List.of(item));
        lenient().when(variants.findById(20L)).thenReturn(Optional.of(variant));
    }

    @Test
    void zeroOrNegativeQuantityNeverDeletesOrSavesItem() {
        assertThatThrownBy(() -> service.updateItemQuantity(1L, 40L, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateItemQuantity(1L, 40L, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(item.getQuantity()).isEqualTo(2);
        verify(items, never()).delete(any());
        verify(items, never()).save(any());
    }

    @Test
    void overStockUpdateIsRejectedWithoutClampingOrSaving() {
        assertThatThrownBy(() -> service.updateItemQuantity(1L, 40L, 11))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("10");
        assertThat(item.getQuantity()).isEqualTo(2);
        verify(items, never()).save(any());
    }

    @Test
    void newAdditionOverStockIsRejectedInsteadOfSilentlyClamped() {
        assertThatThrownBy(() -> service.addItemToCart(1L, 20L, 11))
                .isInstanceOf(IllegalArgumentException.class);
        verify(items, never()).save(any());
    }

    @Test
    void mergingLargeQuantitiesCannotOverflowToNegative() {
        item.setQuantity(Integer.MAX_VALUE - 1);
        variant.setStockQuantity(Integer.MAX_VALUE);
        when(items.findByCartIdAndVariantId(10L, 20L)).thenReturn(Optional.of(item));
        assertThatThrownBy(() -> service.addItemToCart(1L, 20L, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(item.getQuantity()).isEqualTo(Integer.MAX_VALUE - 1);
        verify(items, never()).save(any());
    }

    @Test
    void changingQuantityChecksProductAsWellAsVariantStatus() {
        variant.getProduct().setStatus(ProductStatus.HIDDEN);
        assertThatThrownBy(() -> service.updateItemQuantity(1L, 40L, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addItemToCart(1L, 20L, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(item.getQuantity()).isEqualTo(2);
        verify(items, never()).save(any());
    }

    @Test
    void updatingOutOfStockItemPreservesItForRemoval() {
        variant.setStockQuantity(0);
        assertThatThrownBy(() -> service.updateItemQuantity(1L, 40L, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(item.getQuantity()).isEqualTo(2);
        verify(items, never()).save(any());
    }

    @Test
    void otherUsersItemsCannotBeChangedSelectedOrRemoved() {
        item.setCart(Cart.builder().id(99L).build());
        assertThatThrownBy(() -> service.updateItemQuantity(1L, 40L, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateItemSelection(1L, 40L, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.removeItem(1L, 40L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(items, never()).save(any());
        verify(items, never()).delete(any());
    }

    @Test
    void unavailableItemCanStillBeRemoved() {
        variant.setStockQuantity(0);
        variant.getProduct().setStatus(ProductStatus.DISCONTINUED);
        when(items.findByCartId(10L)).thenReturn(List.of());
        CartDto result = service.removeItem(1L, 40L);
        verify(items).delete(item);
        assertThat(result.getItems()).isEmpty();
        assertThat(result.isCheckoutAllowed()).isFalse();
    }

    @Test
    void selectingOnlyOneRowChargesOnlyThatRowAndOneFixedShippingFee() {
        CartItem second = CartItem.builder().id(41L).cart(cart).variant(variant)
                .quantity(3).isSelected(false).build();
        when(items.findByCartId(10L)).thenReturn(List.of(item, second));
        CartDto result = service.getCartForUser(1L);
        assertThat(result.getTotalQuantity()).isEqualTo(5);
        assertThat(result.getSelectedQuantity()).isEqualTo(2);
        assertThat(result.getSelectedItemCount()).isEqualTo(1);
        assertThat(result.getSubtotal()).isEqualByComparingTo("30000");
        assertThat(result.getShippingFee()).isEqualByComparingTo("30000");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("60000");
        assertThat(result.isCheckoutAllowed()).isTrue();
    }

    @Test
    void deselectingLastRowLeavesEmptyZeroCheckoutAndKeepsCartItems() {
        CartDto result = service.updateItemSelection(1L, 40L, false);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getSelectedQuantity()).isZero();
        assertThat(result.getSelectedItemCount()).isZero();
        assertThat(result.getSubtotal()).isEqualByComparingTo("0");
        assertThat(result.getShippingFee()).isEqualByComparingTo("0");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("0");
        assertThat(result.isCheckoutAllowed()).isFalse();
        verify(items).save(item);
    }

    @Test
    void selectedUnavailableItemBlocksCheckoutAndExplainsReason() {
        variant.getProduct().setStatus(ProductStatus.HIDDEN);
        CartDto result = service.getCartForUser(1L);
        assertThat(result.isCheckoutAllowed()).isFalse();
        assertThat(result.getItems().get(0).isAvailable()).isFalse();
        assertThat(result.getItems().get(0).getAvailabilityMessage()).contains("tạm dừng");
    }

    @Test
    void selectedQuantityAboveCurrentStockBlocksCheckout() {
        variant.setStockQuantity(1);
        CartDto result = service.getCartForUser(1L);
        assertThat(result.isCheckoutAllowed()).isFalse();
        assertThat(result.getItems().get(0).getAvailabilityMessage()).contains("giảm số lượng");
    }

    @Test
    void validQuantityUpdateLocksCartButDoesNotReserveOrSaveInventory() {
        CartDto result = service.updateItemQuantity(1L, 40L, 4);
        assertThat(result.getSelectedQuantity()).isEqualTo(4);
        assertThat(variant.getStockQuantity()).isEqualTo(10);
        verify(carts).findByUserIdForUpdate(1L);
        verify(items).save(item);
        verify(variants, never()).save(any());
    }

    @Test
    void readOfMissingCartDoesNotCreateOrLockAnything() {
        when(carts.findByUserId(1L)).thenReturn(Optional.empty());
        CartDto result = service.getCartForUser(1L);
        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotalAmount()).isEqualByComparingTo("0");
        verify(carts, never()).findByUserIdForUpdate(any());
        verify(carts, never()).save(any());
    }

    @Test
    void concurrentFirstAdditionUsesUserLockBeforeCreatingCart() {
        when(carts.findByUserIdForUpdate(1L)).thenReturn(Optional.empty());
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(carts.save(any())).thenReturn(cart);
        service.addItemToCart(1L, 20L, 1);
        verify(entityManager).lock(user, LockModeType.PESSIMISTIC_WRITE);
        verify(carts).save(any());
        assertThat(variant.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void largeCartQuantityCountersNeverBecomeNegative() {
        variant.setStockQuantity(Integer.MAX_VALUE);
        item.setQuantity(Integer.MAX_VALUE);
        CartItem second = CartItem.builder().id(41L).cart(cart).variant(variant)
                .quantity(Integer.MAX_VALUE).isSelected(true).build();
        when(items.findByCartId(10L)).thenReturn(List.of(item, second));
        CartDto result = service.getCartForUser(1L);
        assertThat(result.getTotalQuantity()).isEqualTo(Integer.MAX_VALUE);
        assertThat(result.getSelectedQuantity()).isEqualTo(Integer.MAX_VALUE);
        assertThat(service.getCartItemCount(1L)).isEqualTo(Integer.MAX_VALUE);
    }
}
