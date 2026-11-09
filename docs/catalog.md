# Danh mục và thương hiệu

Homepage hiển thị một giá bán: giá thấp nhất trong các SKU đang hoạt động của sản phẩm. Không thay đổi giá từng SKU hoặc cách chọn biến thể ở trang chi tiết.

Form thêm và sửa thương hiệu dùng chung popup, gồm tên và mô tả. Tên được bỏ khoảng trắng đầu/cuối, kiểm tra bắt buộc, trùng tên không phân biệt hoa/thường, tối đa 150 ký tự; mô tả tối đa 1000 ký tự. Nhập sai sẽ mở lại popup và giữ dữ liệu. Trường logo cũ trong database được giữ nguyên, không cần chạy SQL.

Danh mục sử dụng cấu trúc hai cấp hiện có:

- Không chọn cha: danh mục cấp cha. Chọn một danh mục cấp cha: danh mục con.
- Khi sửa, form nạp đúng cha đang lưu. Danh sách cha chỉ chứa cấp gốc, không chứa chính danh mục đang sửa.
- Không cho chọn chính mình, chọn danh mục con làm cha hoặc đưa danh mục đang có con xuống cấp con.
- Danh mục con được xếp ngay dưới cha theo thứ tự hiển thị. Các ô chọn danh mục sản phẩm hiện đường dẫn “Cha / Con”.
- Chọn cha để lọc sản phẩm sẽ lấy cả sản phẩm gắn trực tiếp với cha và sản phẩm của các con; chọn con chỉ lấy sản phẩm của con. Cửa hàng, Admin và Staff dùng cùng quy tắc này.
- Giữ khả năng gắn sản phẩm trực tiếp với cấp cha như dữ liệu hiện có.
- Ngừng danh mục cha ẩn cả nhánh khỏi menu cửa hàng. Không thay đổi trạng thái danh mục con hoặc trạng thái bán sản phẩm. Bật lại cha không tự bật những con đang ngừng. Sản phẩm vẫn có thể xuất hiện trong tìm kiếm và danh sách chung theo trạng thái bán của chính sản phẩm.
- Khi sửa sản phẩm đang gắn với một danh mục ngừng hiển thị, vẫn giữ lựa chọn hiện có để không làm mất phân loại khi cập nhật thông tin khác.

Không thêm, xóa cột hoặc cập nhật dữ liệu SQL trong thay đổi này. Các thành viên chỉ cần lấy code mới.

Kiểm thử: `CatalogHierarchyTests`, `CatalogUpdateTests`, `TeamIntegrationTests`, `PortalLayoutTests`; chạy toàn bộ bằng `mvn verify`. Các ca kiểm tra bao gồm nhập sai, mã trùng, phân cấp sai, giữ lựa chọn khi báo lỗi, lọc cha/con, giá SKU, quyền Admin và CSRF.
