package com.petshop.dto.stock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Form DTO for creating a Stock Import receipt with multiple items.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockImportFormDto {

    @NotBlank(message = "Nhà cung cấp không được để trống")
    @Size(max = 200, message = "Nhà cung cấp tối đa 200 ký tự")
    private String supplierName;

    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String note;

    @NotEmpty(message = "Phải có ít nhất một sản phẩm trong phiếu nhập")
    @Valid
    @Builder.Default
    private List<StockImportItemDto> items = new ArrayList<>();
}