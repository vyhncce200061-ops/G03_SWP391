package com.petshop.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressCreateDto {

    @NotBlank(message = "Tên người nhận không được để trống")
    @Size(max = 150)
    private String recipientName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Size(max = 20)
    private String recipientPhone;

    @NotBlank(message = "Tỉnh/Thành phố không được để trống")
    private String province;

    @NotBlank(message = "Quận/Huyện không được để trống")
    private String district;

    @NotBlank(message = "Phường/Xã không được để trống")
    private String ward;

    @NotBlank(message = "Địa chỉ chi tiết không được để trống")
    @Size(max = 300)
    private String addressLine;

    private Boolean isDefault;
}
