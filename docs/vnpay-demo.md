# Demo thanh toán VNPay Sandbox trên localhost

Trong application-local.yml của máy demo, thêm sandbox-return-confirmation-enabled vào nhóm vnpay và đặt true. Giá trị mặc định của cờ này là false.

Khi bật cờ, Return của khách đã đăng nhập chỉ được ghi nhận khi có chữ ký hợp lệ, terminal, mã giao dịch, số tiền và chủ đơn khớp dữ liệu đã lưu. Giao dịch phải thuộc môi trường SANDBOX và có cả hai mã kết quả 00. Tiến trình dùng chung khóa đơn và luồng xử lý thanh toán với IPN, vì vậy nhận lại Return hoặc IPN vẫn chỉ trừ kho một lần.

Nhật ký ghi rõ nguồn VNPay Sandbox Return. Đơn đã hủy hoặc hết hạn được giữ trạng thái đóng và ghi chú cần đối soát nếu có kết quả thành công đến muộn.

Máy demo có thể đặt VNPAY_SANDBOX_RETURN_CONFIRMATION_ENABLED=true thay cho cấu hình trong file. Thông tin merchant và Return URL tiếp tục lấy từ cấu hình riêng của máy.

Khách cần quay về PetShop để chế độ này ghi nhận kết quả. IPN tiếp tục phục vụ xác nhận từ máy chủ kể cả khi khách đóng trình duyệt trước lúc quay về. Với triển khai thật cần kiểm tra đường IPN đang hoạt động.

Tham khảo [đặc tả PAY của VNPay](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html) về dữ liệu, chữ ký và kết quả Return/IPN. Cờ demo là lựa chọn tích hợp của dự án cho môi trường Sandbox.
