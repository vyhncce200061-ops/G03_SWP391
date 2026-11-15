# Admin trả lời Feedback — quyết định 29/10/2026

Review và Feedback là cùng đánh giá sản phẩm, lưu tại `Reviews`. Theo phạm vi đã xác nhận: Admin trả lời và giữ ẩn/hiện; Staff chưa có quyền trả lời vì chưa được giao chăm sóc phản hồi khách hàng. Phân quyền được chặn ở URL và kiểm tra ACTIVE/ADMIN trong service. Không thêm chức năng xóa tài khoản hoặc Admin reset mật khẩu Staff.

## Nghiệp vụ

- Mỗi đánh giá nhận một câu trả lời chính thức của cửa hàng, không ghi đè nhận xét của khách và không sửa/xóa câu trả lời đã gửi trong phạm vi này.
- Nội dung phải có chữ, tối đa 1000 ký tự; bỏ khoảng trắng đầu/cuối, escape HTML khi hiển thị. Không chạy mã HTML do người dùng gửi.
- Lưu người trả lời và thời điểm; Admin xem người thực hiện, khách chỉ thấy nhãn “Phản hồi từ PetShop” và thời gian.
- Khóa giao dịch giúp gửi lặp/hai Admin cùng gửi không ghi đè nhau; ẩn/hiện đồng thời không làm mất câu trả lời.
- Ẩn đánh giá sẽ ẩn cả nhận xét/câu trả lời khỏi trang sản phẩm, bỏ khỏi số sao công khai; chủ đánh giá vẫn thấy trong My Feedback. Hiện lại giữ nguyên nội dung và câu trả lời.
- Không tự ẩn vì đánh giá ít sao, không gửi email phản hồi, không thêm chat/khiếu nại hoặc tự cấp thêm quyền Staff.
- Form lỗi giữ nội dung nhập và bộ lọc/phân trang để người dùng sửa. Gửi trùng được báo và yêu cầu tải lại để xem câu trả lời đã lưu.

## Database

`database/migrations/V20261029__review_reply.sql`: chỉ thêm `ReplyText`, `RepliedByUserId`, `RepliedAt` nullable, một FK và một CHECK. Đã lưu snapshot riêng của toàn bộ 5 dòng Reviews cùng cấu trúc trước khi áp dụng tại máy Đạt; xác minh mọi cột cũ không đổi, ba cột mới đều NULL. Snapshot chứa dữ liệu khách nên không nằm trong repository/gói gửi nhóm.

SQL Server đã kiểm tra: chạy migration hai lần; Unicode; FK từ chối tác giả không tồn tại; CHECK từ chối bộ thông tin trả lời thiếu. Kiểm tra service/ORM thực (trả lời rồi ẩn) chạy trong transaction rollback; không lưu trả lời thử.

## Demo

1. Admin mở Đánh giá sản phẩm, lọc một đánh giá chưa trả lời; mở “Trả lời đánh giá”. Bỏ trống sẽ bị chặn; giới hạn 1000 ký tự ở giao diện và server.
2. Nhập lời trả lời phù hợp nhận xét khách rồi gửi. Trang hiển thị câu trả lời, người thực hiện và thời gian; không còn form gửi lần hai.
3. Customer mở sản phẩm / Đánh giá của tôi để thấy câu trả lời từ cửa hàng.
4. Admin ẩn đánh giá: trang sản phẩm không còn đánh giá/câu trả lời; My Feedback vẫn giữ. Hiện lại: nội dung trở lại đầy đủ.
5. Staff/Customer thử đường dẫn Admin sẽ bị từ chối. Staff vẫn tự Quên mật khẩu bằng luồng công khai.

Tài liệu UC-27.2/FEAT-24 đã cập nhật thành Reply & Moderate Feedback; nhóm cần cập nhật nhãn trên bản vẽ Use Case tương ứng. Không tự đánh lại số toàn bộ UC.

## Kiểm tra bổ sung sau Reply

- Maven verify: 183 test đạt; sau chỉnh hướng dẫn form và bổ sung chặn phiên Admin bị khóa, 15 test TeamIntegrationTests chạy lại đạt.
- 231 kiểm tra ứng dụng SQL Server đạt: 60 GET, 165 form POST có CSRF, 3 đăng nhập và 3 kiểm tra chuyển hướng/phân quyền.
- 4 kiểm tra service/ORM trên SQL Server đạt trong transaction rollback; không giữ phản hồi thử hoặc đổi trạng thái đánh giá thật. Migration đã thử chạy hai lần và xác minh nguyên vẹn mọi giá trị cũ.
