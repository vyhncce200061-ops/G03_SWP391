package com.petshop.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileUpdateRequestDto {

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(max = 150)
    private String fullName;

    @Size(max = 255)
    private String email;

    @Size(max = 20)
    private String phone;

    private String avatarUrl;
}
