# Quy tắc xóa và ngừng hoạt động

## Danh mục

Hai thao tác có mục đích riêng, không bổ sung cột hoặc bảng:

| Thao tác | Kết quả | Điều kiện |
| --- | --- | --- |
| Ngừng hoạt động | Đặt Categories.IsActive = 0, giữ bản ghi để có thể kích hoạt lại | Danh mục tồn tại |
| Kích hoạt | Đặt Categories.IsActive = 1 | Danh mục tồn tại; danh mục con chỉ xuất hiện ở menu khi cha cũng hoạt động |
| Xóa | Xóa vĩnh viễn bản ghi Categories, không thể khôi phục | Không có danh mục con và không có sản phẩm nào tham chiếu |

- Danh mục cha và con cùng lưu trong bảng Categories, liên kết bằng ParentCategoryId.
- Kiểm tra xóa bao gồm danh mục con đã ngừng hoạt động và sản phẩm ACTIVE, HIDDEN, DISCONTINUED.
- Có thể xóa danh mục rỗng ở cả trạng thái đang hoạt động và ngừng hoạt động. Xóa con giữ lại cha và mở lại nhóm cha trên trang danh sách.
- Ngừng hoạt động cha không đổi trạng thái riêng của con hoặc sản phẩm. Kích hoạt lại cha giữ nguyên các trạng thái đó.
- Nút trạng thái gửi trạng thái đích rõ ràng; gửi lặp không đảo ngược lựa chọn.
- Mỗi thay đổi chạy trong transaction và khóa danh mục. Khóa ngoại database vẫn bảo vệ nếu sản phẩm được thêm đồng thời; xung đột trả thông báo để tải lại, không báo xóa thành công.
- Danh mục đang được dùng cần giữ lại và dùng Ngừng hoạt động. Không tự xóa con hay chuyển sản phẩm sang danh mục khác.

## Các thao tác xóa hiện có

Không phải tất cả đều là xóa mềm. Bảng này mô tả code hiện tại; thay đổi trong đợt này chỉ áp dụng cho danh mục.

| Đối tượng | Cách xử lý |
| --- | --- |
| Danh mục | Xóa cứng nếu không còn tham chiếu như điều kiện trên; có nút ngừng hoạt động riêng |
| Thương hiệu | Xóa cứng nếu không có sản phẩm tham chiếu, bao gồm sản phẩm đã ẩn/ngừng kinh doanh; nếu đang dùng thì ngừng hoạt động |
| Sản phẩm | Giữ bản ghi và chuyển HIDDEN; có thể khôi phục ACTIVE |
| Biến thể/SKU | Giữ bản ghi và chuyển INACTIVE; có thể khôi phục ACTIVE |
| Voucher trong quản trị | Xóa mềm bằng IsDeleted/DeletedAt, chuyển INACTIVE; phục hồi giữ mã và lịch sử sử dụng |
| Địa chỉ khách hàng | Xóa mềm bằng IsDeleted; xử lý lại địa chỉ mặc định, giữ địa chỉ đã chụp trong đơn hàng |
| Dòng giỏ hàng | Xóa cứng dòng giỏ hàng, giữ sản phẩm và đơn hàng |
| Mục yêu thích | Xóa cứng liên kết của khách với sản phẩm, giữ sản phẩm |
| Voucher đã lưu trong ví khách | Xóa cứng dòng CustomerVouchers của khách đó, giữ chiến dịch voucher và lịch sử đơn hàng |
| Tài khoản | Chỉ khóa/mở khóa theo phạm vi đã chốt; không có chức năng xóa |
| Đơn hàng | Hủy theo quy trình trạng thái; không xóa bản ghi |
| Feedback | Ẩn/hiện và trả lời; không có chức năng xóa |

Sản phẩm/SKU hiện giữ bản ghi bằng trạng thái, chưa có cờ xóa riêng. Không bổ sung cờ mới trong thay đổi này. Không chuyển dữ liệu đơn hàng, tồn kho, tài khoản hoặc voucher sang xóa cứng.
