# Đặt hàng, giữ tồn VNPay và giao dịch đồng thời

Không thêm bảng/cột, không cập nhật dữ liệu đang lưu và không cần script database mới. Giữ cột Orders.StockDeducted, các trạng thái và thời hạn PaymentTransactions.ExpiresAt đã có.

| Mốc | COD | VNPay mới |
|---|---|---|
| Thêm vào giỏ | Không giữ hàng | Không giữ hàng |
| Đặt đơn | PENDING_CONFIRMATION / UNPAID, chưa trừ tồn | PENDING_PAYMENT / UNPAID, trừ lượng đang có để giữ hàng và đặt StockDeducted=true |
| Cấp hàng | Nhân viên xác nhận mới trừ kho | Hàng đã được giữ khi đặt đơn |
| Thanh toán thành công | Ghi PAID khi hoàn tất giao hàng như trước | Callback hợp lệ trong hạn ghi PAID / CONFIRMED, không trừ lượng đã giữ lần nữa |
| Hủy trước khi xử lý / thanh toán | Quyền và điều kiện giữ nguyên | Hủy đơn chưa thanh toán, hoàn tồn và lượt voucher đúng một lần |
| Thanh toán thất bại / hết hạn | Không áp dụng | Hủy đơn, hoàn lượng giữ và voucher đúng một lần |

VNPay giữ hàng trong thời hạn 15 phút của giao dịch đã tạo. Tiếp tục thanh toán dùng lại mã giao dịch, số tiền và hạn cũ; không tạo đơn mới hoặc gia hạn giữ hàng. Tác vụ hết hạn chạy theo lịch hiện có, mỗi đơn được xử lý bằng một transaction riêng.

Nhiều người mua cùng SKU được kiểm tra/trừ bằng khóa hàng và câu UPDATE có điều kiện đủ tồn. Các SKU luôn khóa theo ID tăng dần. Một đơn nhiều SKU chỉ thành công khi giữ/cấp đủ cả đơn; lỗi ở SKU sau rollback toàn bộ các thay đổi trước đó, gồm tồn, đơn, thanh toán, quota và giỏ.

Tất cả thao tác sửa đơn khóa Customer trước Order, sau đó mới đến SKU và voucher. Sửa giỏ dùng cùng thứ tự Customer trước Cart, kể cả khi tạo giỏ lần đầu. Cách này tránh việc transaction hủy đang giữ voucher đếm phải một đơn khác của cùng khách đang cập nhật và chờ voucher đó.

Khi nhận khóa cập nhật lần đầu trong transaction, đọc lại trạng thái đơn, khách và SKU cần chốt giá. Dữ liệu được tải trước trong phiên trình duyệt không được dùng để hủy lại đơn, hoàn tồn lại hoặc lấy giá cũ. Lời gọi lồng trong cùng transaction giữ nguyên trạng thái PAID và lượng giữ đang xử lý, không refresh đè các thay đổi chưa commit.

RetryingTransactions chỉ thử lại khi SQL Server báo deadlock 1205, tối đa 3 lần. Mỗi lần bắt đầu lại toàn transaction sau rollback; một lời gọi đang tham gia transaction ngoài để caller ngoài quyết định retry. CheckoutPlacementService bao cả đặt đơn và sinh URL trong một transaction, nên lỗi tạo URL không để lại đơn, giữ hàng hoặc giữ quota. Nếu vẫn deadlock sau giới hạn, người dùng nhận thông báo thử lại; IPN nhận mã 99 để cổng có thể gửi lại.

Đơn và link VNPay cũ:

- Không tự backfill, trừ thêm hoặc sửa các đơn đã lưu.
- Đơn cũ chưa giữ hàng phải kiểm tra/giữ đủ hàng trước khi cấp lại URL thanh toán; giữ nguyên hạn gốc.
- Callback từ link cũ đã cấp trước bản sửa vẫn được đối chiếu. Nếu link đó chưa từng giữ hàng và đã trả tiền nhưng thiếu tồn, giữ đường xử lý PAID/chờ bổ sung có sẵn, không đổi tiền đã nhận thành chưa trả.
- Callback thành công đến sau khi đơn đã hủy/hết hạn không tự mở lại đơn hoặc chiếm lại hàng đã giải phóng. Lưu dấu đối soát theo cơ chế hiện có. Chưa có chức năng hoàn tiền tự động; nhóm vẫn cần xử lý đối soát trường hợp này.

Báo cáo doanh thu/bán hàng tiếp tục chỉ tính đơn COMPLETED và PAID. Đối chiếu tồn tiếp tục dùng lượng đã giữ/xuất của các đơn StockDeducted=true chưa hủy, nên đơn chờ VNPay đã giữ hàng được phản ánh vào tồn khả dụng.

Kiểm thử:

- OrderWorkflowTests: giữ hàng, tranh hàng, rollback nhiều SKU, hủy/hết hạn, tương thích đơn cũ, nhiều tab dùng dữ liệu cũ và giá được sửa đồng thời.
- RetryingTransactionsTests: rollback rồi retry toàn transaction, giới hạn retry, không retry lỗi nghiệp vụ/lỗi SQL khác, transaction lồng và ngắt luồng.
- CheckoutWorkflowTests / VnPayServiceTests: tiền nguyên đồng, số tiền ký VNPay, dùng lại hạn cũ, chủ sở hữu, chữ ký, callback lặp và thanh toán đến muộn.
- Chạy toàn bộ bằng Maven verify. Kiểm chứng SQL Server độc lập có cả ca lỗi đã tái hiện trước sửa, tranh món cuối, giữ/hoàn kho, phiếu nhập và lỗi giữa đặt đơn với tạo URL.
