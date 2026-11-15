package com.petshop.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class BrandFormDto {
    @NotBlank(message = "Vui lòng nhập tên thương hiệu.")
    @Size(max = 150, message = "Tên thương hiệu tối đa 150 ký tự.")
    private String name;

    @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự.")
    private String description;

    private MultipartFile logoFile;
    private boolean removeLogo;
}
