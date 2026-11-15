# Đối chiếu chức năng của Đạt — 29/10/2026

## Phạm vi đã chốt trong trao đổi

Ảnh có 19 dòng. Loại Delete Account theo xác nhận của Đạt: chỉ khóa/mở khóa và giữ lịch sử. Còn 18 mục đã có hoặc được hoàn thiện; ngày 29/10/2026 Đạt xác nhận làm Admin trả lời Feedback, giữ ẩn/hiện và thông báo thay đổi schema. Không có nghiệp vụ Admin hỗ trợ/reset mật khẩu Staff; Staff tự Quên mật khẩu như Customer. Gửi lại lời mời tạo Staff ban đầu vẫn còn.

Review và Feedback là cùng đánh giá sản phẩm. Bảng `Reviews` đã tồn tại trong SQL gốc và được UC-13/FEAT-24 tham chiếu từ trước. Không tạo thêm bảng Feedback, không đổi tên bảng vì sẽ ảnh hưởng FK/view và code của nhóm. Tên giao diện dùng Đánh giá sản phẩm (Feedback).

## Ma trận theo ảnh

| # trong ảnh | Chức năng | Đối chiếu và xử lý |
|---|---|---|
| 1 | Create Account | Đã có: Admin cấp Staff, validate tên/email/SĐT, chặn trùng, gửi lời mời; lỗi gửi mail rollback. Không cấp ADMIN/đổi role. |
| 2 | View Account | Đã có: danh sách, lọc loại tài khoản/trạng thái/từ khóa, phân trang; chỉ Admin. |
| 3 | Update Account | Đã có, bổ sung validate tầng service và thông báo xung đột dữ liệu; không tự khóa hoặc khóa Admin cuối. |
| 4 | Delete Account | Bỏ theo xác nhận người dùng; không tạo endpoint, không thêm cột xóa tài khoản. |
| 5 | Lock / Unlock Account | Đã có, bảo toàn quan hệ dữ liệu và kiểm tra phiên đăng nhập ở request sau. |
| 6 | View Order List | Dùng chung `/staff/orders`; Admin được truy cập. Không nhân đôi logic hoặc controller. |
| 7 | View Order Detail | Dùng chung chi tiết đơn, snapshot giá/SKU/địa chỉ, lịch sử trạng thái. |
| 8 | Confirm Order | COD chờ xác nhận, chưa thanh toán; khóa dòng và chặn xử lý lặp. Bổ sung kiểm tra role ở service và lỗi trạng thái trên giao diện. VNPay đi theo callback xác minh thanh toán hiện có. |
| 9 | Create Voucher | Chặn mã trùng kể cả voucher đã xóa, chuẩn hóa mã, validate số tiền theo precision SQL, phần trăm <=100, thời gian/lượt dùng. |
| 10 | View Voucher | Danh sách hiện tại và trang voucher đã xóa riêng; lọc/phân trang. |
| 11 | Update Voucher | Chặn sửa voucher đã xóa, quota không thấp hơn lượt đã dùng, giữ snapshot đơn cũ. |
| 12 | Delete Voucher | Xóa mềm, không áp dụng khi đã xóa; phục hồi cùng mã về INACTIVE và giữ lịch sử/lượt dùng. |
| 13 | View Review | Đã có: Admin xem/lọc/ẩn-hiện; bổ sung link menu. Công khai chỉ VISIBLE, nội dung được escape. |
| 14 | Reply Review | Đã hoàn thiện Admin trả lời một lần, giữ ẩn/hiện, kiểm tra quyền/CSRF/đồng thời/HTML; thêm 3 cột theo xác nhận 29/10/2026. Staff không có quyền trả lời. |
| 15 | View Inventory Report | Đối chiếu nhập, số đã giữ/xuất, tồn hiện tại và chênh lệch. Không ước tính vốn khi thiếu chứng từ/tồn đầu chưa đối chiếu. |
| 16 | View Product Sales Report | Bổ sung trang `/admin/reports/product-sales`, đầy đủ sản phẩm có bán trong kỳ và phân trang, thay vì chỉ Top 10. Số lượng/tiền hàng trước giảm/số đơn từ đơn COMPLETED + PAID theo completedAt. |
| 17 | View Completed Order Revenue Report | Theo ngày/tháng/năm, tổng hàng, giảm giá, doanh thu thuần, ship và tổng thu tách riêng. Loại đơn hủy/chưa thanh toán; báo lỗi khoảng ngày đảo ngược. |
| 18 | Customer Feedback Product | Bổ sung form từ chi tiết đơn: đúng người mua, COMPLETED + PAID, 1–5 sao, nhận xét 10–1000 ký tự theo FEAT-24; khóa giao dịch + unique OrderItemId chống gửi trùng. |
| 19 | View My Feedback | Bổ sung danh sách riêng `/customer/reviews`, phân trang, chỉ dữ liệu của tài khoản hiện tại; vẫn thấy đánh giá mình bị ẩn. |

## Quy tắc và căn cứ

- FEAT-24 trong `PetShop_Product_Backlog.md` quy định 1–5 sao, nhận xét tối thiểu 10 ký tự, mỗi OrderItem một lần, chỉ đơn hoàn tất. Tối đa 1000 ký tự theo SQL gốc. Điều kiện PAID cùng COMPLETED khớp ràng buộc Orders đã có.
- Không tự thêm khiếu nại, chat hỗ trợ, đổi trả, hoàn tiền hoặc quy tắc chặn đánh giá tiêu cực. Ẩn/hiện là kiểm duyệt nội dung, không xóa đánh giá khách.
- Tham khảo [chính sách mua hàng Hachiko Pet](https://hachikopet.vn/marketplace_v2/chinh-sach/purchase) về bước cửa hàng xác nhận đơn và [Pet Mart](https://www.petmart.vn/chinh-sach-doi-tra) về đối chiếu hàng với đơn/hóa đơn. Đây chỉ là tham chiếu trải nghiệm cửa hàng; không suy diễn rằng quy trình nội bộ hoặc database của họ giống dự án, không đưa chính sách đổi trả riêng của họ vào code.
- Mã báo cáo trong ảnh là UC-28.x, tài liệu cũ là UC-29.x. Giữ nguyên code nghiệp vụ và ghi nhận khác biệt để nhóm thống nhất mã tài liệu; không tự đánh lại số UC toàn dự án.

## Phần Reply đã triển khai theo xác nhận 29/10/2026

Chỉ thêm vào `Reviews`: `ReplyText nvarchar(1000) NULL`, `RepliedByUserId bigint NULL` (FK Users), `RepliedAt datetime2(6) NULL`. Cả ba cùng NULL khi chưa trả lời. Không ghi đè Comment của khách. Admin đang hoạt động gửi một câu trả lời, không ghi đè trả lời đã có; nội dung 1–1000 ký tự, escape HTML. Khi ẩn đánh giá thì cả nhận xét và câu trả lời không xuất hiện công khai, chủ đánh giá vẫn xem được.

Migration chính thức: `database/migrations/V20261029__review_reply.sql`. Đã sao lưu Reviews trước khi áp dụng tại máy Đạt, đối chiếu nguyên vẹn dữ liệu cũ và kiểm tra chạy lại. Xem [quy tắc, kiểm thử và demo](FEEDBACK_REPLY_20261029.md); nhóm cần chạy migration trước khi khởi động code mới.

## Kịch bản kiểm tra/demo

1. Admin tạo Staff bằng thông tin duy nhất, kiểm tra trường rỗng/sai email/trùng SĐT. Staff nhận tài khoản qua email; tài khoản đã dùng tự mở Quên mật khẩu. Admin không còn nút hỗ trợ mật khẩu.
2. Khóa một Customer thử, phiên cũ mất quyền ở request tiếp theo; mở khóa cho phép đăng nhập lại. Thử khóa chính Admin bị từ chối.
3. Tạo voucher giảm phần trăm; thử 101%, ngày kết thúc trước bắt đầu, số tiền quá giới hạn. Xóa, tạo lại cùng mã bị từ chối; phục hồi giữ mã/lượt dùng và ở trạng thái ngưng áp dụng.
4. Xác nhận đơn COD chờ duyệt, bấm lại phải báo không đúng trạng thái; Customer không được gọi endpoint xử lý. Doanh thu chưa ghi nhận đơn vừa xác nhận.
5. Sau khi đơn hoàn tất/đã thanh toán, Customer đánh giá sản phẩm; thử thiếu nhận xét, sao 0/6, gửi trùng và ID sản phẩm thuộc đơn người khác. Admin xem rồi ẩn; trang sản phẩm mất đánh giá nhưng My Feedback vẫn thấy.
6. Lọc báo cáo trong ngày hoàn tất: tiền hàng trước giảm ở Product Sales khác doanh thu thuần sau giảm; phí ship tách riêng. Đảo ngày lọc phải có thông báo, không ra lỗi 500.

Kiểm thử tự động dùng H2 và mail giả lập. Không gửi thư thật, không tạo đơn/feedback thử trong PetShopDB thật. Phần Reply bổ sung đúng ba cột đã được duyệt; kiểm thử ghi trên SQL Server dùng rollback, không giữ dữ liệu thử.

## Kết quả kiểm tra bản bàn giao

- Maven verify sau bổ sung Reply: 183 test, 0 thất bại, 0 lỗi; gồm kiểm thử bảo mật/CSRF, voucher, tài khoản, luồng đơn/checkout/VNPay, Feedback và báo cáo. Các test biến động dữ liệu và gửi mail chạy trên H2/mail giả lập.
- Lần kiểm tra trước Reply: 226 kiểm tra đạt trên ứng dụng đóng gói, cấu hình SQL Server thật: 60 lượt mở trang, 160 form POST có token CSRF, 3 đăng nhập thật và 3 kiểm tra chuyển hướng/phân quyền. Chỉ đọc dữ liệu, không gửi mail hoặc tạo đơn/Feedback thật.
- Đã xem My Feedback trên trình duyệt, dùng các đánh giá đã lưu trong Reviews. Các ô lỗi dấu chưa được cho phép sửa ở lần trước vẫn giữ nguyên; đây là dữ liệu mẫu cũ, không phải luồng Feedback mới làm hỏng encoding.
- Reply đã có trong source và SQL Server máy Đạt: 3 cột nullable, một FK và một CHECK; dữ liệu cũ giữ nguyên. Service thực đã kiểm tra reply + ẩn trong transaction rollback. Không thay mật khẩu, không gửi email khi trả lời.
- Giới hạn: chưa thử gửi email thật hay thanh toán thật trong lượt này; không kết luận mọi tình huống đều hết lỗi chỉ từ số test.
## Kiểm tra bổ sung sau Reply

- Maven verify: 183 test đạt; sau chỉnh hướng dẫn form và bổ sung chặn phiên Admin bị khóa, 15 test TeamIntegrationTests chạy lại đạt.
- 231 kiểm tra ứng dụng SQL Server đạt: 60 GET, 165 form POST có CSRF, 3 đăng nhập và 3 kiểm tra chuyển hướng/phân quyền.
- 4 kiểm tra service/ORM trên SQL Server đạt trong transaction rollback; không giữ phản hồi thử hoặc đổi trạng thái đánh giá thật. Migration đã thử chạy hai lần và xác minh nguyên vẹn mọi giá trị cũ.
