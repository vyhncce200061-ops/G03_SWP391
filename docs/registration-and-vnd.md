Token xác nhận đăng ký dùng JWE với AES-256-GCM. Nội dung tài khoản chờ kích hoạt được mã hóa; đăng nhập vẫn dùng session và mật khẩu vẫn lưu bằng BCrypt như trước. Link JWT chỉ ký kiểu cũ bị từ chối, người chưa kích hoạt cần đăng ký lại để nhận link mới. Tài khoản đã kích hoạt và token quên mật khẩu trong database không thay đổi.

Trong application-local.yml riêng của máy, thêm registration-encryption-key dưới app.jwt. Giá trị là Base64 của 32 byte ngẫu nhiên, tạo một lần và giữ qua các lần chạy. Có thể dùng biến REGISTRATION_ENCRYPTION_KEY khi cấu hình theo application-local.yml.example. Không chia sẻ khóa trong chat hoặc đưa vào Git. Nếu để trống, ứng dụng dùng khóa tạm và link đăng ký hết hiệu lực khi khởi động lại.

Tạo giá trị trên PowerShell rồi lưu thẳng vào cấu hình riêng:

```powershell
$registrationBytes = New-Object byte[] 32
$registrationRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$registrationRng.GetBytes($registrationBytes)
[Convert]::ToBase64String($registrationBytes)
$registrationRng.Dispose()
```

Giá bán, giảm giá cố định, giá trị đơn tối thiểu, mức giảm tối đa và phí giao hàng dùng nguyên đồng. Giá trị như 10000.00 từ cột DECIMAL hiện có vẫn hợp lệ. Tỷ lệ phần trăm được giữ hai chữ số thập phân. Tiền giảm phần trăm làm tròn HALF_UP về đồng trong service dùng chung cho báo giá và checkout; sau đó tính tổng đơn. Ví dụ 18.000đ giảm 3,33% được giảm 599đ; cộng phí 30.000đ thành 47.401đ. Tổng/giá của đơn đã tạo không được cập nhật lại.

Không có migration hoặc cột/bảng mới trong thay đổi này. Các quy tắc hủy, hoàn tiền và xử lý tồn kho được giữ nguyên.
