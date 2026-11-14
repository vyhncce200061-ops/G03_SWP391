# Rà soát chức năng của Đạt — 08/11/2026

## Kết luận và phạm vi

Cả **18 mục chức năng đã chốt đều có trong source và đã được đối chiếu/kiểm thử**. Trong các ca rà soát lần này, phát hiện và sửa 7 nhóm lỗi đầu vào/điều hướng/giới hạn kỹ thuật; bổ sung xử lý thông báo lỗi và giải thích đúng điều kiện ước tính tồn kho. Không bổ sung nghiệp vụ, đổi phân quyền, thêm/xóa bảng/cột hoặc chạy script sửa dữ liệu.

Nguồn nghiệp vụ là các quyết định đã chốt trong trao đổi và `DAT_FUNCTION_REVIEW_20261029.md`, `FEEDBACK_REPLY_20261029.md`. Không tự áp quy định của cửa hàng khác vào dự án. Bản được rà bắt đầu từ dev `8f61b98`.

- Giữ khóa/mở khóa tài khoản; không có xóa tài khoản hoặc chuyển đổi role.
- Admin tạo Staff bằng lời mời đặt mật khẩu qua email; Staff đã nhận tài khoản dùng Quên mật khẩu của Authentication. Không khôi phục nút Admin hỗ trợ/reset mật khẩu.
- Review/Feedback cùng bảng Reviews; Admin trả lời một lần, giữ ẩn/hiện; Staff không có quyền này.
- Voucher xóa mềm, mã vẫn duy nhất kể cả đã xóa, phục hồi cùng chương trình về INACTIVE.
- Không thêm giới hạn chống giữ kho/đơn chờ, hoàn tiền tự động hoặc các chức năng ngoài phân công.

## Đối chiếu 18 mục

| # | Chức năng | Các điều kiện và lỗi đã kiểm tra | Kết quả |
|---|---|---|---|
| 1 | Tạo tài khoản Staff | Tên rỗng/quá dài; email/SĐT sai hoặc trùng; role cố định STAFF; mật khẩu không được Admin nhập; email mời có token; lỗi SMTP rollback tài khoản/token; lời mời gửi lại thu hồi link cũ; đã nhận tài khoản/đang khóa không gửi lại lời mời | Đạt trong các ca kiểm tra |
| 2 | Xem tài khoản | Admin truy cập; Guest/Customer/Staff bị chặn; lọc/từ khóa/phân trang; trang âm, size ngoài giới hạn và offset vượt giới hạn JPA | Đạt; sửa lỗi offset lớn |
| 3 | Cập nhật tài khoản | Validate tên/email/SĐT/trạng thái; trùng thông tin không lưu; role không đổi qua request; đổi email thu hồi token cũ; ID trên đường dẫn là nguồn xác định bản ghi; form lỗi giữ đúng ID | Đạt; sửa regex SĐT và ID form lỗi |
| 4 | Khóa/mở khóa tài khoản | Không tự khóa; không khóa Admin hoạt động cuối; hai Admin khóa nhau đồng thời chỉ một thành công; phiên cũ bị kiểm tra lại; giữ lịch sử liên quan | Đạt |
| 5 | Xem danh sách đơn | Chỉ Admin/Staff; lọc trạng thái, phân trang, offset quá lớn; dữ liệu hiển thị ngoài transaction | Đạt; sửa lỗi offset lớn |
| 6 | Xem chi tiết đơn | Thông tin người nhận/dòng hàng/giá snapshot/lịch sử; ID không tồn tại; giới hạn role | Đạt; ID không tồn tại trả 404 thay vì lỗi server |
| 7 | Xác nhận đơn | COD + PENDING_CONFIRMATION + UNPAID; chặn Customer, thiếu CSRF, sai trạng thái và xác nhận lặp; khóa đơn khi xử lý; không trừ kho thêm; VNPay xác nhận qua callback đang có | Đạt |
| 8 | Tạo voucher | Mã chuẩn hóa/độ dài/ký tự; mã trùng cả đã xóa; phần trăm >100; số âm/0/precision sai; ngày thiếu/đảo ngược; lượt dùng không hợp lệ; request không tự đặt UsedCount hoặc trạng thái xóa | Đạt |
| 9 | Xem voucher | Tìm kiếm/lọc/phân trang; danh sách thường và đã xóa tách riêng; quyền/CSRF cho thao tác; offset quá lớn | Đạt; sửa lỗi offset lớn |
| 10 | Cập nhật voucher | Không sửa voucher đã xóa; không giảm quota dưới lượt đã dùng; chặn mã trùng; snapshot đơn cũ giữ nguyên; form lỗi không chuyển sang tạo mới/sửa voucher khác | Đạt; sửa ID form lỗi |
| 11 | Xóa voucher | Xóa mềm, INACTIVE, giữ mã/thời điểm/lượt dùng/ví/đơn; xóa lặp không đổi thời điểm; mã đã xóa không áp dụng mới; phục hồi không làm mới hạn hoặc reset quota và vẫn INACTIVE | Đạt |
| 12 | Xem Feedback | Admin lọc/số sao/trạng thái/phân trang; nội dung HTML được escape; chỉ VISIBLE công khai/tính sao; ẩn/hiện giữ nội dung/câu trả lời; lỗi và bộ lọc sau chuyển trạng thái | Đạt; giữ thêm size khi quay lại danh sách |
| 13 | Trả lời Feedback | ACTIVE ADMIN; Staff/Customer và phiên Admin bị khóa bị chặn; thiếu CSRF; rỗng/quá 1000 ký tự; ID sai; tác giả lấy từ principal; gửi lặp/hai Admin cùng gửi; ẩn đồng thời không mất câu trả lời; escape HTML | Đạt |
| 14 | Báo cáo tồn kho | Phiếu nhập − số giữ/xuất của đơn chưa hủy = tồn dự kiến; đơn chờ vẫn giữ hàng; hủy hoàn tồn; có chênh lệch hoặc thiếu giá thì không tự ước tính vốn; không sửa tồn khi xem báo cáo | Đạt; làm rõ nhãn dữ liệu chưa đủ |
| 15 | Báo cáo bán sản phẩm | Chỉ COMPLETED + PAID theo CompletedAt; số lượng/số đơn/tiền hàng trước giảm và chưa ship; phân trang; mốc cuối tháng; ngày đảo ngược/ngoài phạm vi và offset lớn | Đạt; sửa tràn phép tính ngày mặc định/offset |
| 16 | Báo cáo doanh thu hoàn tất | Theo ngày/tháng/năm; loại đơn chưa hoàn tất/hủy; tách tiền hàng/giảm giá/net/ship/tổng thu; ranh giới ngày cuối tháng và đầu tháng; khoảng ngày sai; báo cáo không có dữ liệu | Đạt |
| 17 | Customer đánh giá sản phẩm | Đúng người mua; COMPLETED + PAID; 1–5 sao; nhận xét sau trim 10–1000 ký tự; mỗi OrderItem một lần; request giả ID người mua; trùng/đồng thời; chỉ Customer và CSRF hợp lệ | Đạt |
| 18 | My Feedback | Chỉ lịch sử của người đăng nhập; bỏ qua customerId giả trong query; vẫn thấy đánh giá mình bị ẩn và trả lời; phân trang/offset lớn; không đọc Feedback riêng của người khác | Đạt; sửa lỗi offset lớn |

Dashboard, báo cáo đánh giá, gửi lại lời mời, bật/tắt/phục hồi voucher và áp dụng voucher cũng được rà cùng các mục liên quan; không tính thành những chức năng mới trong bảng phân công.

## Lỗi đã tái hiện và sửa

1. `UserAdminUpdateDto` dùng `[3|5|7|8|9]`, vô tình chấp nhận `0|12345678` như SĐT. Đổi về `[35789]`, giữ nguyên định dạng Việt Nam đã dùng.
2. Form sửa tài khoản khi lỗi có thể nhận `id` giả từ body, làm đường dẫn submit lần sau trỏ sai tài khoản. Bỏ bind ID body và luôn gán ID đường dẫn cho model.
3. Form sửa voucher có lỗi tương tự; khi thiếu ID còn có thể chuyển action thành tạo voucher. Áp dụng cách xác định ID như trên.
4. `page=Integer.MAX_VALUE` tạo offset vượt giới hạn JPA và lỗi server. Hàm phân trang dùng chung giữ page không âm, size 1–100 như trước, đồng thời đảm bảo offset biểu diễn được. Áp dụng đúng các trang tài khoản/voucher/đơn/Feedback/báo cáo bán hàng và phân trang Feedback công khai liên quan.
5. Ngày kết thúc `LocalDate.MIN` ở báo cáo đánh giá/bán sản phẩm làm `minusDays(29)` tràn trước khi kiểm tra phạm vi. Kiểm tra năm trước phép trừ; dùng thông báo/khoảng ngày dự phòng đã có, giữ nguyên bộ lọc ngày bình thường.
6. Chi tiết đơn ID không tồn tại ném IllegalArgumentException thành lỗi server. Trả HTTP 404 theo đúng tình huống không tìm thấy.
7. Ẩn/hiện đánh giá làm mất size đã chọn khi quay lại danh sách. Giữ size cùng page/từ khóa/số sao/trạng thái.

Bổ sung: thông báo lỗi kỹ thuật khi ẩn/hiện không đưa nguyên nội dung lỗi SQL ra trình duyệt; chi tiết nằm trong log server. Nhãn giá trị tồn kho chưa đủ dữ liệu nêu đúng cả điều kiện giá nhập và đối chiếu khớp đang có trong service. Không thêm công thức tính vốn mới.

## Bằng chứng kiểm thử

- **208 test Maven verify đạt, 0 thất bại, 0 lỗi, 0 bỏ qua; đóng gói JAR thành công.** Bao gồm 191 test trước đó, 16 ca DatScopeAuditTests và 1 ca ReviewModerationErrorTests.
- Sáu ca lỗi ban đầu được ghi nhận ở `outputs/scope-audit/reproduce.log`: 3 failure và 3 error; sau sửa cả 6 đạt. Ca mất size được bổ sung sau đó và sửa khi tái hiện được.
- Kiểm tra nghiệp vụ riêng: tranh lượt voucher cuối chỉ một đơn thành công; hai Admin cùng khóa nhau vẫn giữ một Admin hoạt động; hủy trả đúng tồn; tiền hàng/giảm/ship/tổng thu tính đúng; đơn cuối tháng không lẫn đơn đầu tháng; My Feedback không lộ dữ liệu khách khác.
- **269 kiểm tra với SQL Server thật đạt** trên server kiểm tra riêng: trang/form, đăng nhập ba role, token CSRF, ngày/offset cực trị, đơn không tồn tại, form sai SĐT/ID và đối chiếu dữ liệu trước/sau. Các form POST đều cố ý không hợp lệ, không tạo đơn, gửi mail hoặc lưu tài khoản/voucher thử.
- Đối chiếu trong bộ nhớ 11 bảng trước/sau: Users (thông tin tài khoản), Orders, OrderItems, OrderStatusHistories, Vouchers, CustomerVouchers, Reviews, ProductVariants, StockImports, StockImportItems, PasswordResetTokens giữ nguyên dữ liệu nghiệp vụ. Đăng nhập thật cập nhật LastLoginAt/UpdatedAt như thiết kế hiện có.
- Chỉ server kiểm tra được tắt job nền để không tự xử lý đơn thật trong lúc rà. Cấu hình/chương trình chạy bình thường không thay đổi. Server kiểm tra đã dừng.
- Không có migration mới; không thay entity/schema, dữ liệu mẫu, mật khẩu, cấu hình SMTP hoặc các ô lỗi dấu chưa xác minh.

Các ca ghi dữ liệu và gửi mail chạy trên H2/mail giả lập. Lần này không xác minh SMTP/VNPay thật hoặc hoàn tiền thật. Kết quả đạt áp dụng cho những ca đã kiểm tra, không phải cam kết đã loại hết mọi lỗi có thể phát sinh.

## Cách dùng sau cập nhật

Máy Đạt dùng thư mục `petshop` đã đồng bộ và build. Thành viên lấy code dev có bản sửa này; nếu database đã đồng bộ gói `PetShop_Database_Update.zip` trước đó thì không cần chạy thêm SQL. Không chạy file bootstrap/reset để lấy bản sửa.
