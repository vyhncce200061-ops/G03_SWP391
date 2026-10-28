# UC-21: Cấp tài khoản Staff qua email và chính sách vai trò

Cập nhật 21/10/2026, công việc bắt đầu tối 20/10. Phạm vi quyết định: hoàn thiện quy trình cấp Staff đã có trong UC-21, đồng thời bỏ thao tác đổi role không có nghiệp vụ chuyển đổi cụ thể. Không thêm UC chuyển role.

## 1. Tạo Staff và nhận tài khoản

Tiền điều kiện: Admin đăng nhập; SMTP đã cấu hình; họ tên, email, SĐT hợp lệ và duy nhất. Khách hàng không được tự cấp Staff.

1. Admin mở Quản lý người dùng → Cấp tài khoản nhân viên, nhập họ tên/email/SĐT. Không nhập mật khẩu hoặc chọn role.
2. Hệ thống tạo Users với role STAFF, trạng thái ACTIVE, MustChangePassword=true và hash của bí mật ngẫu nhiên không được đưa cho Admin/Staff. Vì chưa ai biết bí mật này nên Staff chưa thể đăng nhập.
3. Hệ thống tạo token ngẫu nhiên, chỉ lưu SHA-256 vào PasswordResetTokens; hạn 30 phút. Token không nằm trong log hay thông báo Admin.
4. Dùng EmailService được tích hợp từ ft/auth của Bảo để gửi thư HTML có link /reset-password?token=... đến email Staff.
5. Staff mở thư, đặt và xác nhận mật khẩu; hệ thống kiểm tra token còn hạn, chưa dùng, tài khoản ACTIVE và mật khẩu hợp lệ.
6. Lưu hash BCrypt, đánh dấu token đã dùng, làm hết hạn các token còn lại và bỏ cờ MustChangePassword. Staff đăng nhập bằng email và mật khẩu vừa chọn.

SMTP lỗi/thiếu cấu hình: trả lỗi, transaction tạo Users/token được rollback; không báo thành công hoặc hiển thị mật khẩu để chuyển miệng. SMTP nhận thư không bảo đảm thư đã vào Inbox; nếu thư bị chuyển sang Spam hoặc trễ, Admin/Staff kiểm tra hộp thư và dùng quy trình gửi lại.

Trạng thái ACTIVE ở đây là quyền được phép nhận tài khoản; badge Chờ đặt mật khẩu phản ánh MustChangePassword. Không thêm giá trị trạng thái hoặc cột database mới.

Link đặt mật khẩu mở trang công khai riêng, không có tài khoản hoặc điều hướng Admin. Nếu trình duyệt đang đăng nhập, mở link hợp lệ sẽ kết thúc đúng phiên của trình duyệt đó và tải lại form với session/CSRF mới. Đây là việc tách phiên khi mở link; thao tác gửi mail vẫn không đổi mật khẩu hay ngắt các phiên khác. Tài khoản được đổi mật khẩu luôn là chủ token, không phải tài khoản đang đăng nhập trước đó.

Lưu thành công → hủy session đặt mật khẩu → trang login ở trạng thái chưa đăng nhập → Staff đăng nhập bằng email/mật khẩu vừa chọn. Nhập xác nhận sai giữ form để thử lại; link hết hạn/đã dùng/đã bị thu hồi hoặc tài khoản bị khóa hiển thị thông báo yêu cầu gửi lại, không đưa thẳng về login. Link không hợp lệ không tự đăng xuất tài khoản đang dùng.

## 2. Gửi lại lời mời

- Admin bấm Gửi lại cho Staff ACTIVE đang chờ đặt mật khẩu. Link cũ hết hiệu lực, token mới có hạn 30 phút.
- Không gửi lời mời cho Customer, tài khoản bị khóa hoặc Staff đã thiết lập mật khẩu.
- Nếu SMTP lỗi, rollback việc thay token để link cũ còn hiệu lực trong thời gian còn lại.
- Nếu Admin sửa email, link đã gửi đến email cũ hết hiệu lực; dùng Gửi lại để gửi link đến địa chỉ mới.

Thu hồi token bằng mốc UsedAt (đã dùng hoặc đã bị thu hồi), giữ nguyên ExpiresAt ban đầu. Cách này giữ CHECK ExpiresAt > CreatedAt của schema hiện có, kể cả khi gửi lại ngay trong cùng giây.

## 3. Staff tự quên mật khẩu (chốt lại 29/10/2026)

Staff, Customer và Admin tự mở Quên mật khẩu tại trang đăng nhập, nhập email, nhận link và tự chọn mật khẩu mới. Admin không còn nút hoặc endpoint gửi link hỗ trợ mật khẩu cho Staff. Không đổi mật khẩu hoặc ngắt phiên chỉ vì có yêu cầu gửi link; chỉ khi chủ email xác nhận mật khẩu mới thì phiên cũ hết hiệu lực. Tài khoản khóa không được cấp token và không thể dùng token cũ.

Gửi lại lời mời vẫn chỉ dành cho Staff ACTIVE chưa hoàn tất nhận tài khoản. Đây là bước cấp tài khoản ban đầu, không phải hỗ trợ mật khẩu của tài khoản đã sử dụng. SMTP lỗi giữ lại trạng thái trước đó; không hiển thị mật khẩu tạm.

Không xóa tài khoản. Admin khóa/mở khóa, bảo toàn lịch sử đơn và chứng từ; không đổi role.

## 4. Vì sao bỏ chuyển Staff ↔ Customer?

UC-21 hiện có cấp tài khoản Staff và quản lý tài khoản; UC-21.1 là khóa/mở. Không có đặc tả điều kiện chuyển vai trò, xử lý chứng từ đang dở, giữ quyền truy cập dữ liệu cá nhân và trách nhiệm lịch sử. Đổi RoleId đơn thuần không đủ diễn tả nghiệp vụ.

| Chuyển đổi | Dữ liệu và vấn đề cần có quy tắc nếu muốn làm sau này |
|---|---|
| Customer → Staff | Orders, Carts, UserAddresses, CustomerVouchers, WishlistItems, Reviews vẫn gắn UserId cũ nhưng Staff mất quyền Customer portal. Cần quy tắc xử lý đơn đang chờ/giao, hủy đơn, địa chỉ và quyền xem lịch sử mua hàng. |
| Staff → Customer | Products.CreatedByUserId/UpdatedByUserId, StockImports.CreatedByUserId, Orders.CancelledByUserId và OrderStatusHistories.ChangedByUserId vẫn là chứng từ công việc của nhân viên cũ. Cần bàn giao công việc, thu hồi quyền và phân biệt vai trò lúc lập chứng từ với vai trò hiện tại. |

Quy tắc áp dụng hiện tại:

- Role cố định theo tài khoản: đăng ký công khai tạo CUSTOMER, Admin cấp tài khoản mới tạo STAFF; không nâng ADMIN tại màn hình sửa.
- Staff nghỉ việc: khóa tài khoản Staff, giữ chứng từ và người thực hiện. Không biến tài khoản thành Customer hoặc chuyển chứng từ sang người khác.
- Customer được tuyển: không tự nâng quyền tài khoản mua hàng hiện có. Cấp tài khoản công việc riêng, thỏa email/SĐT duy nhất; có thể dùng thông tin công việc khác thông tin mua hàng cá nhân.
- Email đã tồn tại thì báo trùng; không tự chuyển vai trò để dùng lại email đó.
- Phần sửa tài khoản chỉ nhận họ tên, email, SĐT, trạng thái. Role hiển thị readonly, không có trường ghi role trong DTO hoặc service. Tham số roleCode/roleId gửi thủ công bị bỏ qua.

Quan hệ FK, UserId và chủ sở hữu đơn/giỏ/địa chỉ/voucher/review/chứng từ được giữ nguyên.

## 5. Tích hợp nhánh Bảo

Repo: https://github.com/vyhncce200061-ops/G03_SWP391. Nguồn EmailService: ft/auth, commit 4233a19d3fdd55fff8bc84dac64235245b8fd4d0.

Bản cập nhật 21/10 đã gộp lịch sử ft/auth tại commit trên vào nhánh tích hợp. Giữ mail HTML, xác nhận đăng ký Customer qua email, hồ sơ/ảnh đại diện, sổ địa chỉ, giao diện đơn hàng và giỏ hàng AJAX của Bảo. Bổ sung thư mời và quy trình tự quên mật khẩu của Staff, báo lỗi SMTP và không ghi link truy cập vào log. Giữ đăng nhập bằng session và CSRF của bản đã kiểm thử; JWT chỉ dùng xác nhận đăng ký, không dùng làm cookie đăng nhập. Vì vậy khóa tài khoản/đổi mật khẩu vẫn thu hồi phiên như quy định.

Đã bỏ ProfileDemoDataInitializer và lời gọi tự sinh địa chỉ/đơn hàng khi mở hồ sơ; bỏ src/test/main là bản sao source cũ, file IDE test.iml và application-test.yml trùng trong src/main. Giữ các test Maven thật tại src/test/java và cấu hình test tại src/test/resources. Chi tiết giao nhận: [AUTH_INTEGRATION_HANDOFF.md](AUTH_INTEGRATION_HANDOFF.md).

## 6. Cấu hình và demo

Các biến cần cấu hình tại máy chạy ứng dụng, không ghi mật khẩu mail vào Git:

Cách điền trực tiếp khi demo: copy `src/main/resources/application-local.yml.example` thành `application-local.yml`, điền `spring.mail.username` và `spring.mail.password`. Profile dev tự bao gồm local nên Run bình thường sẽ đọc cấu hình riêng. File local bị Git bỏ qua; file example chỉ chứa giá trị trống để chia sẻ.

File local cũng bị loại khỏi JAR khi đóng gói. Nếu chạy JAR tại thư mục dự án và muốn dùng cấu hình riêng này, thêm `--spring.config.additional-location=optional:file:./src/main/resources/`. Bản JAR chuyển cho thành viên khác dùng cấu hình SMTP của máy họ.

```text
SMTP_HOST=<máy chủ SMTP>
SMTP_PORT=587
SMTP_USERNAME=<tài khoản gửi thư>
SMTP_PASSWORD=<mật khẩu SMTP/app password>
SMTP_FROM=<địa chỉ gửi, mặc định SMTP_USERNAME>
SMTP_AUTH=true
SMTP_STARTTLS=true
PUBLIC_BASE_URL=http://localhost:8080
```

Cũng nhận MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD và APP_BASE_URL nếu dùng tên cấu hình này từ nhánh auth. PUBLIC_BASE_URL phải là URL Staff truy cập được; localhost phù hợp khi demo mở thư trên cùng máy.

Demo: Admin tạo Staff → mở hộp thư nhận → bấm link → tự đặt mật khẩu → đăng nhập vào Staff portal → thử mở lại link (bị từ chối). Để demo gửi lại, thực hiện trước khi hoàn tất đặt mật khẩu; link trước phải mất hiệu lực.

Demo role: mở màn hình sửa Staff/Customer, role chỉ đọc; thử POST roleCode khác vẫn giữ role cũ và lịch sử của cùng UserId.

Để demo Admin và Staff song song, mở link Staff trong cửa sổ ẩn danh hoặc trình duyệt khác. Nếu mở cùng trình duyệt đang đăng nhập Admin, phiên Admin của trình duyệt đó sẽ kết thúc theo chính sách tách phiên nêu trên; mật khẩu Admin không thay đổi.

Demo quên mật khẩu: Staff tự mở /forgot-password, nhận link, xác nhận mật khẩu mới rồi đăng nhập. Admin chỉ quản lý trạng thái tài khoản và gửi lại lời mời chưa nhận.

## 7. Database và triển khai

Không thêm migration/cột/bảng trong đợt này. Tận dụng Users.MustChangePassword và PasswordResetTokens đã có từ bản PetShop hiện tại. Máy đang dùng schema cũ từ GitHub main cần đồng bộ các migration trước đây trong database/migrations; đây là điều kiện của bản baseline, không phải schema mới cho gửi mail.

Không gửi email đến Staff thật trong kiểm thử tự động. Test SMTP nhận thư chỉ trên localhost và test nghiệp vụ dùng database thử riêng. Cấu hình SMTP thật và kiểm tra Inbox thực tế là bước cần làm trên máy demo.

Kết quả bản gộp ft/auth cuối ngày 21/10: 53/53 test Maven và đóng gói JAR thành công; 57/57 kiểm tra SQL Server (38 hồi quy + 19 xác nhận đăng ký/Staff) trên database thử mới, SMTP giả lập. Xác nhận chặn Customer/Admin, Staff chờ/bị khóa; gửi link giữ nguyên mật khẩu/phiên, chỉ xác nhận mật khẩu mới mới thu hồi phiên; SMTP lỗi giữ lại token trước thao tác; hồ sơ có đơn hàng render được khi không giữ phiên Hibernate mở. Không thay schema hoặc ghi vào PetShopDB thật.
