package com.petshop.repository;

import com.petshop.entity.WishlistItem;
import com.petshop.entity.WishlistItemId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, WishlistItemId> {
    List<WishlistItem> findByIdCustomerId(Long customerId);
    boolean existsByIdCustomerIdAndIdProductId(Long customerId, Long productId);
    void deleteByIdCustomerIdAndIdProductId(Long customerId, Long productId);
    long countByIdCustomerId(Long customerId);
}
