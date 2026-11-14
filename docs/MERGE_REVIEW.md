# Đối chiếu và gộp chức năng vào dev

Rà ngày 03/11/2026, từ `dev` ở `e692744`. Nhánh tích hợp: `update-features`.

| Nhánh thành viên | Commit đã rà | Kết quả |
|---|---|---|
| `ft/auth` | `9109d2a` | Gộp phần chuẩn hóa đăng nhập, ghi thời gian đăng nhập, cập nhật hồ sơ/avatar/phiên đăng nhập và validate địa chỉ của Bảo. |
| `home-page-Vy` | `087b40b` | Gộp gợi ý danh mục/sản phẩm khi tìm kiếm, giữ giao diện catalog mới hơn của Vy đã có trong `dev` tại `e692744`; hỗ trợ cả tham số lọc/sort cũ và mới. |
| `feature/staff-module-v2` | `18b9fd5`, `328957b` | Gộp giao diện Staff mới, ảnh sản phẩm, chọn ảnh chính, sửa dòng nhập kho, xử lý đơn và phân trang. |
| `feature/cart-checkout`, `feature/staff-module` | `3c15ee6`, `2d21955` | Đã là tổ tiên của `dev`; không có commit chức năng mới cần gộp thêm. |
| Các nhánh Admin/Feedback, runtime và tích hợp cũ | Các commit đã có trong `dev` | Giữ nguyên tài khoản, voucher xóa mềm/phục hồi, Feedback và trả lời Admin, báo cáo, checkout và bảo mật token. |

Các nhánh gốc của thành viên giữ nguyên. `main` không được cập nhật trong lượt này.

## Các chỗ đã hòa giải hoặc sửa để chức năng chạy đúng

- Hồ sơ dùng đúng giao diện Customer/Staff/Admin. Giữ tải avatar, CSRF cho form sửa địa chỉ và link My Feedback; không quay lại giao diện Customer thiếu dữ liệu cho Admin. Bảo bổ sung refresh phiên sau cập nhật thông tin/mật khẩu, validate SĐT/địa chỉ và LastLoginAt.
- Kiểu CategoryId/BrandId vẫn là Integer theo schema chung. Catalog nhận `sortBy=price_asc/price_desc` lẫn link `sort=priceAsc/priceDesc`, và khoảng giá cũ. Trang chi tiết tiếp tục có Feedback, trả lời Admin, SKU khả dụng và lỗi 404 khi sản phẩm không công khai.
- Các controller quản lý sản phẩm tiếp tục dùng DTO có validate và service chung; không quay lại bind entity trực tiếp của bản giao diện cũ. Danh sách/form dùng đúng base path Admin/Staff, link SKU dùng endpoint Staff vốn cho phép cả Admin. Phân trang giữ bộ lọc và encode ký tự đặc biệt.
- Bản Staff thêm chọn ảnh chính nhưng service chưa lưu lựa chọn. Đã nối việc lưu, kiểm tra ảnh thuộc đúng sản phẩm, khóa sản phẩm khi sửa, bỏ cờ ảnh cũ rồi flush trước khi chọn ảnh mới để tôn trọng unique index SQL Server. Giữ Set và thứ tự ảnh chính trước để tránh fetch hai bag của Hibernate; không đổi cột hay cấu trúc bảng.
- Giữ tạo phiếu nhập có validate, mã phiếu không trùng khi tạo cùng giây, chống trùng SKU và transaction. Phần sửa phiếu mới kiểm tra số lượng/đơn giá, phiếu tương ứng, quyền Staff/Admin, khóa dòng phiếu và SKU; cập nhật tồn bằng chênh lệch, chặn giảm vượt tồn và chống hai lượt sửa trừ lặp. Không mở chức năng sửa tồn trực tiếp bên ngoài luồng nhập kho.
- Giữ ý định của nhánh Staff cho phép từ chối đơn VNPay đã thu tiền trước đóng gói. Trong dự án hiện tại, callback xác thực chuyển thẳng sang CONFIRMED và CHECK `CK_Orders_PendingConfirm` chỉ cho COD chờ xác nhận. Vì vậy từ chối VNPay dùng CONFIRMED + PAID; không đưa đơn về trạng thái COD hoặc sửa CHECK. Khi từ chối, giữ PAID và giao dịch SUCCESS, hoàn tồn/voucher đúng một lần, ghi lý do và cảnh báo cần đối chiếu hoàn tiền riêng. Không gọi API hoàn tiền, không tuyên bố đã hoàn tiền. Customer vẫn không được tự hủy đơn đã trả tiền; sau PREPARING cũng không được từ chối bằng thao tác này.

## Database và đồng bộ máy thành viên

Đợt gộp này **không thêm/xóa cột, bảng hoặc ràng buộc database**, không có migration mới. Máy đã đồng bộ sáu cột Staff/voucher/Feedback ở đợt trước chỉ cần lấy code `dev` và chạy lại. Máy chưa đồng bộ vẫn dùng gói `PetShop_Database_Update.zip` đã bàn giao: cập nhật schema rồi kiểm tra, không chạy bootstrap/reset hoặc sửa dữ liệu demo hàng loạt.

## Kiểm chứng

- Maven verify: 191 test đạt, không thất bại/lỗi. Bao gồm 22 test TeamIntegrationTests: form ngoài transaction, quyền/CSRF, Feedback/reply, ảnh, nhập kho, phân trang, checkout và cạnh tranh tồn kho.
- SQL Server thật: 12 kiểm tra service/ràng buộc và dữ liệu trước/sau đạt. Các thao tác thử sửa phiếu, đổi ảnh chính và từ chối đơn VNPay nằm trong transaction rollback; đối chiếu bảy bảng nghiệp vụ giữ nguyên dữ liệu. Không gửi mail hoặc thanh toán/hoàn tiền thật.
- 237 kiểm tra ứng dụng đóng gói trên SQL Server đạt: 69 GET, 162 form POST có CSRF, 3 đăng nhập và 3 kiểm tra chuyển hướng/phân quyền. Đăng nhập cập nhật LastLoginAt theo chức năng của Bảo.
- Giới hạn: không xác minh gửi mail thật/VNPay thật trong lượt gộp; phản hồi phụ thuộc cấu hình máy và môi trường của từng thành viên. Dữ liệu mẫu lỗi dấu chưa xác minh vẫn giữ nguyên.

Bằng chứng cục bộ trong `outputs/merge-review`: `verify-delivery.log`, `sql-smoke.log`, `live-routes.tsv`. Không đưa log, cấu hình local, thông tin SMTP hoặc backup riêng vào Git.
