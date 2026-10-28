# Cập nhật database PetShop

Bảng thay đổi để gửi nhóm và cập nhật tài liệu: [CHANGELOG_20261007.md](CHANGELOG_20261007.md). Gói SQL chia sẻ nằm trong `outputs/team_database_update_20261007`.

Database `PetShopDB` trên máy này đã được cập nhật trong lần sửa ngày 07/10/2026. Không cần chạy lại các script sửa trên database này.

Migration xóa mềm voucher cũng đã được áp dụng ngày 07/10/2026; dữ liệu voucher, ví và đơn hiện có được giữ nguyên. Bằng chứng kiểm tra và snapshot voucher trước migration nằm trong `outputs/voucher_soft_delete_20261007`.

## Bổ sung ngày 29/10/2026: Admin trả lời Feedback

Chạy `migrations/V20261029__review_reply.sql` trên database hiện có **trước khi khởi động code mới**, kể cả database vừa tạo từ bootstrap. Script thêm đúng ba cột nullable vào `Reviews`: `ReplyText NVARCHAR(1000)`, `RepliedByUserId BIGINT`, `RepliedAt DATETIME2(6)`; một FK đến `Users` và một CHECK yêu cầu cả ba cùng có giá trị hoặc cùng NULL. Không tạo bảng Feedback mới, không thay nội dung/trạng thái đánh giá cũ. Có thể chạy lại an toàn. Cấu hình Hibernate tiếp tục `validate`.

Xem [hướng dẫn gửi nhóm](sync_feedback_reply/README.md) và [nghiệp vụ](../docs/FEEDBACK_REPLY_20261029.md). Các cột cũ về Staff/voucher vẫn cần giữ; migration này không thay thế các migration trước.

## Database đang có dữ liệu

Các migration tăng dần, có kiểm tra tồn tại:

1. `migrations/V20261007__account_and_order_integrity.sql`: thêm `Users.MustChangePassword`, CHECK `COMPLETED => PAID`, index `CompletedAt`.
2. `migrations/V20261007__revenue_view.sql`: tách tiền hàng sau giảm, phí ship và tổng thu.
3. `migrations/V20261007__voucher_soft_delete.sql`: thêm `Vouchers.IsDeleted`, `DeletedAt` và CHECK xóa mềm. Chạy các batch phân cách bởi `GO` theo thứ tự; không chạy bootstrap/reset để cập nhật database có dữ liệu.

Voucher xóa mềm vẫn giữ mã duy nhất, lượt dùng, liên kết ví và đơn hàng. Danh sách Admin mặc định và ví khách hàng ẩn voucher đã xóa; Admin có bộ lọc "Đã xóa". Voucher đã xóa không thể sửa, kích hoạt, lưu vào ví hoặc dùng cho checkout mới. Hủy đơn cũ vẫn hoàn tồn và quota theo luồng hiện có.

`repair_demo_20261007.sql` chỉ dành cho 8 đơn demo đã xác minh. Nó đồng bộ 6 SKU, quota hai voucher, tổng đơn `ORD-20261004-004`, ví voucher, snapshot lịch sử và audit thanh toán giả lập. Chạy trong transaction và lưu snapshot trước. Không dùng script này như một chức năng điều chỉnh kho của ứng dụng.

## Database mới cho demo

`PetShop_Database_Final.sql` là bootstrap/reset và có DROP TABLE. Chỉ dùng cho database mới hoặc môi trường test được phép dựng lại.

Sau bootstrap, chạy `seed_demo_orders_reviews.sql`. Seed có transaction, kiểm tra số lượng/quota, chỉ trừ tồn và quota cho đơn mới; chạy lại không trừ lần nữa. Seed không tự sửa sai lệch cũ; trường hợp đó cần script repair riêng.

Thanh toán VNPay trong seed là dữ liệu giả lập: `TransactionCode` bắt đầu `DEMO-`, `SignatureVerified=NULL`, `FailureReason` nêu rõ đây không phải xác minh từ nhà cung cấp. Không dùng các dòng này làm bằng chứng thanh toán thực tế.

## Bản sao và hoàn tác

Bản source trước sửa: `outputs/fix_20261007/source_before_fix.zip`.

Snapshot dữ liệu 21 bảng trước commit: `outputs/fix_20261007/database_before_apply_1791314864511.json`. Đây là snapshot dữ liệu logic, không phải file SQL Server `.bak`; có thông tin tài khoản nên không đưa vào repository công khai.

`outputs/fix_20261007/undo_demo_repair.sql` khôi phục dữ liệu đã sửa và xóa các dòng audit do repair thêm. Script kiểm tra SHA-256 của các bảng liên quan và dừng nếu đã có phát sinh tiếp. Nó không gỡ migration schema. Chỉ sử dụng để hoàn tác ngay sau đợt sửa, không dùng để khôi phục tùy ý sau khi đã bán/nhập hàng mới.

Database test riêng còn được giữ lại: `PetShop_FixTest_20261007_0206`. Các test ghi dữ liệu và đồng thời đã chạy ở database này; `PetShopDB` chỉ được sửa bằng script repair có snapshot.
