package com.petshop.dto.staff;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffActivityDto {
    private String actionType;      // ORDER, STOCK_IMPORT
    private String referenceCode;   // Mã đơn hàng / Mã phiếu nhập
    private String title;           // Tiêu đề hành động (ví dụ: "Duyệt & Chuẩn bị đơn hàng", "Tạo phiếu nhập kho")
    private String detail;          // Chi tiết (ví dụ: "Chuyển trạng thái sang ĐANG GIAO", "Nhà cung cấp: CP Pet")
    private String badgeClass;      // Bootstrap badge class
    private String iconClass;       // Bootstrap icon
    private String targetUrl;       // Đường dẫn chuyển đến chi tiết (/staff/orders/1)
    private LocalDateTime timestamp;
}
