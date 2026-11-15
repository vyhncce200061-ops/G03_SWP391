package com.petshop.dto.stock;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

/**
 * DTO for editing Stock Import information and its items.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockImportEditFormDto {

    @NotNull(message = "ID phiếu nhập không được để trống")
    private Long importId;

    @Size(max = 200, message = "Nhà cung cấp tối đa 200 ký tự")
    private String supplierName;

    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String note;

    // --- Fields for editing an item ---
    private Long itemId;

    @NotNull(message = "Số lượng không được để trống")
    @jakarta.validation.constraints.Max(value = 10000, message = "Số lượng nhập tối đa 10.000")
    @Min(value = 1, message = "Số lượng nhập phải lớn hơn 0")
    private Integer quantity;

    @NotNull(message = "Đơn giá không được để trống")
    @jakarta.validation.constraints.Digits(integer = 16, fraction = 2, message = "Đơn giá nhập không hợp lệ")
    @DecimalMin(value = "0", inclusive = true, message = "Đơn giá phải lớn hơn hoặc bằng 0")
    private BigDecimal unitCost;
}