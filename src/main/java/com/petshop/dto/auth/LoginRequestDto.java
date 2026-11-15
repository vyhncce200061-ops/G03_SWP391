package com.petshop.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequestDto {

    /** Email or phone number. */
    @NotBlank(message = "Vui lòng nhập email hoặc số điện thoại")
    private String username;

    @NotBlank(message = "Vui lòng nhập mật khẩu")
    private String password;
}
