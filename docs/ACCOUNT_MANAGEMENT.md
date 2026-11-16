# Quyền quản lý tài khoản

Phạm vi đã chốt: bảo vệ thông tin đăng nhập và hồ sơ của người dùng, giữ chức năng khóa/mở khóa. Không có xóa tài khoản, chuyển vai trò hoặc Admin đặt lại mật khẩu cho người khác.

| Tài khoản được quản lý | Quyền Admin |
|---|---|
| Customer | Xem thông tin, khóa/mở khóa. Customer tự cập nhật hồ sơ cá nhân. |
| Staff | Xem, sửa họ tên, khóa/mở khóa; giữ thao tác gửi lại lời mời khi chưa đặt mật khẩu. |
| Admin | Xem, khóa/mở khóa Admin khác theo ràng buộc hiện có. Mỗi Admin tự cập nhật hồ sơ cá nhân. Không tự khóa hoặc khóa Admin hoạt động cuối cùng. |

Email và SĐT đăng nhập không được sửa tại trang quản lý tài khoản, kể cả bằng request giả mạo. Form sửa Staff chỉ nhận họ tên, tối đa 150 ký tự, có nội dung và không chứa ký tự điều khiển/ẩn. Không bắt tên phải chỉ gồm chữ Latin hoặc có độ dài tối thiểu tùy ý.

## Luồng thao tác

1. Admin mở danh sách người dùng, lọc/tìm tài khoản. Nút mắt mở chi tiết chỉ xem; chỉ Staff có nút sửa.
2. Form sửa Staff hiển thị email/SĐT/vai trò/trạng thái dưới dạng thông tin, không phải trường gửi lên server. POST chỉ cập nhật họ tên. Mã tài khoản lấy từ URL; trường ID hoặc các thông tin khác trong body bị từ chối.
3. Form không đổi trạng thái, không đổi email/SĐT, mật khẩu, vai trò hoặc cờ chờ đặt mật khẩu; không thu hồi hay gửi token. Đơn hàng và dữ liệu liên quan giữ nguyên chủ sở hữu.
4. Khóa/mở khóa dùng thao tác riêng. Request gửi trạng thái đích ACTIVE hoặc LOCKED, có CSRF. Gửi lặp cùng trạng thái không đảo ngược kết quả. Request thiếu/sai trạng thái hoặc form cũ được từ chối và yêu cầu tải lại danh sách.
5. Tầng service kiểm tra người thực hiện vẫn là Admin ACTIVE tại thời điểm xử lý. Các thao tác quản trị tài khoản được tuần tự hóa qua khóa vai trò Admin và khóa bản ghi đích, giữ nguyên bảo vệ Admin cuối và tự khóa. Form sửa mở trước khi tài khoản bị khóa không thể mở lại tài khoản khi lưu họ tên.

Trang hồ sơ cá nhân của Customer/Staff/Admin giữ luồng hiện có; chủ tài khoản tự sửa thông tin được cho phép tại đó. Email không thay đổi từ form quản trị. Chưa bổ sung quy trình đổi email có xác minh, cũng không sử dụng việc tạo tài khoản khác hoặc dữ liệu giả để lách ràng buộc định danh duy nhất. Nếu cần sửa email/SĐT đã nhập nhầm khi cấp Staff, phải xác định quy trình xác minh riêng trước khi mở lại quyền sửa định danh.

## Liên quan Authentication và database

- Tạo Staff qua email, gửi lại lời mời và tự quên mật khẩu giữ luồng [STAFF_ONBOARDING_ROLE_POLICY.md](STAFF_ONBOARDING_ROLE_POLICY.md).
- Sửa họ tên không đổi cách đăng nhập, email nhận thư, mật khẩu hoặc hiệu lực link nhận tài khoản đang có. Mật khẩu, token và hash không hiển thị trong trang chi tiết.
- Không sửa cấu trúc database hoặc dữ liệu tài khoản thật; không cần migration mới.
- Kiểm thử dùng H2 và email giả lập: giả mạo trường, phân quyền, CSRF, tên không hợp lệ, bản ghi không tồn tại, form cũ, đăng nhập sau khi sửa, link mời còn hiệu lực, gửi lặp khóa/mở khóa và thao tác đồng thời.

Tham chiếu bảo mật: [OWASP – Changing a User's Registered Email Address](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html#changing-a-users-registered-email-address). Đây là thay đổi nhạy cảm cần quy trình xác minh; dự án hiện chọn giữ định danh cố định trong trang Admin thay vì đổi trực tiếp.
