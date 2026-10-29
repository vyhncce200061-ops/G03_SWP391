# Bản tích hợp dev — 26/10/2026

## Phạm vi

Gộp các nhánh đang có trên remote vào một bản chung, giữ lịch sử và không ghi đè nhánh gốc của thành viên:

| Nguồn | Commit | Chức năng giữ lại |
|---|---|---|
| dev / main | d0c3d68 | Nền dự án |
| ft/auth | 33704b7 | Authentication của Bảo, nhận lời mời Staff, Admin/Voucher/Reports đã tích hợp |
| staff-mail-role-policy | cc7c089 | Đã nằm trong ft/auth, không gộp lặp |
| feature/staff-module | 2d21955 | Sản phẩm, SKU, ảnh, nhập kho, xử lý đơn |
| hantgce201539-ops-patch-1 | 6dc70f1 | Quy tắc cập nhật giỏ hàng |
| hantgce201539-ops-patch-2 | 5d1614b | Thông báo và phản hồi giỏ hàng |
| home-page-Vy | 91c6bae | Giao diện, catalog, tìm kiếm/lọc, danh mục, thương hiệu, quản lý sản phẩm Admin |
| feature/cart-checkout | 3c15ee6 | Cập nhật trên remote sau đợt gộp đầu: chọn dòng giỏ hàng, checkout, lịch sử đơn và VNPay Sandbox |

Nhánh home-page-Vy có lịch sử Git độc lập. Bản gộp giữ nó làm merge parent, đối chiếu thay đổi nội dung với cây source nền d0c3d68 để tránh đưa lại toàn bộ mã nền cũ.

## Các điểm sửa khi giải quyết xung đột

- Dùng chung ProductFormDto có validation cho Admin và Staff. Giữ upload ảnh, SKU và tồn kho của Staff; giữ giao diện/tìm kiếm của Vy. Link form và phân trang đi đúng khu vực Admin/Staff. Sản phẩm ẩn/ngừng bán vẫn quản lý được; khách chỉ xem/mua sản phẩm ACTIVE và SKU ACTIVE còn hàng.
- Giữ giới hạn tên/mô tả sản phẩm 250/1000 theo database chung. Tạo SKU có tồn bằng 0; cập nhật SKU không ghi lại tồn kho. Nhập kho qua DTO gọi cùng lõi cập nhật tồn nguyên tử; không nhập trùng SKU trong một phiếu. Mã phiếu có hậu tố tránh trùng khi tạo cùng giây.
- Ảnh mới lưu ở uploads/products, được phục vụ ngoài JAR; ảnh mẫu đã có trong static vẫn được phục vụ. Kiểm tra loại/kích thước và nội dung ảnh, đặt tên ngẫu nhiên. Thư mục upload runtime không đưa vào Git.
- Giữ đăng nhập session, kiểm tra CSRF cho cả Staff, khóa tài khoản và cách ly phiên khi mở link mật khẩu. Không đưa lại bộ lọc JWT cũ; JWT vẫn phục vụ xác thực email của Bảo. Cấu hình SMTP và khóa riêng không đưa vào Git/JAR.
- Giữ cả AJAX và form thường khi cập nhật giỏ hàng. Số lượng 0 xóa dòng rồi dừng; kiểm tra chủ giỏ, trạng thái và tồn kho trước khi tăng số lượng; không lưu số âm hoặc vượt tồn.
- Nối đúng tham số bộ lọc, link danh mục trang chủ, sort và phân trang. Giữ lọc thương hiệu, loài thú cưng, khoảng giá và tính giá từ SKU đang bán. Sửa link cảnh báo tồn và hồ sơ cá nhân cho đúng role.
- Sửa danh mục cha rỗng; chặn tự làm cha/vòng lặp/cây vượt hai cấp, kiểm tra độ dài theo schema và lưu trạng thái trên form sửa. Không thay cấu trúc bảng.
- Trang liên hệ chưa có backend /contact/send: bỏ form gửi vào route lỗi, giữ thông tin và link email hỗ trợ. Không triển khai thêm chức năng gửi/lưu tin nhắn.
- Bỏ khai báo thư viện mail lặp, dùng JDBC do Spring Boot quản lý. Không đưa lại bản sao source trong src/test/main hoặc file HTML tìm kiếm ảnh bị dùng nhầm làm ảnh.

## Kiểm chứng

- Đợt gộp đầu vào dev qua PR #7: 67 test đạt. Sau khi feature/cart-checkout xuất hiện, bản tích hợp tiếp được build sạch ngày 27/10 bằng Maven `clean verify`: 174 test đạt, không failure/error; tạo được JAR.
- 6 test tích hợp mới chạy với open-in-view tắt và không dùng transaction bao quanh test: render trang, form Admin/Staff, nhập kho, giỏ AJAX/form, danh mục/thương hiệu, upload ảnh và rollback ảnh không hợp lệ.
- SQL Server: 38 kiểm tra hồi quy, 7 kiểm tra riêng Stock/SKU và 12 kiểm tra vòng đời VNPay đạt. Bao gồm nhập kho đồng thời, nhập kho cùng lúc checkout, giữ tồn khi sửa SKU và hoàn tồn khi hủy; chữ ký sai, IPN thành công/hủy/lặp, callback đến muộn và xử lý hết hạn. View đối chiếu tồn không có chênh lệch sau kiểm thử. VNPay được kiểm tra bằng merchant/chữ ký giả lập nội bộ, không gọi gateway thật.
- Script đồng bộ ba cột: 6 kiểm tra trên database fixture riêng đạt, gồm chạy lại không trùng và giữ dữ liệu cũ.
- Kiểm thử SQL chỉ dùng database PetShop_FixTest_* / PetShop_SyncTest_* riêng. Không chạy DDL/DML trên PetShopDB đang dùng; không gửi email thật trong đợt kiểm thử này.

Các kiểm tra trên xác nhận phạm vi đã chạy, không thay thế kiểm thử chấp nhận toàn bộ nghiệp vụ của từng thành viên.

### Các điểm nối thêm cho feature/cart-checkout

- Giữ authentication, email xác nhận và reset mật khẩu của Bảo; không đưa lại JWT login filter. Test CSRF của nhánh checkout được đổi sang dùng session/header thực tế, giữ kiểm tra thiếu token bị chặn.
- Giữ API JSON chọn dòng/cập nhật số lượng và giao diện checkout/lịch sử đơn của Hân. Route /cart/update cũ vẫn hỗ trợ form/AJAX để tương thích; API mới yêu cầu số lượng nguyên dương, xóa qua route riêng.
- Nối lại markCustomerVoucherUsed/restoreCustomerVoucher để checkout và hủy thanh toán giữ đúng trạng thái ví cùng quota. Giữ kiểm tra tài khoản CUSTOMER/ACTIVE và khóa customer/cart trong checkout.
- Dùng chung ShippingFeeConfig cho giỏ, quote và đơn hàng; không để phí preview lệch phí lưu. Nạp dòng đơn theo danh sách ID của trang để render sau khi transaction đóng.
- Sửa fixture test để tài khoản có role và trạng thái hợp lệ với bộ kiểm tra phiên, dùng database test riêng; sửa câu SQL test dùng schema dbo và khoảng trắng đơn vị tiền trên giao diện. Không tắt kiểm tra bảo mật hoặc bỏ bài test để đạt kết quả.
- Cấu hình tùy chọn VNPAY_TMN_CODE, VNPAY_HASH_SECRET, VNPAY_RETURN_URL chỉ lấy từ môi trường/cấu hình riêng. Thiếu merchant hợp lệ thì giao diện không cho chọn VNPay và bước đặt thanh toán bị chặn trước khi tạo đơn. Chưa thử kết nối merchant thật.

## Nhận source và database

1. Commit/stash phần đang làm trên máy mình, rồi fetch và nhận nhánh dev. Không ép ghi đè thay đổi chưa lưu.
2. Nếu dùng database cũ đã có dữ liệu, chọn database dự án trong SSMS và chạy database/sync_existing/01_SYNC_REQUIRED_COLUMNS.sql. Script chỉ bổ sung ba cột đã được code dùng từ trước và CHECK xóa mềm, không nạp lại seed hay ghi đè dữ liệu.
3. Giữ Users.MustChangePassword, Vouchers.IsDeleted và Vouchers.DeletedAt. Xóa chúng sẽ làm lỗi code hoặc mất trạng thái lời mời/xóa mềm. Các migration cũ khác được ghi rõ trong database/sync_existing/README.md để đối chiếu riêng.
4. Tạo src/main/resources/application-local.yml từ file .example rồi điền cấu hình riêng trên máy, hoặc dùng biến môi trường. Profile dev nạp local. Không commit mật khẩu SMTP/DB hoặc khóa ký; dùng application-local.yml.example làm mẫu.
5. Chạy `mvn verify`, rồi `mvn spring-boot:run`. Nếu thiếu Maven trên PATH, dùng Maven từ IDE. Mở http://localhost:8080 sau khi ứng dụng khởi động thành công.
