# Catalog phục vụ demo

Gói này **chỉ cập nhật dữ liệu**, không thêm bảng/cột và không thay cấu hình. Kéo source mới để có ảnh local trước khi chạy SQL.

1. Sao lưu database đang dùng. Chọn database `PetShopDB` trong SSMS.
2. Nếu máy chưa đồng bộ schema trước đây, chạy gói `database/sync_existing` và migration tồn kho đã được nhóm thống nhất trước.
3. Chạy `01_DEMO_CATALOG.sql`. Script dùng transaction, có thể chạy lại; chỉ cộng tồn cho SKU vừa được tạo trong lần đó.
4. Chạy `02_VERIFY.sql`. Khởi động lại ứng dụng và tải lại trang.

## Dữ liệu thay đổi

- Thêm 15 sản phẩm, 21 SKU, 5 thương hiệu và 1 phiếu nhập `IMP-CATALOG-DEMO`.
- Bổ sung ảnh local có nguồn cho 2 sản phẩm thức ăn cũ và 15 sản phẩm mới. Sửa 2 tên danh mục lỗi mã hóa và mô tả lỗi dấu của bộ vòng cổ/dây dắt; sửa alt text ảnh đang dùng.
- Ba mẫu ban đầu được ngừng bán: Me-O quy cách 8 lít chưa đối chiếu được (thêm mới quy cách 5 lít có ảnh đúng), bóng cao su S/M và bộ vòng cổ/dây dắt S/M chưa xác minh được mẫu cụ thể. Giữ nguyên sản phẩm/SKU cũ để bảo toàn lịch sử đơn, giá và tồn; chỉ bỏ các đường dẫn ảnh mẫu bị thiếu của ba mẫu này.
- Giữ nguyên tài khoản, mật khẩu, đơn hàng, thanh toán, voucher, feedback và ảnh chụp tên/giá trong đơn cũ. Giá/tồn của 9 SKU cũ không thay đổi.

**Giá bán, giá nhập và lượng tồn thêm mới là dữ liệu demo**, không phải báo giá thị trường hay hóa đơn nhập thực tế. Giá bán có thể được quản lý cập nhật theo nghiệp vụ hiện có. Phiếu nhập được ghi rõ là giả lập. Không tự chạy script này khi khởi động ứng dụng.

## Nguồn và ảnh

`catalog.json` ghi URL trang sản phẩm, URL ảnh gốc, kích thước và SHA-256 cho từng file. Ảnh được lưu nguyên nội dung tải về trong `src/main/resources/static/images/products/`, không dùng ảnh tạo sinh hay lấy ảnh khác gắn nhầm sản phẩm.

- Royal Canin: trang hãng, đối chiếu tên, đối tượng và quy cách; ví dụ [Poodle Adult](https://www.royalcanin.com/vn/dogs/products/retail-products/poodle-adult-3057), [Indoor 27](https://www.royalcanin.com/vn/cats/products/retail-products/indoor-27-2529).
- Whiskas 85 g: [Genshai](https://genshai.com.vn/products/8853301550031).
- Các sản phẩm INABA, CIAO, MEOWOW, BOBO, DELE: [Pet Mart](https://www.petmart.vn/); URL từng sản phẩm trong manifest. Pet Mart công bố [CC BY-ND 4.0](https://creativecommons.org/licenses/by-nd/4.0/); ảnh được giữ nguyên và ghi nguồn.
- Me-O Lemon Scent: [Fago Pet](https://fagopet.vn/san-pham/cat-ve-sinh-cho-meo-me-o-cat-litter-lemon-scent/).

Thông tin mô tả được viết ngắn theo loại sản phẩm; không thêm công dụng điều trị hoặc cam kết an toàn không được xác minh.
