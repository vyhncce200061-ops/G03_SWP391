# Feedback và báo cáo doanh thu

## Feedback

- Một feedback gắn với một dòng sản phẩm/biến thể đã mua (OrderItem), thuộc khách hàng hiện tại, trong đơn COMPLETED và PAID. Mỗi dòng được đánh giá một lần, kể cả feedback sau đó bị ẩn.
- Form riêng, popup trong chi tiết đơn, popup trong hồ sơ, lịch sử đánh giá và phần Admin trả lời đều thể hiện ảnh cùng thông tin sản phẩm.
- Tên, phân loại và SKU theo thông tin đã lưu trong đơn. Ảnh lấy từ ProductImages hiện tại: ưu tiên ảnh chính, rồi SortOrder và Id; thiếu hoặc lỗi ảnh dùng hình mặc định trong dự án.
- Ảnh được truy vấn theo lô, không fetch collection ảnh trong truy vấn phân trang feedback.
- Nút đánh giá chung mặc định chọn dòng đầu tiên ở chi tiết đơn được thay bằng nút tại từng dòng. Luồng chọn từng sản phẩm trong hồ sơ được giữ lại; dòng đã có feedback có dấu “Đã đánh giá”.
- Gửi từ form riêng thành công chuyển về lịch sử đánh giá, tránh quay lại form không còn đủ điều kiện rồi báo nhầm là gửi trùng. Giữ quy trình POST/Redirect/GET.
- Nội dung feedback 1–1000 ký tự sau xử lý khoảng trắng, điểm 1–5. Admin đang hoạt động được trả lời một lần, nội dung không rỗng và tối đa 1000 ký tự. Giữ thao tác ẩn/hiện và lịch sử trả lời.
- Báo cáo gộp theo ProductId, không gộp nhầm hai sản phẩm trùng tên. Số lượt là số feedback trong khoảng ngày gửi, không phải số sản phẩm bán được.
- Báo cáo nội bộ tính cả feedback công khai và đang ẩn. Trang sản phẩm chỉ tính/hiển thị feedback công khai.
- Điểm trung bình toàn kỳ = tổng điểm của các feedback / tổng số feedback, không lấy trung bình các điểm trung bình của sản phẩm.

## Doanh thu

- Điều kiện ghi nhận: COMPLETED, PAID, có CompletedAt trong kỳ. Khoảng ngày gồm ngày đầu và ngày cuối; truy vấn dùng mốc cuối loại trừ lúc 00:00 của ngày tiếp theo.
- Doanh thu tiền hàng = SubtotalAmount − DiscountAmount.
- Phí giao hàng được hiển thị riêng. Tổng thu = doanh thu tiền hàng + ShippingFee; các chỉ số này không phải lợi nhuận.
- Tổng quan hiển thị doanh thu toàn thời gian đến hết hôm nay. Báo cáo mặc định là 30 ngày gần nhất; lựa chọn “Toàn thời gian” dùng cùng điều kiện với tổng quan.
- Liên kết báo cáo từ tổng quan chọn sẵn toàn thời gian; liên kết ở doanh thu hôm nay chọn sẵn hôm nay.
- Không ghi nhận đơn chưa hoàn tất, chưa thanh toán, thiếu thời điểm hoàn tất hoặc có thời điểm hoàn tất trong tương lai.

Đợt cập nhật này không bổ sung bảng/cột, không thay đổi dữ liệu doanh thu/feedback đang lưu. Các migration đã có trên dev vẫn cần được áp dụng khi dùng một database cũ.

## Tham khảo giao diện và cách gọi chỉ số

- [Amazon: Customer reviews and star ratings](https://www.aboutamazon.com/news/retail/amazon-customer-reviews-star-ratings): ngữ cảnh sản phẩm đã mua và thang điểm.
- [eBay: Viewing and responding to buyer feedback](https://export.ebay.com/en/fees-regulations-policies/seller-protection/feedback/viewing-and-responding-to-feedback-from-buyers/): đọc feedback và phản hồi trong cùng ngữ cảnh.
- [Shopify: Sales reports](https://help.shopify.com/en/manual/reports-and-analytics/shopify-reports/report-types/default-reports/sales-report): phân biệt tiền hàng, giảm giá, phí giao hàng và tổng thu.
