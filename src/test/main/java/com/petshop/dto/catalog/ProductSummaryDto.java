package com.petshop.dto.catalog;

import com.petshop.entity.enums.PetType;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSummaryDto {
    private Long id;
    private String name;
    private String slug;
    private String categoryName;
    private String brandName;
    private PetType petType;
    private String primaryImageUrl;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer totalStock;
    private Double avgRating;
    private Long reviewCount;
}
