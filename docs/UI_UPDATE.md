# Cập nhật giao diện

Giao diện cửa hàng, tài khoản, Admin và Staff dùng chung màu sắc, kiểu form, bảng, nút và thông báo trong `static/css/ui.css`. Các trang quản lý danh mục, thương hiệu và sản phẩm đã dùng layout quản lý.

## Điều hướng

- Sidebar giữ logo và liên kết hồ sơ ở vị trí cố định; vùng menu cuộn độc lập với nội dung trang.
- Nút cuộn lên/xuống xuất hiện khi menu vượt chiều cao hiện tại. Nút ở đầu/cuối bị vô hiệu hóa tương ứng.
- Mục đang mở được đánh dấu, vị trí cuộn được giữ theo vai trò trong phiên trình duyệt.
- Trên màn hình nhỏ, nút menu mở sidebar có nền che; đóng bằng nút X, nền che hoặc Escape. Focus được giữ trong menu khi mở.
- Staff chỉ thấy các mục được phân quyền sẵn; các màn hình tài khoản và lỗi dùng layout theo vai trò.
- Danh mục sản phẩm thu gọn trên điện thoại. Bảng rộng cuộn trong vùng bảng.

Bootstrap, Bootstrap Icons và Chart.js được phục vụ từ source local. Ảnh sản phẩm không tải được dùng ảnh dự phòng local; không thay URL đang lưu trong database. Tiền VND được hiển thị thống nhất bằng dấu chấm phân nhóm nghìn.

## Phạm vi và kiểm tra

Không đổi controller, service, phân quyền, database hay cấu hình riêng của từng máy. Trang đặt mật khẩu qua email tiếp tục dùng layout riêng, không hiện danh tính Admin đang đăng nhập.

- `mvn verify`: 248 test đạt.
- Kiểm tra HTTP với SQL Server thật: 620 kiểm tra đạt; dữ liệu nghiệp vụ trước/sau không đổi (ngoại trừ thời gian đăng nhập thông thường).
- Kiểm tra trình duyệt: Admin/Staff, nút cuộn menu, màn hình 1440×900, 1024×480 và 390×844, menu Customer, tab hồ sơ, modal và báo cáo.

Một số tên danh mục/mô tả mẫu đang lưu trong database còn lỗi mã hóa tiếng Việt từ trước. Đợt cập nhật giao diện này giữ nguyên nội dung đó để không tự sửa dữ liệu chưa đối chiếu.
