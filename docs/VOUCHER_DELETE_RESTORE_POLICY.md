# UC-26: Xóa mềm và phục hồi voucher — 21/10/2026

Danh sách quản lý mặc định chỉ hiển thị voucher chưa xóa. Đã bỏ lựa chọn Danh sách / Chưa xóa vì trùng ý nghĩa của trang. Nút Voucher đã xóa mở danh sách riêng; tìm kiếm, lọc trạng thái và phân trang vẫn giữ đúng danh sách đang xem.

Mã voucher duy nhất trong toàn bộ dữ liệu, kể cả voucher đã xóa. Tạo hoặc sửa voucher khác sang mã đã xóa bị từ chối và hướng dẫn phục hồi. Form có liên kết đến Voucher đã xóa theo mã đang nhập. Không giải phóng mã khi xóa mềm.

Admin vào Voucher đã xóa → bấm Phục hồi → xác nhận → voucher trở về danh sách quản lý, trạng thái Tạm khóa/INACTIVE. Admin kiểm tra hạn dùng và lượt còn lại, chỉnh sửa khi cần rồi kích hoạt. Phục hồi không tự chạy lại một chương trình cũ.

Phục hồi dùng chính VoucherId và code cũ; đặt IsDeleted=false, DeletedAt=null, Status=INACTIVE. Giữ CreatedAt, thời hạn, điều kiện giảm giá, tổng hạn mức, UsedCount, giới hạn từng khách, đơn hàng/snapshot và bản ghi ví voucher. Khách đã dùng hết giới hạn vẫn không được dùng thêm chỉ vì voucher được phục hồi. Voucher hết hạn/hết quota cần được Admin chỉnh điều kiện hợp lệ trước khi dùng tiếp; không tự làm mới lượt.

POST /admin/vouchers/{id}/restore chỉ cho Admin và cần CSRF. Khóa ghi trên voucher để tuần tự hóa với sửa/xóa/thay trạng thái. Yêu cầu phục hồi lặp lại không thay đổi trạng thái voucher đã phục hồi, kể cả voucher đã được kích hoạt. Mã không tồn tại trả lỗi trên danh sách đã xóa.

Dùng IsDeleted, DeletedAt và unique Code hiện có; không thêm bảng/cột/migration, không chạy sửa dữ liệu PetShopDB trong kiểm thử. UpdatedAt thay đổi khi phục hồi; lịch sử đơn hàng và số lượt không bị xóa. Không thêm bảng audit cho thao tác phục hồi trong phạm vi này.

Demo: tạo mã DEMO_RESTORE → xóa → thử tạo lại DEMO_RESTORE (bị chặn và có hướng dẫn) → mở Voucher đã xóa → Phục hồi → thấy cùng mã ở Tạm khóa → kiểm tra/chỉnh thời hạn và hạn mức → kích hoạt → áp mã theo điều kiện bình thường. Với voucher đã có đơn, so sánh UsedCount và đơn hàng trước/sau để chứng minh phục hồi không làm mới quota.
