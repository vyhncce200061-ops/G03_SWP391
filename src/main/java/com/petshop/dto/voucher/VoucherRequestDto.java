package com.petshop.dto.voucher;

import com.petshop.entity.enums.DiscountType;
import com.petshop.entity.enums.VoucherStatus;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherRequestDto {

    private Long id;

    @NotBlank(message = "Mã voucher không được để trống")
    @Size(min = 3, max = 50, message = "Mã voucher từ 3 đến 50 ký tự")
    @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Mã voucher chỉ bao gồm chữ in hoa, số, dấu gạch nối hoặc gạch dưới")
    private String code;
    public void setCode(String code) { this.code = code == null ? null : code.trim().toUpperCase(java.util.Locale.ROOT); }

    @NotBlank(message = "Tiêu đề voucher không được để trống")
    @Size(max = 150, message = "Tiêu đề không vượt quá 150 ký tự")
    private String title;

    @Size(max = 1000, message = "Mô tả không vượt quá 1000 ký tự")
    private String description;

    @NotNull(message = "Loại giảm giá không được để trống")
    private DiscountType discountType;

    @NotNull(message = "Giá trị giảm không được để trống")
    @DecimalMin(value = "0.01", message = "Giá trị giảm phải lớn hơn 0")
    @Digits(integer = 16, fraction = 2, message = "Số tiền tối đa 16 chữ số nguyên và 2 chữ số thập phân")
    private BigDecimal discountValue;

    @NotNull(message = "Giá trị đơn hàng tối thiểu không được để trống")
    @DecimalMin(value = "0.0", message = "Giá trị đơn tối thiểu không được âm")
    @Digits(integer = 16, fraction = 2, message = "Số tiền tối đa 16 chữ số nguyên và 2 chữ số thập phân")
    private BigDecimal minOrderAmount;

    @DecimalMin(value = "0.0", inclusive = false, message = "Mức giảm tối đa phải lớn hơn 0 hoặc để trống")
    @Digits(integer = 16, fraction = 2, message = "Số tiền tối đa 16 chữ số nguyên và 2 chữ số thập phân")
    private BigDecimal maxDiscountAmount;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startDate;

    @NotNull(message = "Ngày kết thúc không được để trống")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endDate;

    @NotNull(message = "Tổng lượt sử dụng không được để trống")
    @Min(value = 1, message = "Tổng lượt sử dụng tối thiểu là 1")
    private Integer totalUsageLimit;

    @NotNull(message = "Giới hạn mỗi khách hàng không được để trống")
    @Min(value = 1, message = "Mỗi khách hàng phải được dùng tối thiểu 1 lần")
    private Integer perCustomerLimit;

    @Builder.Default
    private VoucherStatus status = VoucherStatus.ACTIVE;
}
