package com.petshop.service.impl;

import com.petshop.dto.voucher.VoucherApplyResultDto;
import com.petshop.dto.voucher.VoucherRequestDto;
import com.petshop.entity.CustomerVoucher;
import com.petshop.entity.User;
import com.petshop.entity.Voucher;
import com.petshop.entity.enums.DiscountType;
import com.petshop.entity.enums.VoucherStatus;
import com.petshop.repository.CustomerVoucherRepository;
import com.petshop.repository.OrderRepository;
import com.petshop.repository.UserRepository;
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
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherServiceImpl implements VoucherService {

    private final VoucherRepository voucherRepository;
    private final OrderRepository orderRepository;
    private final CustomerVoucherRepository customerVoucherRepository;
    private final UserRepository userRepository;
    private final jakarta.persistence.EntityManager entityManager;

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
                    .message("Mã giảm giá '" + voucherCode + "' không tồn tại")
                    .build();
        }

        if (Boolean.TRUE.equals(voucher.getIsDeleted())) {
            return VoucherApplyResultDto.builder()
                    .valid(false)
                    .message("Mã giảm giá đã bị xóa và không còn được áp dụng.")
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStatus() != VoucherStatus.ACTIVE) {
            return VoucherApplyResultDto.builder()
                    .valid(false)
                    .message("Mã giảm giá đang tạm khóa hoặc ngưng áp dụng")
                    .build();
        }

        if (now.isBefore(voucher.getStartDate())) {
            return VoucherApplyResultDto.builder()
                    .valid(false)
                    .message("Mã giảm giá chưa đến thời gian áp dụng (Bắt đầu từ: " + voucher.getStartDate() + ")")
                    .build();
        }

        if (now.isAfter(voucher.getEndDate())) {
            return VoucherApplyResultDto.builder()
                    .valid(false)
                    .message("Mã giảm giá đã hết hạn sử dụng")
                    .build();
        }

        if (voucher.getUsedCount() >= voucher.getTotalUsageLimit()) {
            return VoucherApplyResultDto.builder()
                    .valid(false)
                    .message("Mã giảm giá đã hết lượt sử dụng trên toàn hệ thống")
                    .build();
        }

        BigDecimal minOrder = voucher.getMinOrderAmount() != null ? voucher.getMinOrderAmount() : BigDecimal.ZERO;
        if (safeSubtotal.compareTo(minOrder) < 0) {
            return VoucherApplyResultDto.builder()
                    .valid(false)
                    .message("Đơn hàng chưa đạt giá trị tối thiểu " + String.format("%,.0f", minOrder) + " đ để áp dụng voucher")
                    .build();
        }

        if (customerId != null) {
            // Check customer wallet if already used
            customerVoucherRepository.findByCustomerIdAndVoucherId(customerId, voucher.getId()).ifPresent(cv -> {
                if (Boolean.TRUE.equals(cv.getIsUsed())) {
                    log.info("Customer {} already used voucher {} from wallet", customerId, voucher.getId());
                }
            });

            int perCustomerLimit = (voucher.getPerCustomerLimit() != null && voucher.getPerCustomerLimit() > 0)
                    ? voucher.getPerCustomerLimit() : 1;
            long usedByCustomer = orderRepository.countVoucherUsageByCustomer(customerId, voucher.getId());
            if (usedByCustomer >= perCustomerLimit) {
                return VoucherApplyResultDto.builder()
                        .valid(false)
                        .message("Bạn đã sử dụng hết số lần cho phép (" + perCustomerLimit + " lần) với mã này")
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
        voucherRepository.findById(voucherId).ifPresent(entityManager::refresh);
    }

    @Override
    @Transactional
    public void releaseVoucher(Long voucherId) {
        if (voucherId != null) {
            voucherRepository.restoreVoucherQuota(voucherId);
            voucherRepository.findById(voucherId).ifPresent(entityManager::refresh);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Voucher> getAllVouchers(Pageable pageable) {
        return voucherRepository.findByIsDeletedFalse(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Voucher> searchVouchers(String keyword, VoucherStatus status, Pageable pageable) {
        return searchVouchers(keyword, status, false, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Voucher> searchVouchers(String keyword, VoucherStatus status, boolean deleted, Pageable pageable) {
        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return voucherRepository.searchVouchers(cleanKeyword, status, deleted, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Voucher getVoucherById(Long id) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã giảm giá với ID: " + id));
        requireNotDeleted(voucher);
        return voucher;
    }

    @Override
    @Transactional
    public Voucher createVoucher(VoucherRequestDto request) {
        String cleanCode = request.getCode().trim().toUpperCase(java.util.Locale.ROOT);
        voucherRepository.findByCode(cleanCode).ifPresent(existing -> { throw duplicateCode(existing); });

        validateVoucherLogic(request);

        Voucher voucher = Voucher.builder()
                .code(cleanCode)
                .title(request.getTitle().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .minOrderAmount(request.getMinOrderAmount())
                .maxDiscountAmount(request.getMaxDiscountAmount())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .totalUsageLimit(request.getTotalUsageLimit())
                .perCustomerLimit(request.getPerCustomerLimit())
                .usedCount(0)
                .status(request.getStatus() != null ? request.getStatus() : VoucherStatus.ACTIVE)
                .build();

        return voucherRepository.save(voucher);
    }

    @Override
    @Transactional
    public Voucher updateVoucher(Long id, VoucherRequestDto request) {
        Voucher voucher = voucherRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã giảm giá."));
        entityManager.refresh(voucher, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        requireNotDeleted(voucher);

        String cleanCode = request.getCode().trim().toUpperCase(java.util.Locale.ROOT);
        if (!voucher.getCode().equalsIgnoreCase(cleanCode)) {
            voucherRepository.findByCode(cleanCode).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw duplicateCode(existing);
                }
            });
            voucher.setCode(cleanCode);
        }

        validateVoucherLogic(request);

        if (request.getTotalUsageLimit() < voucher.getUsedCount()) {
            throw new IllegalArgumentException("Tổng lượt sử dụng không được nhỏ hơn số lượt đã dùng (" + voucher.getUsedCount() + ").");
        }
        voucher.setTitle(request.getTitle().trim());
        voucher.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        voucher.setDiscountType(request.getDiscountType());
        voucher.setDiscountValue(request.getDiscountValue());
        voucher.setMinOrderAmount(request.getMinOrderAmount());
        voucher.setMaxDiscountAmount(request.getMaxDiscountAmount());
        voucher.setStartDate(request.getStartDate());
        voucher.setEndDate(request.getEndDate());
        voucher.setTotalUsageLimit(request.getTotalUsageLimit());
        voucher.setPerCustomerLimit(request.getPerCustomerLimit());
        if (request.getStatus() != null) {
            voucher.setStatus(request.getStatus());
        }

        return voucherRepository.save(voucher);
    }

    @Override
    @Transactional
    public void toggleVoucherStatus(Long id) {
        Voucher voucher = voucherRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã giảm giá."));
        entityManager.refresh(voucher, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        requireNotDeleted(voucher);
        voucher.setStatus(voucher.getStatus() == VoucherStatus.ACTIVE ? VoucherStatus.INACTIVE : VoucherStatus.ACTIVE);
        voucherRepository.save(voucher);
    }

    @Override
    @Transactional
    public void softDeleteVoucher(Long id) {
        Voucher voucher = voucherRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã giảm giá."));
        entityManager.refresh(voucher, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (Boolean.TRUE.equals(voucher.getIsDeleted())) {
            return;
        }
        voucher.setIsDeleted(true);
        voucher.setDeletedAt(LocalDateTime.now());
        voucher.setStatus(VoucherStatus.INACTIVE);
        voucherRepository.save(voucher);
    }

    @Override
    @Transactional
    public void restoreDeletedVoucher(Long id) {
        Voucher voucher = voucherRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã giảm giá."));
        entityManager.refresh(voucher, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!Boolean.TRUE.equals(voucher.getIsDeleted())) return;
        // Restore the same campaign; preserve its dates, quotas, usage and history.
        voucher.setIsDeleted(false);
        voucher.setDeletedAt(null);
        voucher.setStatus(VoucherStatus.INACTIVE);
        voucherRepository.save(voucher);
    }

    @Override
    @Transactional
    public void saveVoucherToWallet(Long customerId, Long voucherId) {
        User customer = userRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khách hàng: " + customerId));
        Voucher voucher = voucherRepository.findByIdForUpdate(voucherId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã giảm giá."));
        entityManager.refresh(voucher, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        requireNotDeleted(voucher);

        if (voucher.getStatus() != VoucherStatus.ACTIVE) {
            throw new IllegalArgumentException("Voucher này đang không hoạt động.");
        }
        if (LocalDateTime.now().isAfter(voucher.getEndDate())) {
            throw new IllegalArgumentException("Voucher này đã hết hạn.");
        }

        if (customerVoucherRepository.existsByCustomerIdAndVoucherId(customerId, voucherId)) {
            throw new IllegalArgumentException("Bạn đã lưu mã giảm giá này vào ví rồi.");
        }

        CustomerVoucher cv = CustomerVoucher.builder()
                .customer(customer)
                .voucher(voucher)
                .savedAt(LocalDateTime.now())
                .isUsed(orderRepository.countVoucherUsageByCustomer(customerId, voucherId) > 0)
                .build();

        customerVoucherRepository.save(cv);
        log.info("Customer {} saved voucher {} to wallet", customerId, voucherId);
    }

    @Override
    @Transactional
    public void removeVoucherFromWallet(Long customerId, Long walletVoucherId) {
        customerVoucherRepository.deleteByCustomerIdAndId(customerId, walletVoucherId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerVoucher> getCustomerWalletVouchers(Long customerId) {
        List<CustomerVoucher> wallet = customerVoucherRepository.findByCustomerIdWithVoucher(customerId);
        for (CustomerVoucher entry : wallet) {
            long used = orderRepository.countVoucherUsageByCustomer(customerId, entry.getVoucher().getId());
            entry.setRemainingUses((int)Math.max(0, entry.getVoucher().getPerCustomerLimit() - used));
        }
        return wallet;
    }

    @Override
    @Transactional
    public void markCustomerVoucherUsed(Long customerId, Long voucherId) {
        customerVoucherRepository.markAsUsed(customerId, voucherId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void restoreCustomerVoucher(Long customerId, Long voucherId) {
        if (orderRepository.countVoucherUsageByCustomer(customerId, voucherId) == 0) {
            customerVoucherRepository.restoreUnused(customerId, voucherId);
        }
    }

    private void requireNotDeleted(Voucher voucher) {
        if (Boolean.TRUE.equals(voucher.getIsDeleted())) {
            throw new IllegalArgumentException("Voucher đã bị xóa. Hãy vào Voucher đã xóa và phục hồi trước khi chỉnh sửa hoặc sử dụng.");
        }
    }

    private IllegalArgumentException duplicateCode(Voucher voucher) {
        return new IllegalArgumentException(Boolean.TRUE.equals(voucher.getIsDeleted())
                ? "Mã voucher '" + voucher.getCode() + "' đã được dùng cho voucher đã xóa. Hãy vào Voucher đã xóa để phục hồi, không tạo mã trùng."
                : "Mã voucher '" + voucher.getCode() + "' đã tồn tại trong hệ thống.");
    }

    private void validateVoucherLogic(VoucherRequestDto request) {
        if (request.getStartDate() == null || request.getEndDate() == null
                || !request.getEndDate().isAfter(request.getStartDate())) {
            throw new IllegalArgumentException("Ngày kết thúc phải sau ngày bắt đầu.");
        }
        if (request.getMaxDiscountAmount() != null && request.getMaxDiscountAmount().signum() <= 0) {
            throw new IllegalArgumentException("Mức giảm tối đa phải lớn hơn 0 hoặc để trống.");
        }
        if (request.getDiscountType() == null || request.getDiscountValue() == null || request.getDiscountValue().signum() <= 0
                || request.getMinOrderAmount() == null || request.getMinOrderAmount().signum() < 0
                || request.getTotalUsageLimit() == null || request.getTotalUsageLimit() < 1
                || request.getPerCustomerLimit() == null || request.getPerCustomerLimit() < 1) {
            throw new IllegalArgumentException("Giá trị giảm, giá trị đơn và giới hạn lượt dùng không hợp lệ.");
        }
        if (request.getDiscountType() == DiscountType.PERCENT) {
            if (request.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("Giảm giá theo phần trăm không được vượt quá 100%.");
            }
        }
    }
}
