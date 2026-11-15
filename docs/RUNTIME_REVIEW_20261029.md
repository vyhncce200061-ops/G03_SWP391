# Rà soát bản chạy thực tế — 29/10/2026

Đã tái hiện trang chủ/catalog trả 500 và đăng nhập thất bại trên đúng project petshop người dùng chạy từ IntelliJ. Nguyên nhân chung là mật khẩu datasource hiệu lực khác mật khẩu SQL Server của máy, mã lỗi 18456. Kiểm thử trước dùng cấu hình kết nối riêng nên chưa phát hiện cách chạy trực tiếp từ IDE. Các tài khoản mẫu vẫn ACTIVE và mật khẩu mẫu khớp BCrypt; không cần reset tài khoản.

| Phần | Lỗi xác nhận | Xử lý |
|---|---|---|
| Khởi động/kết nối | File local chưa ghi đè datasource của máy | Sửa file local bị Git bỏ qua; bổ sung datasource vào file example và hướng dẫn phân biệt mật khẩu SQL với mật khẩu tài khoản web |
| Kiểm tra database | ddl-auto none cho ứng dụng khởi động dù không kết nối được SQL | Dùng validate để kiểm tra kết nối/schema khi khởi động, không tạo/sửa bảng; xác nhận mật khẩu SQL sai làm startup thất bại |
| Hồ sơ Admin/Staff | Template dùng chung gửi form cập nhật/ảnh/mật khẩu sang customer-only routes | Nối đúng account/profile, account/profile/avatar và account/password; dùng lại UserService hiện có, không mở quyền customer routes cho Admin/Staff |
| Hồ sơ Customer | Generic account/profile không nạp đầy đủ dữ liệu Customer | Chuyển Customer từ account/profile sang customer/profile; giữ đầy đủ đơn hàng và địa chỉ |
| Sửa địa chỉ | Form có action do JavaScript đặt nên không tự sinh token CSRF | Bổ sung token cho modal sửa địa chỉ ở cả trang hồ sơ và trang sổ địa chỉ; giữ kiểm tra CSRF phía server |
| Dashboard Admin | Hai link cảnh báo kho trỏ vào stock-imports/low-stock không tồn tại | Sửa sang staff/products/low-stock |
| Đăng nhập/giao diện lỗi | Checkbox ghi nhớ không có backend; nút/chữ trên nền đậm khó đọc | Bỏ checkbox chưa hoạt động, tăng tương phản nút/footer và đặt nội dung trang lỗi trên nền sáng; không thêm cơ chế đăng nhập lưu dài hạn |

Kiểm chứng bản cuối: Maven verify đạt 177 test, không failure/error; gồm ba test mới cho form hồ sơ theo role, sửa địa chỉ dùng token lấy từ HTML và upload avatar Admin/Staff trên database H2 riêng. Bài kiểm tra HTTP login/logout có kiểm tra chuyển Customer về trang hồ sơ đầy đủ. Các test dùng mail giả lập.

Trên ứng dụng thật kết nối PetShopDB: 228 kiểm tra đạt, gồm 57 lượt mở trang, 165 lượt kiểm tra token của form POST, đăng nhập thật Admin/Staff/Customer, hai kiểm tra chặn truy cập Admin và kiểm tra chuyển trang hồ sơ Customer. Phạm vi có Admin dashboard/user/voucher/reports; Staff sản phẩm/SKU/nhập kho/đơn; Customer hồ sơ/địa chỉ/giỏ/checkout/lịch sử đơn; guest catalog và các trang auth. Các lượt này không gửi email thật, không đặt/thanh toán đơn và không tự sửa mật khẩu tài khoản đang dùng. Không suy rộng kết quả thành đã thử merchant VNPay hoặc SMTP thật trong đợt này.

Dữ liệu text được rà riêng: 108 ô có dấu hiệu lỗi mã hóa, 82 ô khôi phục khớp chính xác với file SQL mẫu. Người dùng xác nhận chỉ sửa 82 ô đó và giữ nguyên 26 ô chưa xác minh. Đã tạo snapshot giá trị trước/sau và script hoàn tác trước khi UPDATE; tất cả cập nhật trong một transaction có kiểm tra giá trị cũ. Đã đọc lại 82 ô, đối chiếu 26 ô còn lại và hash các trường mật khẩu/role/trạng thái tài khoản trước/sau. Không thêm cột/bảng, không reset seed, không đổi mật khẩu/quyền, không điều chỉnh số liệu tồn/đơn/voucher.

Lưu ý khi nhận source: cấu hình datasource và SMTP riêng không đưa lên Git/JAR. Mỗi máy điền đúng mật khẩu SQL Server trong application-local.yml hoặc DB_PASSWORD. Nếu database thành viên chưa có ba cột từ đợt trước, dùng database/sync_existing theo README; bản sửa ngày 29/10 không yêu cầu cột mới. Hai nhánh ft/auth và home-page-Vy có commit mới trên remote sau đợt gộp trước; đợt sửa này giữ base dev bf90aeb và chỉ sửa lỗi đã tái hiện, không tự nhập thêm chức năng từ các commit mới đó.
