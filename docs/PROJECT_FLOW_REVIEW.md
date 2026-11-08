# Kiểm tra luồng và catalog

Đợt kiểm tra này giữ nguyên các controller/service, quy tắc tài khoản, phân quyền, chuyển trạng thái đơn và thanh toán đang có. Hai lỗi hiển thị được sửa: ảnh sản phẩm nổi bật bị cắt bao bì; nút mua/yêu thích vẫn hiện với Admin/Staff dù endpoint chỉ dành cho Customer. Trang chi tiết cũng có thông báo khi không còn SKU có hàng.

## Luồng đã kiểm tra

| Nhóm | Nội dung kiểm tra |
| --- | --- |
| Authentication | Đăng nhập ba vai trò, form công khai/CSRF, đổi/quên/đặt mật khẩu qua liên kết, khóa tài khoản, đường dẫn và phiên đăng nhập |
| Admin account | Tạo Staff và lời mời qua mail, rollback khi gửi lỗi, trùng email/điện thoại, sửa thông tin, khóa/mở; bảo toàn Admin hoạt động |
| Catalog | Danh mục/thương hiệu, lọc chó/mèo/giá, sắp xếp, phân trang, từ khóa tiếng Việt, SKU, ảnh và trạng thái sản phẩm |
| Staff catalog/kho | Form chung Admin/Staff, upload ảnh hợp lệ/giả mạo, quyền sở hữu ảnh chính, nhập kho và chỉnh phiếu theo chênh lệch; tình huống đồng thời |
| Cart/checkout | Chọn dòng mua, số lượng, giá/tổng tiền phía server, CSRF, địa chỉ, voucher, lỗi thiếu hàng và rollback |
| Orders/COD | Tạo đơn chưa trừ tồn; xác nhận trừ đúng một lần; chuẩn bị → vận chuyển → hoàn tất/đã thu tiền; không chuyển trạng thái sai hoặc xử lý lặp |
| VNPay | Bộ test chữ ký/callback, đối chiếu tiền, thanh toán lặp, hạn thanh toán, thiếu tồn và rollback. Cấu hình merchant thật của máy hiện còn trống nên chưa thử giao dịch qua cổng VNPay thật |
| Voucher | Ràng buộc tiền/ngày/hạn mức, áp dụng khi checkout, tranh chấp quota, xóa mềm/khôi phục và không tái sử dụng mã đã xóa |
| Feedback | Chỉ người đã mua và hoàn tất/đã trả tiền; chống trùng/khác chủ sở hữu, validate, escaping; Admin trả lời và ẩn/hiện, Customer xem feedback của mình |
| Reports | Doanh thu đơn hoàn tất/đã trả tiền, tiền hàng/phí ship/giảm giá, bán sản phẩm theo snapshot, tồn kho theo lượng thực sự đã phân bổ |
| Profile/tiện ích | Hồ sơ, avatar, địa chỉ, wishlist, ví voucher và các quyền truy cập tương ứng |

`fullCodJourneyKeepsReceiptVoucherStockFeedbackAndReportsAligned` đi xuyên suốt bằng HTTP trên H2 riêng: nhập kho → thêm giỏ → checkout có voucher → Staff xác nhận/chuẩn bị/giao/hoàn tất → Customer đánh giá → Admin trả lời/ẩn → đối chiếu bán hàng và tồn kho. Không tạo đơn thử hoặc gửi mail cho người thật trong SQL Server đang dùng.

## Kết quả

- `mvn verify`: **250 test đạt**, không lỗi/failure, build thành công.
- **640 kiểm tra HTTP** với SQL Server thật và ba vai trò; các trường hợp form/filter sai vẫn được xử lý. Dữ liệu nghiệp vụ trước/sau kiểm tra không đổi, ngoại trừ thời điểm đăng nhập.
- **47 kiểm tra catalog/ảnh/SQL**: đủ ảnh thật, hash file đúng, UTF-8, lọc/phân trang, SKU và tồn kho.
- Kết nối và xác thực SMTP thật đạt; không gửi mail thử cho ai.
- Kiểm tra trình duyệt desktop/mobile: phân trang, ảnh không dùng fallback, chọn SKU, Guest chuyển sang đăng nhập, Customer đăng nhập và giao diện không tràn ngang.

## Dữ liệu

Đã sao lưu đầy đủ database bằng `COPY_ONLY` và `RESTORE VERIFYONLY` trước cập nhật. Bổ sung **15 sản phẩm, 21 SKU, 17 ảnh local**; ngừng bán 3 mẫu chưa xác minh, giữ nguyên lịch sử và tồn cũ. Sau cập nhật có **20 sản phẩm lưu trong DB, 17 đang bán, 30 SKU**. Toàn bộ chênh lệch tồn bằng 0.

Chỉ sửa/bổ sung dữ liệu, **không thêm bảng/cột**. Script đồng bộ và nguồn ảnh ở [database/demo_catalog](../database/demo_catalog/README.md); chạy lại không tạo trùng hoặc cộng tồn hai lần. Giá và tồn mới là dữ liệu demo được ghi rõ trong phiếu nhập.
