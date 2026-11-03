package com.petshop.dto.admin;

import jakarta.validation.constraints.Email;
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

    private Long id;

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(max = 150, message = "Họ và tên không vượt quá 150 ký tự")
    private String fullName;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 255, message = "Email không vượt quá 255 ký tự")
    private String email;

    @Pattern(regexp = "^(0|\\+84)[3|5|7|8|9][0-9]{8}$|^$", message = "Số điện thoại không đúng định dạng Việt Nam")
    private String phone;

    @NotBlank(message = "Trạng thái không được để trống")
    @Pattern(regexp = "^(ACTIVE|LOCKED)$", message = "Trạng thái chỉ có thể là ACTIVE hoặc LOCKED")
    private String status;
}
