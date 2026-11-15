# Logo thương hiệu

Pull dev rồi chạy 01_BRAND_LOGOS.sql trong database PetShopDB đang dùng.

Script chỉ điền Brands.LogoUrl đang trống cho 10 thương hiệu có sẵn. Chạy lại an toàn, logo Admin đã chọn được giữ lại. Đây là cập nhật dữ liệu, dùng cột có sẵn.

Ảnh gốc nằm trong src/main/resources/static/images/brands và được đóng gói cùng ứng dụng. sources.json ghi nguồn và SHA-256 của từng ảnh. Me-O và SmartHeart từ website hãng, các logo còn lại từ thư viện thương hiệu công khai của Pet Mart.

Admin tải ảnh trong popup thêm/sửa thương hiệu. Ảnh tải lên được kiểm tra và lưu tại uploads/brands. Khi chuyển máy chạy ứng dụng, cần chuyển cả thư mục uploads nếu muốn mang theo logo Admin đã tải lên.

Trang chủ liên kết tới bộ lọc brandId. Chỉ thương hiệu hoạt động có sản phẩm và SKU hoạt động được hiển thị. Tìm kiếm keyword khớp tên, mô tả sản phẩm hoặc tên thương hiệu và kết hợp được với các bộ lọc khác.
