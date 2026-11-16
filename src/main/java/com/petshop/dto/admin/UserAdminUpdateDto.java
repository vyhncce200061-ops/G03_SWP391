package com.petshop.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAdminUpdateDto {

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(max = 150, message = "Họ và tên không vượt quá 150 ký tự")
    @Pattern(regexp = "^(?![\\p{Z}\\s]*$)[^\\p{Cc}\\p{Cf}]*$", message = "Họ và tên không hợp lệ")
    private String fullName;
}
