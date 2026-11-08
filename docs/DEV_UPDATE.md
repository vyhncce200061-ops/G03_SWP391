# Bản gộp dev

Các nhánh cũ đã có trong lịch sử `dev` được giữ nguyên. Bản này gộp hai phần còn thiếu: `fix/order-service-logic` của Hà và `feature/wishlist-my_vourcher` của Hân, trên nền bản Authentication mới nhất của Bảo (`28f4bd3`). Không sửa lịch sử các nhánh thành viên hoặc nhánh main.

## Nghiệp vụ giữ từ code thành viên

- Hủy/từ chối: chỉ đơn chưa thanh toán và chưa xử lý; Staff từ chối COD đang chờ xác nhận. Đơn đã PAID không có nút từ chối và service cũng chặn thao tác. Những đơn đã hủy/đã thu tiền từ trước vẫn giữ thông tin lịch sử để đối chiếu.
- Wishlist: Customer thêm/gỡ sản phẩm yêu thích của mình, thêm trùng không tạo dòng lặp; giữ cả sản phẩm đã ngừng bán trong danh sách đã lưu để khách có thể gỡ.
- Ví voucher: hiển thị khả dụng, chưa đến hạn, hết hạn, tạm ngưng, hết lượt; giới hạn dựa trên đơn còn hiệu lực. Xóa rồi lưu lại vào ví không làm mất lịch sử sử dụng.
- Tiếp tục VNPay: dùng lại đơn/giao dịch và thời hạn ban đầu; không tạo đơn mới hay gia hạn thanh toán. Chỉ chủ đơn được thực hiện, có CSRF.
- Đơn mới chưa trừ kho khi checkout. COD trừ kho lúc Staff/Admin xác nhận; VNPay trừ sau IPN đã xác minh và thanh toán thành công. Đơn PAID nhưng thiếu hàng giữ PAID, chờ bổ sung hàng rồi xác nhận, không thu tiền lần nữa. Callback lặp không trừ thêm.
- Đơn cũ đã trừ kho giữ cờ phân bổ; xác nhận không trừ lần hai, hủy hợp lệ hoàn đúng phần đã trừ. Voucher vẫn giữ quota ở checkout và trả quota khi hủy hợp lệ.

## Xử lý xung đột

Giữ tìm kiếm/gợi ý/lọc sản phẩm của Vy, Feedback công khai và trả lời Admin của Đạt, rồi bổ sung trạng thái wishlist của khách. Giữ layout/thông báo tự đóng của Bảo và thêm hai menu Wishlist/Ví voucher. Đồng bộ thông báo wishlist với layout; bỏ nút từ chối đơn PAID theo bản sửa của Hà; giữ xử lý lỗi của controller khi xác nhận/từ chối thất bại. Không thay phạm vi quyền của thành viên.

Test cũ được đổi theo thời điểm phân bổ mới: cạnh tranh giảm phiếu nhập so với xác nhận đơn, giới hạn voucher và báo cáo kho, quyền xác nhận của Staff, không hủy đơn PAID. Có thêm kiểm tra JSON/CSRF của wishlist và việc layout vẫn chứa Feedback, wishlist, thông báo đúng một lần.

## Database

Đạt đã đồng ý cập nhật trước khi áp dụng. Chỉ bổ sung `Orders.StockDeducted BIT NOT NULL DEFAULT 0`, điều chỉnh `CK_Orders_PendingConfirm` và đồng bộ **view có sẵn** `vw_StockReconciliation` với cờ phân bổ. Không thêm bảng hoặc trạng thái mới. Sáu cột Staff/voucher/Feedback của các lần trước vẫn cần giữ.

Chạy [migration và hướng dẫn](../database/sync_order_stock/README.md) trước khi chạy code. Migration đánh dấu đơn cũ chưa hủy là đã phân bổ theo checkout cũ, chỉ khi cột vừa được thêm; chạy lại không ghi đè cờ hiện có. Không thay tồn, tiền, trạng thái đơn, tài khoản hoặc dữ liệu khách. Seed demo cũng dùng cờ này và chỉ trừ kho cho các đơn mẫu đã xử lý; không chạy seed để đồng bộ máy đang có dữ liệu.

Phần quy tắc từ chối VNPay/giữ kho trong `MERGE_REVIEW.md` là lịch sử của bản gộp trước; nghiệp vụ hiện tại theo tài liệu này và hai nhánh thành viên nêu trên.

## Kiểm chứng

- Maven verify: 230 test đạt, không thất bại/lỗi/bỏ qua; đóng gói ứng dụng thành công.
- SQL Server thật: 27 kiểm tra nghiệp vụ/ràng buộc và dữ liệu trước/sau; thao tác thử nằm trong transaction rollback.
- 276 kiểm tra HTTP/form/phân quyền, đăng nhập thật ba vai trò; chỉ gửi form nghiệp vụ không hợp lệ, không phát sinh đơn/mật khẩu/email mới.
- Migration: 18 kiểm tra trên database tách biệt (bảo vệ database hệ thống, backfill một lần, chạy lại, kiểu cột sai, rollback, view kho). Gói SQL tổng hợp và seed: thêm 16 kiểm tra trên database dựng riêng đủ 21 bảng; chạy lại giữ cờ, lời mời Staff, voucher đã xóa và trả lời Admin.
- Không gửi mail, thanh toán hoặc hoàn tiền thật trong đợt kiểm thử này. Không đưa cấu hình local, mật khẩu hoặc snapshot riêng vào Git.
