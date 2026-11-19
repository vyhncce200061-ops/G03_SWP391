package com.petshop.dto.catalog;

import com.petshop.entity.ProductVariant;
import com.petshop.entity.enums.ProductVariantStatus;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Form DTO for creating/editing a ProductVariant (SKU) from Staff side.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantFormDto {

    private Long id;

    @NotBlank(message = "Mã SKU không được để trống")
    @Size(max = 80, message = "Mã SKU tối đa 80 ký tự")
    private String skuCode;

    @NotBlank(message = "Tên biến thể không được để trống")
    @Size(max = 200, message = "Tên biến thể tối đa 200 ký tự")
    private String variantName;

    @Size(max = 50, message = "Màu sắc tối đa 50 ký tự")
    private String color;

    @DecimalMin(value = "0.01", inclusive = true, message = "Khối lượng phải lớn hơn 0")
    @Digits(integer = 8, fraction = 2, message = "Khối lượng không hợp lệ")
    private BigDecimal weightKg;

    @NotNull(message = "Giá bán không được để trống")
    @DecimalMin(value = "0", inclusive = true, message = "Giá bán phải lớn hơn hoặc bằng 0")
    @Digits(integer = 16, fraction = 2, message = "Giá bán không hợp lệ")
    @com.petshop.validation.WholeVnd(message = "Giá bán phải là số nguyên đồng")
    private BigDecimal price;

    @NotNull(message = "Ngưỡng cảnh báo không được để trống")
    @Min(value = 0, message = "Ngưỡng cảnh báo phải >= 0")
    @Builder.Default
    private Integer lowStockThreshold = 5;

    @NotNull(message = "Vui lòng chọn trạng thái")
    private ProductVariantStatus status;

    /**
     * Map DTO to Entity (used when CREATING new SKU).
     * Stock quantity defaults to 0 for new SKU.
     */
    public ProductVariant toEntity() {
        ProductVariant variant = new ProductVariant();
        variant.setSkuCode(this.skuCode.trim());
        variant.setVariantName(this.variantName.trim());
        variant.setColor(this.color != null && !this.color.isBlank() ? this.color.trim() : null);
        variant.setWeightKg(this.weightKg);
        variant.setPrice(this.price);
        variant.setLowStockThreshold(this.lowStockThreshold != null ? this.lowStockThreshold : 5);
        variant.setStatus(this.status != null ? this.status : ProductVariantStatus.ACTIVE);
        variant.setStockQuantity(0); // New SKU starts with no stock
        return variant;
    }

    /**
     * Map Entity to DTO (when loading edit form).
     */
    public static ProductVariantFormDto fromEntity(ProductVariant variant) {
        return ProductVariantFormDto.builder()
                .skuCode(variant.getSkuCode())
                .variantName(variant.getVariantName())
                .color(variant.getColor())
                .weightKg(variant.getWeightKg())
                .price(variant.getPrice())
                .lowStockThreshold(variant.getLowStockThreshold())
                .status(variant.getStatus())
                .build();
    }
}
