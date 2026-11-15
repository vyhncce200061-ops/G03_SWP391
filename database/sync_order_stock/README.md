# Đồng bộ tồn kho đơn hàng

Dùng với bản gộp wishlist/đơn hàng mới trên `dev`. Đây là thay đổi cần cho code của Hân, đã được Đạt đồng ý: một cột `Orders.StockDeducted`, điều chỉnh CHECK `CK_Orders_PendingConfirm`, cập nhật view đối soát kho đang có. Không thêm bảng, trạng thái đơn hoặc dữ liệu mẫu.

1. Dừng ứng dụng và sao lưu database của máy mình.
2. Chọn đúng database dự án trong SSMS. Nếu chưa có sáu cột Staff/voucher/Feedback cũ, chạy các script đồng bộ cũ trước, hoặc dùng gói `PetShop_Database_Update.zip` mới nhất do Đạt gửi (đã gom toàn bộ).
3. Chạy toàn bộ [V20261108__order_stock_allocation.sql](../migrations/V20261108__order_stock_allocation.sql). Không chạy bootstrap/reset trên DB có dữ liệu.
4. Chạy [VERIFY.sql](VERIFY.sql), rồi lấy code `dev` mới và khởi động với `ddl-auto=validate`.

`StockDeducted = 1` nghĩa là đơn đã lấy hàng khỏi tồn và chưa hoàn lại. Khi cột vừa được thêm, đơn cũ chưa hủy được đánh dấu 1 theo checkout cũ; đơn đã hủy là 0. **Không tự chạy UPDATE hàng loạt sau đó:** đơn mới chờ xác nhận/chờ thanh toán có thể đúng là 0. Chạy lại migration giữ cờ hiện có.

COD mới trừ kho lúc xác nhận, VNPay mới trừ sau IPN thành công. Nếu đã PAID nhưng thiếu hàng, đơn chờ bổ sung hàng rồi xác nhận; giữ nguyên giao dịch thành công. View SQL và báo cáo ứng dụng đều chỉ tính các đơn thực sự đã trừ kho. Đây là cách xử lý trong nhánh của thành viên; không có cơ chế hoàn tiền tự động mới.

Với DB mới, chạy bootstrap, các migration/gói đồng bộ đầy đủ, sau đó mới chạy seed demo nếu cần. Seed chỉ dành cho môi trường demo mới, không dùng để cập nhật schema.
