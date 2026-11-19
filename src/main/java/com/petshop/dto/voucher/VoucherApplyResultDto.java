package com.petshop.dto.voucher;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherApplyResultDto {
    private boolean valid;
    private String message;
    private Long voucherId;
    private String voucherCode;
    private BigDecimal discountAmount;
    /** Merchandise after discount, before any delivery fee. */
    private BigDecimal finalTotal;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;
}
