package com.petshop.dto.cart;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemDto {
    private Long id;
    private Long variantId;
    private String productName;
    private String variantName;
    private String skuCode;
    private String imageUrl;
    private BigDecimal unitPrice;
    private Integer quantity;
    private Integer maxStock;
    @JsonProperty("isSelected")
    private Boolean isSelected;
    private BigDecimal lineTotal;
    private boolean available;
    private String availabilityMessage;
}
