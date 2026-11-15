# Thay đổi database ngày 07/10/2026 — bản gửi nhóm

Đã áp dụng trực tiếp vào PetShopDB trên máy hiện tại. Có hai đợt: sửa lỗi tài khoản/đơn/báo cáo và dữ liệu demo, sau đó thêm xóa mềm voucher theo yêu cầu. Bộ test ghi dữ liệu chạy trong database PetShop_FixTest_* riêng.

## Cấu trúc đã thay đổi

Vẫn **21 bảng và 31 khóa ngoại**, không thêm/xóa bảng hoặc thay đổi quan hệ FK. Có thay đổi trên Users, Orders, Vouchers và view doanh thu:

| Đối tượng | Thay đổi chính xác | Tài liệu cần cập nhật |
|---|---|---|
| Users | Thêm `MustChangePassword BIT NOT NULL DEFAULT 0` | Data dictionary; luồng Admin reset mật khẩu buộc người dùng đổi mật khẩu |
| Vouchers | Thêm `IsDeleted BIT NOT NULL DEFAULT 0` | Data dictionary; luồng Admin xóa mềm, lọc đã xóa |
| Vouchers | Thêm `DeletedAt DATETIME2(0) NULL` | Data dictionary; thời gian xóa mềm |
| Vouchers | CHECK `CK_Vouchers_SoftDelete`: chưa xóa thì DeletedAt NULL; đã xóa thì DeletedAt có giá trị và Status INACTIVE | Ràng buộc xóa mềm, chặn áp dụng/sửa/kích hoạt mã đã xóa |
| Orders | CHECK `CK_Orders_CompletedPaid`: COMPLETED phải PAID | Quy tắc trạng thái và thanh toán đơn |
| Orders | Index `IX_Orders_CompletedAt`, gồm các cột tiền phục vụ báo cáo, lọc COMPLETED | Danh sách index nếu tài liệu có phần này |
| vw_RevenueByDay | TotalRevenue = SUM(SubtotalAmount − DiscountAmount); thêm TotalShipping và TotalCollected | Công thức báo cáo: tiền hàng sau giảm, phí ship, tổng thu tách riêng |

Tổng thay đổi schema: **3 cột mới, 2 CHECK mới, 1 index mới, 1 view cập nhật**. Sau cập nhật có 54 CHECK. ERD về quan hệ giữ nguyên; ERD có liệt kê thuộc tính cần thêm ba cột trên.

## Dữ liệu demo đã sửa trên máy hiện tại

- Sáu SKU được đồng bộ lại tồn kho theo phiếu nhập và các đơn chưa hủy; tổng giảm 13 đơn vị còn thiếu trong dữ liệu seed cũ.
- Hai voucher PETWELCOME10 và FREESHIP30K được sửa UsedCount thành 1 và đồng bộ cờ sử dụng trong ví theo đơn.
- Đơn ORD-20261004-004 được sửa tạm tính/tổng từ 509.000đ thành 537.000đ theo dòng hàng; không đổi đơn giá/số lượng item.
- Thêm 8 snapshot trạng thái cho đơn seed, có lý do ghi rõ snapshot, không phải lịch sử chuyển trạng thái thực tế.
- Thêm 2 bản ghi audit thanh toán giả lập DEMO- cho dữ liệu seed; không có xác minh chữ ký/callback VNPay thật.

Đợt xóa mềm vừa rồi chỉ thêm schema vào PetShopDB, không xóa voucher hiện có. Snapshot trước sửa dữ liệu và trước migration xóa mềm đã lưu cục bộ trong outputs.

## File SQL/source đã cập nhật

- `PetShop_Database_Final.sql`: bootstrap cho database mới, có schema mới.
- `seed_demo_orders_reviews.sql`: seed đã sửa tổng tiền, tồn, quota và audit; chạy lặp không trừ tồn/quota lần nữa.
- `database/migrations/V20261007__account_and_order_integrity.sql`.
- `database/migrations/V20261007__revenue_view.sql`.
- `database/migrations/V20261007__voucher_soft_delete.sql`.
- `database/repair_demo_20261007.sql`: repair riêng cho bộ đơn demo đã xác minh.

## Thành viên cần làm gì?

**Đang có database và muốn giữ dữ liệu:** sao lưu, chọn đúng database trong SSMS, chạy ba migration theo thứ tự trên rồi lấy code mới. Migration schema không tự đồng bộ tồn/quota/đơn seed trên máy khác. Repair dữ liệu demo là bước riêng, chỉ chạy sau khi kiểm tra dữ liệu của thành viên khớp bộ seed.

**Database mới hoặc chủ động dựng lại demo:** chạy bootstrap mới rồi seed mới. Bootstrap có DROP TABLE và sẽ xóa dữ liệu nếu chạy trên database đang dùng.

Không bắt buộc gửi bản .bak hoặc dữ liệu thật của máy này. Gửi migration + code mới là đủ để cập nhật cấu trúc. Nếu muốn mọi máy có cùng dữ liệu demo, dùng bootstrap + seed trên database demo mới; các thời điểm seed được tính khi chạy nên không bảo đảm timestamp giống hệt bản trên máy này.

## Tình trạng tài liệu

File này là phụ lục thay đổi chính xác để cập nhật tài liệu nhóm. Chưa sửa trực tiếp file Word `Dac_ta_chuc_nang_va_nghiep_vu (2).docx` hoặc các sơ đồ drawio. Tài liệu Word cần bổ sung luồng xóa mềm, reset buộc đổi mật khẩu và công thức doanh thu; data dictionary/ERD thuộc tính cần thêm ba cột. Không cần vẽ lại quan hệ FK.

## Kiểm chứng

35/35 test Maven và 55/55 kiểm tra SQL Server đạt sau xóa mềm. PetShopDB sau migration: sai lệch tồn, quota, tổng tiền đều 0; 6 đơn hoàn tất có tiền hàng 2.112.000đ, phí ship 180.000đ, tổng thu 2.292.000đ.
