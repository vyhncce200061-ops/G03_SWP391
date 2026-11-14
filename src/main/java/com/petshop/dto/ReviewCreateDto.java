package com.petshop.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class ReviewCreateDto {
    @NotNull @Min(value=1, message="Chọn từ 1 đến 5 sao") @Max(value=5, message="Chọn từ 1 đến 5 sao")
    private Integer rating;
    @NotBlank(message="Vui lòng nhập nhận xét")
    @Size(min=1, max=1000, message="Nhận xét tối đa 1000 ký tự")
    private String comment;
}
