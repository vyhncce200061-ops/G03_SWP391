package com.petshop.service.impl;

import com.petshop.dto.voucher.VoucherApplyResultDto;
import com.petshop.entity.Voucher;
import com.petshop.entity.enums.DiscountType;
import com.petshop.entity.enums.VoucherStatus;
import com.petshop.repository.OrderRepository;
import com.petshop.repository.VoucherRepository;
import com.petshop.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherServiceImpl implements VoucherService {

    private final VoucherRepository voucherRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional(readOnly = true)
    public VoucherApplyResultDto validateAndCalculateDiscount(String voucherCode, Long customerId, BigDecimal subtotal) {
        if (voucherCode == null || voucherCode.isBlank()) {
            return VoucherApplyResultDto.builder()
                    .valid(false)
                    .message("Mã voucher không được để trống")
                    .build();
        }

        BigDecimal safeSubtotal = (subtotal != null) ? subtotal : BigDecimal.ZERO;

        Voucher voucher = voucherRepository.findByCode(voucherCode.trim().toUpperCase())
                .orElse(null);

        if (voucher == null) {
            return VoucherApplyResultDto.builder()
                    .valid(false)
                    .message("Mã giảm giá không tồn tại")
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStatus() != VoucherStatus.ACTIVE || now.isBefore(voucher.getStartDate()) || now.isAfter(voucher.getEndDate())) {
            return VoucherApplyResultDto.builder()
                    .valid(false)
                    .message("Mã giảm giá đã hết hạn hoặc chưa kích hoạt")
                    .build();
        }

        if (voucher.getUsedCount() >= voucher.getTotalUsageLimit()) {
            return VoucherApplyResultDto.builder()
                    .valid(false)
                    .message("Mã giảm giá đã hết lượt sử dụng")
                    .build();
        }

        BigDecimal minOrder = voucher.getMinOrderAmount() != null ? voucher.getMinOrderAmount() : BigDecimal.ZERO;
        if (safeSubtotal.compareTo(minOrder) < 0) {
            return VoucherApplyResultDto.builder()
                    .valid(false)
                    .message("Đơn hàng chưa đạt giá trị tối thiểu " + minOrder + " đ để áp dụng voucher")
                    .build();
        }

        if (customerId != null) {
            int perCustomerLimit = (voucher.getPerCustomerLimit() != null && voucher.getPerCustomerLimit() > 0)
                    ? voucher.getPerCustomerLimit() : 1;
            long usedByCustomer = orderRepository.countVoucherUsageByCustomer(customerId, voucher.getId());
            if (usedByCustomer >= perCustomerLimit) {
                return VoucherApplyResultDto.builder()
                        .valid(false)
                        .message("Bạn đã sử dụng hết số lần cho phép với mã này")
                        .build();
            }
        }

        BigDecimal discountAmount;
        if (voucher.getDiscountType() == DiscountType.PERCENT) {
            BigDecimal rawDiscount = safeSubtotal.multiply(voucher.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (voucher.getMaxDiscountAmount() != null && rawDiscount.compareTo(voucher.getMaxDiscountAmount()) > 0) {
                discountAmount = voucher.getMaxDiscountAmount();
            } else {
                discountAmount = rawDiscount;
            }
        } else {
            discountAmount = voucher.getDiscountValue();
        }

        if (discountAmount.compareTo(safeSubtotal) > 0) {
            discountAmount = safeSubtotal;
        }

        BigDecimal finalTotal = safeSubtotal.subtract(discountAmount);

        return VoucherApplyResultDto.builder()
                .valid(true)
                .message("Áp dụng mã giảm giá thành công!")
                .voucherId(voucher.getId())
                .voucherCode(voucher.getCode())
                .discountAmount(discountAmount)
                .finalTotal(finalTotal)
                .build();
    }

    @Override
    @Transactional
    public void reserveVoucher(Long voucherId) {
        int updated = voucherRepository.reserveVoucherAtomic(voucherId, LocalDateTime.now());
        if (updated == 0) {
            throw new IllegalStateException("Mã giảm giá đã hết lượt sử dụng hoặc không còn hiệu lực.");
        }
    }

    @Override
    @Transactional
    public void releaseVoucher(Long voucherId) {
        if (voucherId != null) {
            voucherRepository.restoreVoucherQuota(voucherId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Voucher> getAllVouchers(Pageable pageable) {
        return voucherRepository.findAll(pageable);
    }

    @Override
    @Transactional
    public Voucher createVoucher(Voucher voucher) {
        if (voucherRepository.findByCode(voucher.getCode().trim().toUpperCase()).isPresent()) {
            throw new IllegalArgumentException("Mã voucher đã tồn tại: " + voucher.getCode());
        }
        voucher.setCode(voucher.getCode().trim().toUpperCase());
        return voucherRepository.save(voucher);
    }

    @Override
    @Transactional
    public Voucher updateVoucher(Long id, Voucher updateData) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy voucher: " + id));

        voucher.setTitle(updateData.getTitle());
        voucher.setDescription(updateData.getDescription());
        voucher.setDiscountType(updateData.getDiscountType());
        voucher.setDiscountValue(updateData.getDiscountValue());
        voucher.setMinOrderAmount(updateData.getMinOrderAmount());
        voucher.setMaxDiscountAmount(updateData.getMaxDiscountAmount());
        voucher.setStartDate(updateData.getStartDate());
        voucher.setEndDate(updateData.getEndDate());
        voucher.setTotalUsageLimit(updateData.getTotalUsageLimit());
        voucher.setPerCustomerLimit(updateData.getPerCustomerLimit());
        voucher.setStatus(updateData.getStatus());

        return voucherRepository.save(voucher);
    }

    @Override
    @Transactional
    public void toggleVoucherStatus(Long id) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy voucher: " + id));
        voucher.setStatus(voucher.getStatus() == VoucherStatus.ACTIVE ? VoucherStatus.INACTIVE : VoucherStatus.ACTIVE);
        voucherRepository.save(voucher);
    }
}
