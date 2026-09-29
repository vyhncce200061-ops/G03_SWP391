package com.petshop.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequestDto {

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(max = 150, message = "Họ và tên không vượt quá 150 ký tự")
    private String fullName;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 255, message = "Email không vượt quá 255 ký tự")
    private String email;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Size(min = 9, max = 15, message = "Số điện thoại từ 9 đến 15 ký tự")
    private String phone;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, message = "Mật khẩu tối thiểu 6 ký tự")
    private String password;

    @NotBlank(message = "Vui lòng xác nhận mật khẩu")
    private String confirmPassword;
}
