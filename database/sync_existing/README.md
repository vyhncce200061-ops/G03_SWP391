# Đồng bộ database cho nhóm — 26/10/2026

Khuyến nghị giữ ba cột và cập nhật tăng dần cho máy thành viên. Không nên xóa chúng chỉ để giống database cũ: source hiện đang map và truy vấn trực tiếp nên có thể lỗi Invalid column name, mất trạng thái chờ nhận Staff hoặc không còn phân biệt được voucher xóa/tạm khóa.

| Cột | Chức năng đang dùng | Giá trị cho dữ liệu cũ khi thêm |
|---|---|---|
| Users.MustChangePassword | Staff chờ tự đặt mật khẩu, gửi lại lời mời, phân biệt với hỗ trợ mật khẩu | false; tài khoản cũ giữ nguyên mật khẩu/trạng thái |
| Vouchers.IsDeleted | Xóa mềm, ẩn khỏi checkout/ví/danh sách chính, phục hồi | false; voucher cũ vẫn xuất hiện như trước |
| Vouchers.DeletedAt | Thời điểm xóa, CHECK xóa mềm và thông tin danh sách phục hồi | NULL |

01_SYNC_REQUIRED_COLUMNS.sql chỉ thêm cột còn thiếu và CHECK xóa mềm trong transaction; chạy lại không thêm trùng. Không DROP TABLE/COLUMN, không nạp seed, không thay PasswordHash, không reset tồn kho/quota hoặc đơn hàng. Chọn đúng database dự án trong SSMS rồi chạy; script tự chặn database hệ thống và sai schema. Nếu dữ liệu xóa mềm sẵn có vi phạm CHECK, toàn bộ lần cập nhật sẽ rollback và báo lỗi, cần kiểm tra trước khi sửa dữ liệu.

Không cần gửi file database mới ghi đè database của thành viên. Gửi script này cùng source đã tích hợp. Nên có bản sao lưu database của mỗi máy trước khi cập nhật để có điểm khôi phục riêng.

Các thay đổi trước đây ngoài ba cột cũng cần ghi nhận: CHECK Orders.COMPLETED phải PAID, index IX_Orders_CompletedAt, view vw_RevenueByDay bổ sung tách tiền hàng/phí ship/tổng thu. Chúng nằm trong database/migrations của source cũ; script tối thiểu ở đây không tự áp dụng ba thay đổi đó. Chỉ đồng bộ sau khi đối chiếu nghiệp vụ Staff và dữ liệu máy thành viên, đặc biệt các đơn COMPLETED nhưng chưa PAID. Không chạy repair_demo_20261007.sql hoặc bootstrap lên database đang có dữ liệu bán hàng để đồng bộ schema.

Kiểm tra chỉ đọc trên máy hiện tại: có đủ ba cột; có 1 voucher đang xóa mềm và 0 tài khoản đang chờ đổi mật khẩu tại thời điểm rà soát; 21 bảng, 31 khóa ngoại. Xóa cờ voucher hiện tại sẽ mất cách xác định voucher đang bị xóa. Chưa áp dụng thay đổi cấu trúc hoặc dữ liệu nào lên PetShopDB trong đợt rà soát này.

Bản tích hợp giữ Products.Name 250 ký tự và ShortDescription 1000 ký tự theo schema chung, đồng bộ validation form theo các giới hạn này. Không mở rộng cột theo mapping 500/2000 của nhánh giao diện.
