package com.petshop.dto.order;

import com.petshop.entity.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutSubmitDto {

    @NotBlank(message = "Tên người nhận không được để trống")
    @Size(max = 150)
    private String recipientName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Size(max = 20)
    private String recipientPhone;

    @NotBlank(message = "Tỉnh/Thành phố không được để trống")
    private String shippingProvince;

    @NotBlank(message = "Quận/Huyện không được để trống")
    private String shippingDistrict;

    @NotBlank(message = "Phường/Xã không được để trống")
    private String shippingWard;

    @NotBlank(message = "Địa chỉ chi tiết không được để trống")
    @Size(max = 300)
    private String shippingAddressLine;

    private String customerNote;

    private String voucherCode;

    @NotNull(message = "Vui lòng chọn phương thức thanh toán")
    private PaymentMethod paymentMethod;
}
