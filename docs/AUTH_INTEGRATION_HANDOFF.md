# Bàn giao bản gộp Admin và ft/auth — 21/10/2026

Nguồn auth: ft/auth, commit 4233a19. Nhánh tích hợp: staff-mail-role-policy (tên hiện tại trên GitHub). Bản gộp bao gồm các sửa lỗi Admin/Voucher/Reports, toàn vẹn tài khoản/đơn hàng đã làm trước đây, và quy trình Staff qua email.

## Hành vi sau tích hợp

- Customer đăng ký → nhận link xác nhận có hạn 24 giờ → xác nhận mới tạo tài khoản CUSTOMER và giỏ hàng. Link hết hạn/sai/đã xác nhận không tạo thêm tài khoản.
- Đăng nhập giữ Spring Security Form Login, session và CSRF. Token ký của Bảo chỉ dùng cho xác nhận đăng ký. Khóa tài khoản, đổi mật khẩu hoặc thay quyền trong database làm phiên cũ hết hiệu lực ở request tiếp theo.
- Admin tạo Staff bằng họ tên/email/SĐT → gửi lời mời dùng một lần, hạn 30 phút → Staff tự đặt mật khẩu. Không chọn hay đọc mật khẩu Staff trên màn hình Admin.
- Admin hỗ trợ mật khẩu chỉ cho STAFF ACTIVE đã nhận tài khoản; gửi link giữ nguyên mật khẩu/phiên đến khi Staff tự xác nhận mật khẩu mới. Staff chờ dùng Gửi lại; Customer/Admin tự dùng Quên mật khẩu.
- Giữ giao diện hồ sơ/ảnh đại diện, địa chỉ, danh sách/chi tiết đơn và giỏ hàng AJAX của Bảo. Upload avatar kiểm tra nội dung ảnh, giới hạn 5 MB, lưu lại thành PNG với tên do server tạo. File upload lưu dưới uploads/avatars; đó là dữ liệu runtime riêng của máy.
- Đọc trang hồ sơ không tự thêm đơn hoặc địa chỉ mẫu. Danh sách đơn tải sẵn các dòng hàng của trang hiện tại trong transaction riêng, giữ đúng phân trang SQL và tránh lỗi LazyInitializationException khi render giao diện.
- Quyền CUSTOMER/STAFF cố định theo tài khoản; không có chuyển role từ màn hình quản lý.

## Những phần đã dọn

Xóa 146 file trong src/test/main vì đây là bản sao source/resource cũ, không nằm trong nguồn test Maven. Xóa test.iml và application-test.yml trùng trong src/main/resources. Xóa bộ tự tạo dữ liệu ProfileDemoDataInitializer và filter cookie JWT không dùng. Giữ 6 lớp test đang chạy cùng src/test/resources/application-test.yml.

## Cấu hình và database

application-local.yml trên máy đã được đổi sang SMTP Bảo cung cấp. Xác thực SMTP và STARTTLS thành công, không gửi mail thật trong phép thử kết nối. File này chứa bí mật riêng, bị loại khỏi Git và JAR; không gửi kèm source cho nhóm. Thành viên khác tự tạo file từ application-local.yml.example hoặc dùng biến môi trường MAIL_USERNAME/MAIL_PASSWORD (cũng nhận SMTP_USERNAME/SMTP_PASSWORD).

JWT_SECRET hoặc app.jwt.secret trong file local cần là chuỗi ngẫu nhiên ít nhất 32 ký tự để link đăng ký sống qua lần restart. Máy hiện tại đã có khóa riêng. Nếu để trống, ứng dụng dùng khóa tạm trong bộ nhớ; restart làm link đăng ký trước đó mất hiệu lực. Không chia sẻ một khóa mẫu cố định.

Không thêm migration/cột/bảng trong lần gộp auth này. GitHub ft/auth gốc còn dùng baseline schema cũ: trước khi chạy bản gộp, nhóm cần dùng database bootstrap đã sửa hoặc áp dụng các migration đã bàn giao trước đây trong database/migrations theo README. Không chạy script tạo lại database lên dữ liệu cần giữ. Những migration này thuộc các sửa lỗi trước lần gộp hiện tại.

## Kiểm chứng và demo

Kết quả test/build lưu tại outputs/auth_integration_20261021 của workspace làm việc; bản source chia sẻ chỉ chứa test Maven có thể chạy lại, không chứa log/private config.

53/53 test Maven đạt; JAR đóng gói thành công. 57/57 kiểm tra SQL Server đạt trên database thử (38 hồi quy nghiệp vụ/giao diện + 19 xác nhận email/Staff). Có kiểm tra ngoài transaction phát hiện và xác nhận đã sửa lỗi tải Order.items khi mở hồ sơ có đơn. SMTP thật đã xác thực thành công bằng STARTTLS; chưa gửi mail đến người nhận thật trong lần kiểm thử này.

### Sửa trang login trắng sau tích hợp

Trình duyệt truy cập lần đầu có thể gặp Cannot create a session after the response has been committed: Spring Security trì hoãn tạo token CSRF/session đến lúc Thymeleaf xử lý form, trong khi layout lớn đã bắt đầu gửi HTML. Đã buộc khởi tạo token/session sớm tại CsrfTokenRequestHandler, giữ XOR masking và kiểm tra CSRF bình thường.

Bản sửa này đạt 54/54 test Maven, gồm test HTTP qua Tomcat thật với cookie mới cho login/register/forgot-password và đăng nhập/đăng xuất Customer, Staff, Admin. Kiểm tra trực tiếp bản JAR tại localhost:8080 xác nhận HTML đầy đủ và có CSRF. Bộ 57 kiểm tra SQL Server ở trên là kết quả bản gộp trước sửa CSRF; không chạy lại trong lần sửa này, không đổi database. Chi tiết log và PID ứng dụng local đang chạy được ghi ở outputs/login_fix_20261021 của workspace làm việc.

### Tách phiên khi mở link mật khẩu Staff

Trang đặt mật khẩu nay dùng layout công khai riêng, không hiển thị email, menu hoặc quyền của Admin. Mở link hợp lệ trong trình duyệt đang đăng nhập sẽ kết thúc phiên hiện tại và chuyển lại đúng link với session/CSRF mới. Bỏ qua ràng buộc trạng thái của phiên cũ riêng tại endpoint reset-password; endpoint vẫn kiểm tra token/hạn dùng/trạng thái chủ token và CSRF trên POST. Phiên cũ hoặc cờ MustChangePassword không còn đẩy người nhận link sang login/account-password trước khi họ thấy form.

Sau khi lưu, session đặt mật khẩu bị hủy và login hiển thị ở trạng thái chưa đăng nhập. Nhập xác nhận sai giữ form; link hết hạn/đã dùng/đã gửi lại hoặc tài khoản bị khóa hiện lỗi rõ và không có form ghi mật khẩu. Không tự đăng xuất phiên hiện có khi link không hợp lệ. Gửi email vẫn giữ nguyên mật khẩu và các phiên đang dùng; chỉ việc mở link hợp lệ mới tách phiên tại trình duyệt mở link.

Đã bỏ khung Hỗ trợ tài khoản ở danh sách người dùng theo yêu cầu. Bổ sung no-store/no-referrer cho phản hồi reset và autocomplete=new-password cho các ô mật khẩu. Dùng bảng/token hiện có, không đổi schema, không thêm dữ liệu tài khoản thật trong test.

Demo theo thứ tự: đăng ký Customer và xác nhận email → đăng nhập → xem/sửa hồ sơ → thêm địa chỉ và giỏ hàng → xem đơn đã có. Với Admin: cấp Staff → Staff nhận thư/đặt mật khẩu → đăng nhập Staff → gửi link hỗ trợ và xác nhận rằng phiên cũ chỉ bị thu hồi sau khi Staff đặt mật khẩu mới. Đường link localhost phải được mở trên máy đang chạy ứng dụng.
