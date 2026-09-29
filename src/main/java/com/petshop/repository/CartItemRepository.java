package com.petshop.repository;

import com.petshop.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCartId(Long cartId);
    Optional<CartItem> findByCartIdAndVariantId(Long cartId, Long variantId);

    void deleteByCartIdAndVariantId(Long cartId, Long variantId);
    void deleteByCartId(Long cartId);

    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.variant.id IN :variantIds")
    void deletePurchasedItems(@Param("cartId") Long cartId, @Param("variantIds") List<Long> variantIds);
}
