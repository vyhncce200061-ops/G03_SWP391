package com.petshop.dto.catalog;

import com.petshop.entity.enums.PetType;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductFilterDto {
    private String keyword;
    private Integer categoryId;
    private String categoryCode;
    private Integer brandId;
    private PetType petType;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String sortBy; // price_asc, price_desc, newest
}
