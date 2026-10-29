package com.petshop.service.impl;

import com.petshop.dto.cart.CartDto;
import com.petshop.dto.cart.CartItemDto;
import com.petshop.entity.Cart;
import com.petshop.entity.CartItem;
import com.petshop.entity.ProductImage;
import com.petshop.entity.ProductVariant;
import com.petshop.entity.User;
import com.petshop.entity.enums.ProductStatus;
import com.petshop.entity.enums.ProductVariantStatus;
import com.petshop.repository.CartItemRepository;
import com.petshop.repository.CartRepository;
import com.petshop.repository.ProductImageRepository;
import com.petshop.repository.ProductVariantRepository;
import com.petshop.repository.UserRepository;
import com.petshop.service.CartService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final com.petshop.config.ShippingFeeConfig shippingFeeConfig;

    @Override
    @Transactional(readOnly = true)
    public CartDto getCartForUser(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(this::mapToDto)
                .orElseGet(() -> CartDto.builder()
                        .userId(userId)
                        .subtotal(BigDecimal.ZERO)
                        .shippingFee(BigDecimal.ZERO)
                        .totalAmount(BigDecimal.ZERO)
                        .build());
    }

    @Override
    @Transactional
    public CartDto addItemToCart(Long userId, Long variantId, int quantity) {
        requirePositiveQuantity(quantity);
        Cart cart = getOrCreateCartForUpdate(userId);
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy biến thể sản phẩm."));
        requireAvailable(variant);

        CartItem item = cartItemRepository.findByCartIdAndVariantId(cart.getId(), variantId).orElse(null);
        long newQuantity = (item == null ? 0L : item.getQuantity().longValue()) + quantity;
        requireWithinStock(variant, newQuantity);
        if (item == null) {
            item = CartItem.builder()
                    .cart(cart)
                    .variant(variant)
                    .quantity((int) newQuantity)
                    .isSelected(true)
                    .build();
        } else {
            item.setQuantity((int) newQuantity);
        }
        cartItemRepository.save(item);
        return mapToDto(cart);
    }

    @Override
    @Transactional
    public CartDto updateItemQuantity(Long userId, Long cartItemId, int newQuantity) {
        requirePositiveQuantity(newQuantity);
        Cart cart = getOrCreateCartForUpdate(userId);
        CartItem item = getOwnedItem(cart, cartItemId);
        requireAvailable(item.getVariant());
        requireWithinStock(item.getVariant(), newQuantity);
        item.setQuantity(newQuantity);
        cartItemRepository.save(item);
        return mapToDto(cart);
    }

    @Override
    @Transactional
    public CartDto updateItemSelection(Long userId, Long cartItemId, boolean selected) {
        Cart cart = getOrCreateCartForUpdate(userId);
        CartItem item = getOwnedItem(cart, cartItemId);
        item.setIsSelected(selected);
        cartItemRepository.save(item);
        return mapToDto(cart);
    }

    @Override
    @Transactional
    public void selectItem(Long userId, Long cartItemId, boolean selected) {
        updateItemSelection(userId, cartItemId, selected);
    }

    @Override
    @Transactional
    public CartDto removeItem(Long userId, Long cartItemId) {
        Cart cart = getOrCreateCartForUpdate(userId);
        CartItem item = getOwnedItem(cart, cartItemId);
        // Unavailable products must still be removable from the customer's own cart.
        cartItemRepository.delete(item);
        return mapToDto(cart);
    }

    @Override
    @Transactional
    public void clearCart(Long userId) {
        cartRepository.findByUserIdForUpdate(userId)
                .ifPresent(cart -> cartItemRepository.deleteByCartId(cart.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public int getCartItemCount(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(cart -> cappedQuantity(cartItemRepository.findByCartId(cart.getId()).stream()
                        .mapToLong(item -> Math.max(0, item.getQuantity())).sum()))
                .orElse(0);
    }

    private Cart getOrCreateCartForUpdate(Long userId) {
        return cartRepository.findByUserIdForUpdate(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản người dùng."));
            // The unique Cart.UserId constraint alone would turn concurrent first additions into a 500.
            entityManager.lock(user, LockModeType.PESSIMISTIC_WRITE);
            return cartRepository.findByUserIdForUpdate(userId)
                    .orElseGet(() -> cartRepository.save(Cart.builder().user(user).build()));
        });
    }

    private CartItem getOwnedItem(Cart cart, Long cartItemId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm trong giỏ."));
        if (!cart.getId().equals(item.getCart().getId())) {
            throw new IllegalArgumentException("Không tìm thấy sản phẩm trong giỏ của bạn.");
        }
        return item;
    }

    private void requirePositiveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Số lượng phải lớn hơn 0. Dùng nút xóa để bỏ sản phẩm khỏi giỏ.");
        }
    }

    private void requireAvailable(ProductVariant variant) {
        String message = productAvailabilityMessage(variant);
        if (message != null) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireWithinStock(ProductVariant variant, long quantity) {
        if (quantity > variant.getStockQuantity()) {
            throw new IllegalArgumentException("Chỉ còn " + variant.getStockQuantity() + " sản phẩm trong kho.");
        }
    }

    private String productAvailabilityMessage(ProductVariant variant) {
        if (variant.getStatus() != ProductVariantStatus.ACTIVE
                || variant.getProduct().getStatus() != ProductStatus.ACTIVE) {
            return "Sản phẩm hiện đang tạm dừng bán.";
        }
        if (variant.getStockQuantity() <= 0) {
            return "Sản phẩm hiện đã hết hàng.";
        }
        return null;
    }

    private int cappedQuantity(long quantity) {
        return (int) Math.min(Integer.MAX_VALUE, quantity);
    }

    private CartDto mapToDto(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        List<CartItemDto> itemDtos = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        long totalQty = 0;
        long selectedQty = 0;
        int selectedItemCount = 0;
        boolean selectedItemsAvailable = true;

        for (CartItem item : items) {
            ProductVariant variant = item.getVariant();
            String imageUrl = imageRepository.findByProductIdAndIsPrimaryTrue(variant.getProduct().getId())
                    .map(ProductImage::getImageUrl)
                    .orElse(null);
            String availabilityMessage = productAvailabilityMessage(variant);
            if (availabilityMessage == null && item.getQuantity() > variant.getStockQuantity()) {
                availabilityMessage = "Chỉ còn " + variant.getStockQuantity() + " sản phẩm trong kho. Hãy giảm số lượng.";
            }
            if (availabilityMessage == null && item.getQuantity() <= 0) {
                availabilityMessage = "Số lượng sản phẩm không hợp lệ.";
            }
            boolean available = availabilityMessage == null;
            BigDecimal lineTotal = variant.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            if (Boolean.TRUE.equals(item.getIsSelected())) {
                subtotal = subtotal.add(lineTotal);
                selectedQty += Math.max(0, item.getQuantity());
                selectedItemCount++;
                selectedItemsAvailable &= available;
            }
            totalQty += Math.max(0, item.getQuantity());
            itemDtos.add(CartItemDto.builder()
                    .id(item.getId())
                    .variantId(variant.getId())
                    .productName(variant.getProduct().getName())
                    .variantName(variant.getVariantName())
                    .skuCode(variant.getSkuCode())
                    .imageUrl(imageUrl)
                    .unitPrice(variant.getPrice())
                    .quantity(item.getQuantity())
                    .maxStock(variant.getStockQuantity())
                    .isSelected(Boolean.TRUE.equals(item.getIsSelected()))
                    .lineTotal(lineTotal)
                    .available(available)
                    .availabilityMessage(availabilityMessage)
                    .build());
        }
        BigDecimal shippingFee = selectedItemCount == 0 ? BigDecimal.ZERO : shippingFeeConfig.getShippingFee();
        return CartDto.builder()
                .id(cart.getId())
                .userId(cart.getUser().getId())
                .items(itemDtos)
                .subtotal(subtotal)
                .totalQuantity(cappedQuantity(totalQty))
                .selectedQuantity(cappedQuantity(selectedQty))
                .selectedItemCount(selectedItemCount)
                .checkoutAllowed(selectedItemCount > 0 && selectedItemsAvailable)
                .shippingFee(shippingFee)
                .totalAmount(subtotal.add(shippingFee))
                .build();
    }
}
