package com.petshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite Primary Key for WishlistItem entity.
 * Composed of CustomerId and ProductId.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistItemId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "CustomerId")
    private Long customerId;

    @Column(name = "ProductId")
    private Long productId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WishlistItemId that = (WishlistItemId) o;
        return Objects.equals(customerId, that.customerId) &&
                Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId, productId);
    }
}
