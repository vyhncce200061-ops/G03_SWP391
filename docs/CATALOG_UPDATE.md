# Đồng bộ danh mục sản phẩm

Commit `e9deb7f` của Vy được đẩy trực tiếp lên `dev`. Bản sửa giữ giao diện mới, bộ lọc giá, tìm kiếm, thương hiệu, danh mục, loại thú cưng và các cách sắp xếp của Vy; không thêm chức năng hoặc schema database.

Các lỗi đã xử lý:

- Controller còn ba đoạn xung đột Git khiến project không biên dịch. Xử lý xung đột và giữ cả trạng thái wishlist, Feedback công khai, phân trang Feedback đã giới hạn offset.
- Phân trang làm mất `minPrice`/`maxPrice` của URL cũ. Liên kết trang tiếp tục giữ khoảng giá, keyword có ký tự đặc biệt, danh mục, thương hiệu, loại thú cưng và sắp xếp. Form dùng đúng page size đang chọn.
- Tham số `petType=dog` lọc được nhưng dropdown không giữ lựa chọn; đồng bộ dropdown theo enum đã chuẩn hóa. Giới hạn offset danh sách theo helper sẵn có để page/size rất lớn không gây lỗi JPA.
- Giá trên danh sách dùng dấu phân cách khác nhãn VNĐ; hiển thị thống nhất dạng `200.000 đ`.
- Sản phẩm không có SKU đang hoạt động bị gán giá giả `0`. DTO trả `null` cho giá chưa có, theo điều kiện giao diện sẵn có “Chưa có giá bán”/“Liên hệ”; không thay giá trong database. Giá `0` thực sự được lưu và giá của SKU hết tồn vẫn giữ nguyên.

Không đổi các mốc của bộ lọc: dưới 100.000; 100.000–300.000; 300.000–500.000; trên 500.000. Hai khoảng giữa giữ ranh giới bao gồm 300.000 như code của Vy. Không thay cách đặt đơn, thanh toán, trừ kho, voucher hoặc quyền Admin/Staff.

Kiểm chứng:

- Maven verify: 238 test đạt, không lỗi/thất bại/bỏ qua; build thành công. Có sáu test mới cho khoảng giá, trạng thái SKU, URL cũ, phân trang, ký tự đặc biệt, tham số sai, page/size lớn, giá chưa có và giá đang lưu.
- 301 kiểm tra HTTP/form/quyền/đối chiếu SQL Server đạt. Kết quả bốn bộ lọc và thứ tự sản phẩm được so sánh với truy vấn giá SKU thực tế; kiểm tra chọn lại khoảng giá và trang tiếp theo.
- Đăng nhập thật ba vai trò và kiểm tra các module đã gộp trước đó. Không gửi mail hoặc thanh toán thật. Các bảng nghiệp vụ được đối chiếu trước/sau và giữ nguyên; đăng nhập cập nhật thời điểm đăng nhập theo chức năng có sẵn.

Không có migration mới; gói database bảy cột đã bàn giao vẫn dùng được. Cấu hình SQL/mail riêng không đưa vào Git và được giữ khi đồng bộ source về máy.
