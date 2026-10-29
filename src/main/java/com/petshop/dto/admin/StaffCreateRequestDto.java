package com.petshop.dto.admin;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StaffCreateRequestDto {
    @NotBlank(message = "Họ và tên không được để trống")
    @Size(max = 150, message = "Họ và tên không vượt quá 150 ký tự")
    private String fullName;

    @NotBlank(message = "Email nhân viên là bắt buộc để nhận liên kết đặt mật khẩu")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 255)
    private String email;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^(0|\\+84)[35789][0-9]{8}$", message = "Số điện thoại không đúng định dạng Việt Nam")
    private String phone;
}
