package com.petshop.dto.stock;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * DTO for a single line item in stock import form.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockImportItemDto {

    @NotNull(message = "Vui lòng chọn sản phẩm")
    private Long variantId;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Số lượng nhập phải lớn hơn 0")
    @Max(value = 10000, message = "Số lượng nhập tối đa 10.000")
    private Integer quantity;

    @NotNull(message = "Đơn giá nhập không được để trống")
    @DecimalMin(value = "0", inclusive = true, message = "Đơn giá nhập phải >= 0")
    @Digits(integer = 16, fraction = 2, message = "Đơn giá nhập không hợp lệ")
    private BigDecimal unitCost;
}