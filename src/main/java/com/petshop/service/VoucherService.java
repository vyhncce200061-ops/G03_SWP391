package com.petshop.service;

import com.petshop.dto.voucher.VoucherApplyResultDto;
import com.petshop.entity.Voucher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface VoucherService {
    VoucherApplyResultDto validateAndCalculateDiscount(String voucherCode, Long customerId, BigDecimal subtotal);
    void reserveVoucher(Long voucherId);
    void releaseVoucher(Long voucherId);

    Page<Voucher> getAllVouchers(Pageable pageable);
    Voucher createVoucher(Voucher voucher);
    Voucher updateVoucher(Long id, Voucher voucher);
    void toggleVoucherStatus(Long id);
}
