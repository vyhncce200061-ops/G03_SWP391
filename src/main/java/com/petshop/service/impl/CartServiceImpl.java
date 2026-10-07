package com.petshop.service.impl;

import com.petshop.dto.cart.CartDto;
import com.petshop.dto.cart.CartItemDto;
import com.petshop.entity.Cart;
import com.petshop.entity.CartItem;
import com.petshop.entity.ProductImage;
import com.petshop.entity.ProductVariant;
import com.petshop.entity.User;
import com.petshop.entity.enums.ProductVariantStatus;
import com.petshop.repository.CartItemRepository;
import com.petshop.repository.CartRepository;
import com.petshop.repository.ProductImageRepository;
import com.petshop.repository.ProductVariantRepository;
import com.petshop.repository.UserRepository;
import com.petshop.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CartDto getCartForUser(Long userId) {
        Cart cart = getOrCreateCart(userId);
        return mapToDto(cart);
    }

    //tồn 5, add 10 -> add 5
    @Override
    @Transactional
    public CartDto addItemToCart(Long userId, Long variantId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Số lượng phải lớn hơn 0");
        }

        Cart cart = getOrCreateCart(userId);
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy biến thể sản phẩm: " + variantId));

        if (variant.getStatus() != ProductVariantStatus.ACTIVE) {
            throw new IllegalArgumentException("Sản phẩm hiện đang tạm dừng bán.");
        }

        CartItem item = cartItemRepository.findByCartIdAndVariantId(cart.getId(), variantId)
                .orElse(null);

        if (item == null) {
            int toAdd = Math.min(quantity, variant.getStockQuantity());
            if (toAdd <= 0) {
                throw new IllegalArgumentException("Sản phẩm đã hết hàng trong kho.");
            }
            item = CartItem.builder()
                    .cart(cart)
                    .variant(variant)
                    .quantity(toAdd)
                    .isSelected(true)
                    .build();
            cartItemRepository.save(item);
        } else {
            int newQuantity = item.getQuantity() + quantity;

            if (newQuantity > variant.getStockQuantity()) {
                throw new IllegalArgumentException("Trong giỏ đã có " + item.getQuantity()
                                                + " sản phẩm. Kho hiện chỉ còn " + variant.getStockQuantity() + ".");
                //newQuantity = variant.getStockQuantity();
            }

            item.setQuantity(newQuantity);
            cartItemRepository.save(item);
        }
        return getCartForUser(userId);
    }

    @Override
    @Transactional
    public CartDto updateItemQuantity(Long userId, Long cartItemId, int newQuantity) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm trong giỏ"));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new IllegalArgumentException("Bạn không có quyền chỉnh sửa mục này");
        }

        if (newQuantity <= 0) {
            cartItemRepository.delete(item);
        } else {
            int allowed = Math.min(newQuantity, item.getVariant().getStockQuantity());
            item.setQuantity(allowed);
            cartItemRepository.save(item);
        }

        ProductVariant variant = item.getVariant();

        if (variant.getStatus() != ProductVariantStatus.ACTIVE) {
            throw new IllegalArgumentException("Sản phẩm hiện đang tạm dừng bán.");
        }

        if (variant.getStockQuantity() <= 0) {
            throw new IllegalArgumentException("Sản phẩm hiện đã hết hàng.");
        }

        if (newQuantity > variant.getStockQuantity()) {
            throw new IllegalArgumentException("Chỉ còn " + variant.getStockQuantity() + " sản phẩm trong kho.");
        }

        item.setQuantity(newQuantity);
        cartItemRepository.save(item);

        return getCartForUser(userId);
    }

    @Override
    @Transactional
    public CartDto removeItem(Long userId, Long cartItemId) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm trong giỏ"));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new IllegalArgumentException("Bạn không có quyền chỉnh sửa mục này");
        }

        cartItemRepository.delete(item);
        return getCartForUser(userId);
    }

    @Override
    @Transactional
    public void clearCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        cartItemRepository.deleteByCartId(cart.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public int getCartItemCount(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(c -> cartItemRepository.findByCartId(c.getId()).stream()
                        .mapToInt(CartItem::getQuantity)
                        .sum())
                .orElse(0);
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản người dùng: " + userId));
            Cart newCart = Cart.builder().user(user).build();
            return cartRepository.save(newCart);
        });
    }

    private CartDto mapToDto(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        List<CartItemDto> itemDtos = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int totalQty = 0;

        for (CartItem item : items) {
            ProductVariant v = item.getVariant();
            String imageUrl = imageRepository.findByProductIdAndIsPrimaryTrue(v.getProduct().getId())
                    .map(ProductImage::getImageUrl)
                    .orElse(null);

            BigDecimal lineTotal = v.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            if (Boolean.TRUE.equals(item.getIsSelected())) {
                subtotal = subtotal.add(lineTotal);
            }
            totalQty += item.getQuantity();

            itemDtos.add(CartItemDto.builder()
                    .id(item.getId())
                    .variantId(v.getId())
                    .productName(v.getProduct().getName())
                    .variantName(v.getVariantName())
                    .skuCode(v.getSkuCode())
                    .imageUrl(imageUrl)
                    .unitPrice(v.getPrice())
                    .quantity(item.getQuantity())
                    .maxStock(v.getStockQuantity())
                    .isSelected(item.getIsSelected())
                    .lineTotal(lineTotal)
                    .build());
        }

        return CartDto.builder()
                .id(cart.getId())
                .userId(cart.getUser().getId())
                .items(itemDtos)
                .subtotal(subtotal)
                .totalQuantity(totalQty)
                .build();
    }
}
