package com.petshop.dto.catalog;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryFormDto {
    @NotBlank(message = "Vui lòng nhập mã danh mục.")
    @Size(max = 50, message = "Mã danh mục tối đa 50 ký tự.")
    private String code;

    @NotBlank(message = "Vui lòng nhập tên danh mục.")
    @Size(max = 150, message = "Tên danh mục tối đa 150 ký tự.")
    private String name;

    @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự.")
    private String description;

    private Integer parentId;

    @NotNull(message = "Vui lòng nhập thứ tự hiển thị.")
    @Min(value = 0, message = "Thứ tự hiển thị không được âm.")
    private Integer sortOrder = 0;

    private Boolean isActive = true;
}
