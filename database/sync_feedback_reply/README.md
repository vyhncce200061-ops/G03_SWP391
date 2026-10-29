# Đồng bộ Admin trả lời Feedback — 29/10/2026

1. Sao lưu database đang dùng trên máy của bạn; không dùng database của người khác để ghi đè dữ liệu.
2. Trong SSMS chọn đúng database PetShop của bạn. Chạy file `V20261029__review_reply.sql` gửi cùng gói này (trong repository: `database/migrations/V20261029__review_reply.sql`). Script có transaction, có thể chạy lại và sẽ dừng nếu cột sẵn có không đúng kiểu.
3. Pull nhánh `dev` có chức năng Admin trả lời Feedback, rồi build/run lại. Giữ `ddl-auto=validate`.
4. Đăng nhập Admin → Đánh giá sản phẩm → Trả lời đánh giá. Customer xem ở chi tiết sản phẩm hoặc Đánh giá của tôi. Staff không có quyền trả lời.

## Thay đổi cần cập nhật ERD/tài liệu

| Bảng | Cột mới | Kiểu / ý nghĩa |
|---|---|---|
| Reviews | ReplyText | NVARCHAR(1000) NULL; nội dung trả lời của cửa hàng |
| Reviews | RepliedByUserId | BIGINT NULL; FK đến Users.Id, Admin thực hiện |
| Reviews | RepliedAt | DATETIME2(6) NULL; thời điểm gửi câu trả lời |

Ràng buộc `FK_Reviews_RepliedByUser` liên kết người trả lời. `CK_Reviews_ReplyComplete` yêu cầu cả ba cột cùng NULL hoặc cùng có giá trị và nội dung không rỗng. Tài khoản ACTIVE/ADMIN được kiểm tra trong service và request. Dữ liệu cũ có ba cột NULL; nội dung, sao, trạng thái, đơn hàng và tài khoản giữ nguyên.

Review/Feedback cùng lưu trong Reviews. Không thêm bảng, không xóa cột cũ, không reset dữ liệu, không đổi mật khẩu và không gửi mail khi trả lời. Không cần cấp quyền này cho Staff theo phạm vi đã chốt.

Gói này chỉ là bổ sung Feedback, không thay thế `database/sync_existing/01_SYNC_REQUIRED_COLUMNS.sql` và các migration cũ. Nếu thiếu các cột Staff/voucher, đồng bộ chúng theo hướng dẫn trước đây. Không chạy file bootstrap DROP TABLE để cập nhật một database đang có dữ liệu.

Kiểm tra sau chạy:
```sql
SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='Reviews'
  AND COLUMN_NAME IN ('ReplyText','RepliedByUserId','RepliedAt');
```
Kết quả phải có đúng 3 dòng. Lần cập nhật này không sửa các ô dữ liệu mẫu lỗi dấu chưa được xác minh.
