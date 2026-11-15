package com.petshop.service;

import com.petshop.entity.*;
import com.petshop.entity.enums.ProductStatus;
import com.petshop.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WishlistService {
    private final WishlistItemRepository wishlistRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<WishlistItem> getWishlist(Long customerId) {
        List<WishlistItem> items = wishlistRepository.findByIdCustomerIdOrderByCreatedAtDesc(customerId);
        items.forEach(item -> {
            item.getProduct().getImages().size();
            item.getProduct().getVariants().size();
        });
        return items;
    }

    @Transactional(readOnly = true)
    public boolean contains(Long customerId, Long productId) {
        return wishlistRepository.existsByIdCustomerIdAndIdProductId(customerId, productId);
    }

    @Transactional
    public void add(Long customerId, Long productId) {
        // Serialize concurrent additions/removals for the same customer.
        User customer = lockCustomer(customerId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new IllegalArgumentException("Sản phẩm hiện không còn kinh doanh.");
        }
        WishlistItemId id = new WishlistItemId(customerId, productId);
        if (!wishlistRepository.existsById(id)) {
            wishlistRepository.save(WishlistItem.builder().id(id).customer(customer).product(product).build());
        }
    }

    @Transactional
    public void remove(Long customerId, Long productId) {
        lockCustomer(customerId);
        wishlistRepository.deleteByIdCustomerIdAndIdProductId(customerId, productId);
    }

    private User lockCustomer(Long id) {
        User customer = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        if (!"ACTIVE".equals(customer.getStatus()) || !"CUSTOMER".equals(customer.getRole().getCode())) {
            throw new IllegalArgumentException("Tài khoản không được phép quản lý danh sách yêu thích.");
        }
        return customer;
    }
}
