package com.petshop.service;

import com.petshop.dto.voucher.VoucherApplyResultDto;
import com.petshop.entity.Voucher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface VoucherService {
    VoucherApplyResultDto validateAndCalculateDiscount(String voucherCode, Long customerId, java.math.BigDecimal subtotal);
    void reserveVoucher(Long voucherId);
    void releaseVoucher(Long voucherId);

    Page<Voucher> getAllVouchers(Pageable pageable);
    Page<Voucher> searchVouchers(String keyword, com.petshop.entity.enums.VoucherStatus status, Pageable pageable);
    Page<Voucher> searchVouchers(String keyword, com.petshop.entity.enums.VoucherStatus status, boolean deleted, Pageable pageable);
    Voucher getVoucherById(Long id);
    Voucher createVoucher(com.petshop.dto.voucher.VoucherRequestDto request);
    Voucher updateVoucher(Long id, com.petshop.dto.voucher.VoucherRequestDto request);
    void toggleVoucherStatus(Long id);
    void softDeleteVoucher(Long id);
    void restoreDeletedVoucher(Long id);

    // Customer Voucher Wallet (UC-15)
    void saveVoucherToWallet(Long customerId, Long voucherId);
    void removeVoucherFromWallet(Long customerId, Long walletVoucherId);
    java.util.List<com.petshop.entity.CustomerVoucher> getCustomerWalletVouchers(Long customerId);
    void markCustomerVoucherUsed(Long customerId, Long voucherId);
    void restoreCustomerVoucher(Long customerId, Long voucherId);
}
