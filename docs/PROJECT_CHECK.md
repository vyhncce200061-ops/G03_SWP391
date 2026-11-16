# Kiểm tra các luồng dự án

Bản rà soát gồm thay đổi quyền sửa tài khoản đã được duyệt và các lỗi tái hiện khi chạy lại hệ thống. Không thêm nghiệp vụ, bảng/cột hoặc thay quy tắc hủy đơn/hoàn tiền.

## Các sửa đổi

- Admin xem và khóa/mở khóa Customer; chỉ sửa họ tên Staff. Backend từ chối sửa định danh đăng nhập, mật khẩu, vai trò hoặc trạng thái qua form sửa. Xem [ACCOUNT_MANAGEMENT.md](ACCOUNT_MANAGEMENT.md).
- Hoàn tất đơn VNPay không còn thông báo đã thu tiền COD. Trạng thái thanh toán và tồn kho giữ luồng cũ: COD ghi nhận PAID khi hoàn tất; VNPay đã trả tiền không thu thêm hoặc trừ kho lần nữa.
- URL mặc định trong email mời Staff, xác nhận đăng ký và quên mật khẩu dùng cổng server đang cấu hình. PUBLIC_BASE_URL và APP_BASE_URL tiếp tục được ưu tiên khi có cấu hình địa chỉ riêng. Không đưa cấu hình SMTP/VNPay riêng của máy vào Git.

Hai lỗi mới đều có kiểm thử tái hiện thất bại trước khi sửa: thông báo VNPay và URL email mặc định lệch cổng.

## Phạm vi đối chiếu

| Nhóm | Luồng và điều kiện đã kiểm tra |
|---|---|
| Authentication và hồ sơ | Đăng nhập các vai trò, đăng ký/xác nhận email, quên/đặt lại mật khẩu, token một lần/hết hạn, khóa tài khoản, CSRF, hồ sơ/avatar và địa chỉ đúng chủ sở hữu. Gửi mail dùng mô phỏng; lỗi gửi thư không được tạo tài khoản dở dang. |
| Quản lý tài khoản | Staff nhận tài khoản qua link email; Customer tự sửa hồ sơ; trường bị cấm trong request không được lưu; form cũ không mở lại tài khoản bị khóa; bảo vệ Admin cuối và thao tác đồng thời. |
| Catalog, danh mục và thương hiệu | Tìm kiếm/lọc theo thương hiệu, danh mục cha gồm sản phẩm danh mục con, khoảng giá thấp nhất, phân trang và tham số lỗi. Ngừng hiển thị nhóm phân loại không tự đổi trạng thái bán của sản phẩm theo quyết định hiện có. Xóa cứng danh mục/thương hiệu chỉ khi không còn tham chiếu. |
| Sản phẩm và SKU | Quyền Admin/Staff, giá nguyên đồng, SKU thuộc đúng sản phẩm, không sửa tồn bằng form metadata, ảnh đúng loại/nội dung, giới hạn upload và trạng thái bán. |
| Kho | Phiếu nhập và sửa phiếu điều chỉnh đúng phần chênh lệch; không âm tồn; đối soát lượng nhập, lượng đã phân bổ và tồn hiện có; tranh chấp sửa phiếu/xác nhận đơn. |
| Giỏ hàng và checkout | Số lượng dương/không vượt tồn, chọn dòng, giá hiện tại từ server, phí ship, voucher, chống sửa giỏ/checkout lặp và rollback toàn bộ khi lỗi. |
| COD | Tạo đơn chờ xác nhận chưa trừ kho; xác nhận phân bổ một lần; chuẩn bị → giao → hoàn tất/PAID; hủy/từ chối chỉ trong trạng thái được phép; không hoàn hoặc trừ kho hai lần. |
| VNPay | Giữ tồn khi tạo đơn chờ thanh toán, hết hạn/hủy/thất bại giải phóng một lần; xác minh chữ ký/terminal/số tiền/chủ đơn, callback lặp, IPN/Return đồng thời, thanh toán muộn và rollback. Đơn đã PAID không bị Customer/Staff tự hủy theo quy tắc đã chốt. |
| Voucher | Mã duy nhất kể cả đã xóa mềm, phục hồi về ngừng hoạt động, ngày/hạn mức/giá trị, tranh chấp lượt cuối và hoàn lượt khi hủy. |
| Feedback | Chỉ sản phẩm thuộc đơn của Customer đã hoàn tất/PAID; một đánh giá mỗi dòng đơn; số sao/nội dung, ẩn/hiện, phản hồi Admin một lần và chống ghi đè khi thao tác đồng thời. |
| Báo cáo | Chỉ ghi nhận doanh thu đơn COMPLETED + PAID theo ngày hoàn tất; phân biệt tiền hàng sau giảm giá và phí ship; đối soát cùng khoảng ngày; thống kê Feedback/tồn kho theo dữ liệu thực. |

## Kết quả kiểm thử

- Maven verify: 501 kiểm thử, không thất bại/lỗi/bỏ qua; đóng gói thành công.
- SQL Server tạm: 462 kiểm tra với 1.254 thao tác đồng thời. Có các đợt 20 người mua khi chỉ còn 1, 2 hoặc 10 sản phẩm; không có âm tồn, phân bổ vượt tồn hoặc lệch lượt voucher.
- HTTP thật với database SQL Server tạm: 602 kiểm tra cho Guest, Customer, Staff, Admin; gồm form lỗi, phân quyền, lọc/phân trang, assets và file vượt giới hạn.
- Database đang dùng: 16 kiểm tra chỉ đọc về tồn, tổng tiền, trạng thái, Feedback và quan hệ danh mục/địa chỉ; không thấy vi phạm. Không sửa tài khoản, mật khẩu hoặc dữ liệu nghiệp vụ thật để phục vụ kiểm thử.
- Bản chạy trên máy: 10 đường dẫn trang/assets kiểm tra đạt; 28 ảnh/logo local đều trả HTTP 200 và đúng nội dung ảnh. Các sản phẩm đang bán đều có ảnh; không còn database tạm của đợt kiểm tra.

Kiểm tra VNPay tại đây dùng callback ký bằng khóa thử riêng trong database tạm; không tạo giao dịch mới tại cổng Sandbox, không gửi IPN qua đường hầm công khai và không kiểm chứng lại cấu hình đăng ký terminal của nhà cung cấp. Thanh toán muộn sau khi đơn đã đóng vẫn cần đối soát thủ công theo chức năng hiện có; chưa bổ sung hoàn tiền tự động.

Máy có khoảng gián đoạn/clock leap trong quá trình chạy; phiên 60 phút hết hạn ở một lượt HTTP cũ. Lượt chạy lại bằng phiên mới đạt các kiểm tra nêu trên; không kéo dài hoặc bỏ kiểm tra thời hạn phiên để che việc hết phiên.

Kết quả là phạm vi kiểm chứng tại thời điểm rà soát, không phải cam kết mọi đầu vào hoặc mọi hệ thống bên ngoài đều không thể phát sinh lỗi.
