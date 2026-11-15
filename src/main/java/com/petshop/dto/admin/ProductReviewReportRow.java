package com.petshop.dto.admin;

import lombok.Value;

@Value
public class ProductReviewReportRow {
    Long productId;
    String productName;
    String productSlug;
    Long reviewCount;
    Double averageRating;
    Long visibleCount;
    Long hiddenCount;
    Long ratingTotal;
}
