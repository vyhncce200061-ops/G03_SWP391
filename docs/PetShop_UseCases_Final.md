# TÀI LIỆU ĐẶC TẢ USE CASE HỆ THỐNG PET SHOP SWP391
## Phiên Bản Hoàn Thiện & Chuẩn Hóa Toàn Diện (Final Approved Baseline)

> **Môn học**: SWP391 - Kỹ thuật Phần mềm (Đại học FPT)  
> **Nhóm thực hiện**: SE2003 - G03 (5 thành viên)  
> **Đề tài**: Pet Shop E-Commerce Platform (Chuyên phụ kiện & thức ăn cho Chó và Mèo)  
> **Ngôn ngữ đặc tả**: Tiếng Việt (Vietnamese)  
> **Tổng số Use Case**: 73 Use Cases (29 Parent Use Cases, 44 Sub-Use Cases)  
> **Tiêu chuẩn kiểm duyệt**: Khớp 100% bản vẽ Use Case Diagram đã hiệu chỉnh, loại bỏ UC-28 Banner, sửa UC-27.2 thành Ẩn/Hiện đánh giá, cập nhật Email Service & VNPay Sandbox, bổ sung biến thể SKU/ảnh sản phẩm/cảnh báo tồn kho/phí ship.

---

## 1. TỔNG QUAN HỆ THỐNG VÀ CÁC TÁC NHÂN (ACTORS)

Hệ thống Pet Shop E-Commerce phục vụ bán hàng trực tuyến các sản phẩm chăm sóc thú cưng (chuyên biệt cho chó và mèo) với mô hình phân quyền chặt chẽ thông qua 4 tác nhân chính (Primary Actors) và 2 tác nhân phụ trợ (Secondary Actors).

### 1.1. Các tác nhân chính (Primary Actors)

| Tác nhân | Tên tiếng Việt | Vai trò & Phạm vi trách nhiệm |
|---|---|---|
| **Guest** | Khách vãng lai | Người dùng chưa đăng nhập vào hệ thống. Có quyền xem danh mục sản phẩm, duyệt danh sách và xem chi tiết sản phẩm, tìm kiếm/lọc sản phẩm theo nhiều tiêu chí, xem danh sách voucher khuyến mãi công khai, đăng ký tài khoản thành viên mới. Bắt buộc đăng nhập khi muốn thao tác giỏ hàng hoặc thực hiện mua sắm. |
| **Customer** | Khách hàng | Người dùng đã đăng nhập với vai trò `CUSTOMER`. Kế thừa toàn bộ quyền tra cứu của Guest, đồng thời có toàn quyền sử dụng các tính năng mua sắm: quản lý hồ sơ cá nhân, quản lý sổ địa chỉ giao hàng, thêm/sửa/xóa giỏ hàng, áp dụng voucher từ ví cá nhân, đặt hàng (Checkout) qua COD hoặc VNPay, theo dõi lịch sử đơn hàng, hủy đơn hàng hợp lệ, đánh giá sản phẩm đã mua, quản lý danh sách yêu thích (Wishlist) và thu thập voucher vào ví cá nhân. |
| **Staff** | Nhân viên vận hành | Người dùng nội bộ được cấp tài khoản vai trò `STAFF`. Chịu trách nhiệm vận hành nghiệp vụ bán lẻ hàng ngày: quản lý thông tin sản phẩm, quản lý bộ sưu tập ảnh sản phẩm, quản lý các biến thể SKU (giá bán, tồn kho), lập phiếu nhập hàng vào kho, theo dõi lịch sử nhập xuất, theo dõi cảnh báo SKU sắp hết hàng, tiếp nhận và chuyển đổi trạng thái đơn hàng theo đúng quy trình từ xác nhận đến giao hàng hoàn thành hoặc từ chối đơn. |
| **Admin** | Quản trị viên | Người dùng cấp cao nhất với vai trò `ADMIN`. Kế thừa toàn bộ quyền hạn của Staff, đồng thời chịu trách nhiệm quản trị toàn bộ hệ thống: quản lý tài khoản người dùng (khóa/mở khóa tài khoản), quản lý danh mục sản phẩm 2 cấp, quản lý thương hiệu, quản trị sản phẩm và đơn hàng cấp cao, khởi tạo chiến dịch voucher khuyến mãi, kiểm duyệt đánh giá (ẩn/hiện nhận xét), cấu hình mức phí vận chuyển cố định toàn hệ thống và xem các báo cáo thống kê doanh thu, bán hàng, tồn kho. |

### 1.2. Các tác nhân phụ (Secondary Actors)

| Tác nhân phụ | Tên hệ thống | Use Case liên kết | Nhiệm vụ & Cơ chế vận hành |
|---|---|---|---|
| **Email Service** | Dịch vụ gửi Email tự động | **UC-7 (Forgot Password)** | Khi khách hàng yêu cầu khôi phục mật khẩu, hệ thống sinh token an toàn lưu vào bảng `PasswordResetTokens` (thời hạn 15–30 phút, mã hóa SHA-256). Email Service tiếp nhận yêu cầu từ Spring Mail và gửi email chứa liên kết đặt lại mật khẩu đến hòm thư người dùng. *(Tuyệt đối không sử dụng SMS OTP)* |
| **Online Payment Gateway** | Cổng thanh toán VNPay (Sandbox) | **UC-11.1 (Payment)** | Khi khách hàng lựa chọn thanh toán trực tuyến, hệ thống tạo URL thanh toán có chữ ký bảo mật HMAC-SHA512 để chuyển hướng khách sang VNPay Sandbox. Sau khi thanh toán, VNPay gửi kết quả qua Return URL / IPN để hệ thống đối soát số tiền, chuyển đơn sang `CONFIRMED`, cập nhật `PaymentStatus = 'PAID'` và ghi nhật ký vào `PaymentTransactions`. *(Tuyệt đối không sử dụng Google OAuth2)* |

---

## 2. QUY TẮC HIỆU CHỈNH & QUAN HỆ USE CASE (USE CASE RELATIONSHIPS)

1. **Hiệu chỉnh đánh số (Numbering Fixes)**:
   - Sửa lỗi trùng mã `UC-2.1`: Phân định thành `UC-2.1: View Products List` (Xem danh sách sản phẩm) và `UC-2.2: View Product Detail` (Xem chi tiết sản phẩm).
   - Sửa lỗi trùng mã `UC-15.1`: Phân định thành 3 Use Case con rõ ràng: `UC-15.1: Save Public Voucher` (Lưu voucher công khai), `UC-15.2: View My Voucher` (Xem danh sách voucher của tôi), `UC-15.3: Delete My Voucher` (Xóa voucher khỏi ví cá nhân).
2. **Cắt bỏ hoàn toàn (Deletions)**:
   - Cắt bỏ hoàn toàn `UC-28: Manage Banner` (Admin) do bảng `Banners` đã bị loại bỏ khỏi kiến trúc cơ sở dữ liệu. Banner trang chủ được cấu hình tĩnh trong template Thymeleaf.
   - Loại bỏ hoàn toàn tác nhân phụ `Google OAuth2 Service` (không có trong phạm vi và không có cấu trúc CSDL hỗ trợ).
   - Loại bỏ `SMS OTP Service`, thay thế bằng `Email Service`.
3. **Sửa đổi nghiệp vụ (Modifications)**:
   - Sửa `UC-27.2` từ "Reply Review" thành **"Hide/Show Review"** (Ẩn/Hiện đánh giá). Bảng `Reviews` chỉ quản lý cờ trạng thái `Status IN ('VISIBLE', 'HIDDEN')`, không hỗ trợ phản hồi bình luận.
   - Chuyển `UC-8.4: Create Address` từ phân hệ `UC-8: Manage Profile` sang phân hệ `UC-9: Manage Address` thành `UC-9.1: Create Address`.
4. **Bổ sung mới (New Use Cases)**:
   - `UC-18.3: Manage Product Images` (Staff) — Thêm/xóa/sắp xếp ảnh và đặt ảnh chính `IsPrimary`.
   - `UC-18.4: Manage Product Variants/SKU` (Staff) — CRUD thuộc tính, giá bán, tồn kho cho bảng `ProductVariants`.
   - `UC-19.4: View Low Stock Alerts` (Staff) — Xem cảnh báo tồn kho thấp dựa trên view `vw_LowStockVariants`.
   - `UC-30: Manage Shipping Fee` (Admin) — Cấu hình mức phí ship cố định (mặc định 30.000 VNĐ).
5. **Đặc tả quan hệ UML**:
   - `<<extend>>`: Đại diện cho các hành vi tùy chọn hoặc chức năng chi tiết được kích hoạt từ Use Case cơ sở (Base UC) khi người dùng thực hiện thao tác tương ứng (ví dụ: các chức năng con trong menu quản trị, xem danh sách, cập nhật, xóa, áp dụng voucher).
   - `<<include>>`: Đại diện cho các điều kiện hoặc quy trình bắt buộc phải diễn ra như một phần nội tại của Use Case chính (ví dụ: Checkout bắt buộc phải kiểm tra tồn kho khả dụng; Feedback bắt buộc phải kiểm tra điều kiện đơn hàng đã `COMPLETED`).
   - `Generalization` (Kế thừa Actor): `Customer` kế thừa từ `Guest`; `Admin` kế thừa toàn bộ Use Case của `Staff`.

---

## 3. DANH MỤC CHI TIẾT 73 USE CASES THEO TÁC NHÂN

```
========================================================================================
TỔNG SỐ USE CASES: 73 (GUEST: 7 | CUSTOMER: 30 | STAFF: 18 | ADMIN: 18)
========================================================================================
```

### 3.1. PHÂN HỆ GUEST — KHÁCH VÃNG LAI (UC-1 đến UC-5)
*Gồm 5 Use Case cha và 2 Use Case con (Tổng: 7 Use Cases)*

| Mã UC | Tên Use Case | Tác nhân chính | Tác nhân phụ | Quan hệ UML | Mô tả chức năng (1–2 câu tiếng Việt) | Bảng CSDL / View |
|---|---|---|---|---|---|---|
| **UC-1** | View Categories | Guest | Không | Độc lập | Cho phép khách xem danh sách các nhóm danh mục sản phẩm (thức ăn, phụ kiện, vệ sinh) để dễ dàng định hướng tìm kiếm sản phẩm cho chó và mèo. | `Categories` |
| **UC-2** | View Product | Guest | Không | Base UC | Cho phép khách khám phá các mặt hàng thú cưng trên website thông qua danh sách tổng hợp hoặc xem chi tiết một sản phẩm cụ thể. | `Products`, `ProductVariants` |
| **UC-2.1** | View Products List | Guest | Không | `<<extend>>` UC-2 | Mở rộng của UC-2, hiển thị danh sách sản phẩm dạng lưới kèm bộ lọc theo loài (Chó/Mèo), danh mục, thương hiệu, khoảng giá và sắp xếp theo giá cả/độ mới. | `Products`, `ProductVariants`, `Categories`, `Brands`, `vw_ProductSummary` |
| **UC-2.2** | View Product Detail | Guest | Không | `<<extend>>` UC-2 | Mở rộng của UC-2, hiển thị chi tiết sản phẩm gồm thư viện ảnh, mô tả, các biến thể SKU kèm giá bán và tồn kho, cùng các đánh giá đã được kiểm duyệt. | `Products`, `ProductImages`, `ProductVariants`, `Reviews` |
| **UC-3** | Search Products | Guest | Không | Mở rộng UC-2.1 | Cho phép khách tìm kiếm nhanh sản phẩm bằng từ khóa tên sản phẩm, tên biến thể hoặc thương hiệu trên thanh công cụ tìm kiếm. | `Products`, `Brands`, `Categories` |
| **UC-4** | View Promotions | Guest | Không | Độc lập | Cho phép khách xem danh sách các mã giảm giá và chiến dịch voucher công khai đang có hiệu lực của cửa hàng. | `Vouchers` |
| **UC-5** | Register Account | Guest | Email Service | Độc lập | Khách nhập họ tên, email, SĐT và mật khẩu; hệ thống gửi link xác nhận có hạn 24 giờ. Xác nhận thành công mới tạo tài khoản CUSTOMER, lưu hash BCrypt và giỏ hàng rỗng. Link sai/hết hạn/đã kích hoạt không tạo thêm tài khoản. | `Users`, `Roles`, `Carts` |

---

### 3.2. PHÂN HỆ CUSTOMER — KHÁCH HÀNG (UC-6 đến UC-16)
*Gồm 11 Use Case cha và 19 Use Case con (Tổng: 30 Use Cases; chia sẻ UC-6 và UC-16 với Staff & Admin)*

| Mã UC | Tên Use Case | Tác nhân chính | Tác nhân phụ | Quan hệ UML | Mô tả chức năng (1–2 câu tiếng Việt) | Bảng CSDL / View |
|---|---|---|---|---|---|---|
| **UC-6** | Logout | Customer, Staff, Admin | Không | Độc lập | Hủy phiên làm việc hiện tại của người dùng, xóa bỏ session/token bảo mật và điều hướng người dùng về trang chủ an toàn. | `Users` (Cập nhật session) |
| **UC-7** | Forgot Password | Customer | **Email Service** | Độc lập | Cho phép khách hàng yêu cầu khôi phục mật khẩu qua email; hệ thống tạo mã token bảo mật và Email Service gửi liên kết đặt lại mật khẩu cho khách hàng. | `Users`, `PasswordResetTokens` |
| **UC-8** | Manage Profile | Customer | Không | Base UC | Cung cấp giao diện trung tâm để khách hàng theo dõi và quản lý thông tin tài khoản cá nhân của mình. | `Users` |
| **UC-8.1** | View Profile | Customer | Không | `<<extend>>` UC-8 | Hiển thị thông tin chi tiết hồ sơ cá nhân của khách hàng bao gồm họ tên, email, số điện thoại, ảnh đại diện và thời gian tạo tài khoản. | `Users` |
| **UC-8.2** | Update Profile | Customer | Không | `<<extend>>` UC-8 | Cho phép khách hàng cập nhật thông tin cá nhân cơ bản như chỉnh sửa họ tên, số điện thoại liên lạc và thay đổi ảnh đại diện (avatar). | `Users` |
| **UC-8.3** | Change Password | Customer | Không | `<<extend>>` UC-8 | Cho phép khách hàng đổi mật khẩu tài khoản sau khi đã nhập đúng và xác thực mật khẩu hiện tại. | `Users` |
| **UC-9** | Manage Address | Customer | Không | Base UC | Cung cấp giao diện quản lý sổ địa chỉ giao hàng để khách hàng lưu trữ và lựa chọn nhiều địa chỉ nhận hàng khác nhau. | `UserAddresses` |
| **UC-9.1** | Create Address | Customer | Không | `<<extend>>` UC-9 | Cho phép khách hàng thêm địa chỉ giao hàng mới vào sổ địa chỉ gồm tên người nhận, số điện thoại và địa chỉ chi tiết *(chuyển từ UC-8.4 sang)*. | `UserAddresses` |
| **UC-9.2** | Set Default Address | Customer | Không | `<<extend>>` UC-9 | Cho phép khách hàng chỉ định một địa chỉ cụ thể làm địa chỉ mặc định để tự động điền khi tạo đơn hàng mới. | `UserAddresses` |
| **UC-10** | Manage Cart | Customer | Không | Base UC | Cung cấp chức năng quản lý các mặt hàng dự định mua sắm trong giỏ hàng cá nhân của khách hàng. | `Carts`, `CartItems` |
| **UC-10.1** | Add Product to Cart | Customer | Không | `<<extend>>` UC-10 | Cho phép khách hàng chọn biến thể SKU và số lượng mong muốn để thêm vào giỏ hàng, đồng thời kiểm tra tồn kho khả dụng tức thời. | `Carts`, `CartItems`, `ProductVariants` |
| **UC-10.2** | View Cart | Customer | Không | `<<extend>>` UC-10 | Hiển thị danh sách các sản phẩm đang có trong giỏ, đơn giá từng SKU, số lượng đặt mua và tổng tiền hàng tạm tính. | `Carts`, `CartItems`, `ProductVariants` |
| **UC-10.3** | Update Cart | Customer | Không | `<<extend>>` UC-10 | Cho phép khách hàng tăng/giảm số lượng mặt hàng trong giỏ hoặc tích chọn/bỏ chọn mặt hàng muốn tiến hành đặt mua (`IsSelected`). | `CartItems` |
| **UC-10.4** | Remove Cart Item | Customer | Không | `<<extend>>` UC-10 | Cho phép khách hàng xóa bỏ một hoặc nhiều sản phẩm không còn nhu cầu mua ra khỏi giỏ hàng. | `CartItems` |
| **UC-11** | Checkout | Customer | Không | Base UC | Quy trình xác nhận đơn hàng, chọn địa chỉ giao nhận, áp dụng voucher giảm giá và chốt thông tin thanh toán đơn hàng. | `Orders`, `OrderItems`, `OrderStatusHistories` |
| **UC-11.1** | Payment | Customer | **Online Payment Gateway (VNPay)** | `<<extend>>` UC-11 | Xử lý phương thức thanh toán: chuyển đơn COD sang chờ xác nhận hoặc chuyển hướng khách sang VNPay Sandbox để thanh toán trực tuyến qua thẻ test. | `Orders`, `PaymentTransactions`, `OrderStatusHistories` |
| **UC-11.2** | Apply Voucher | Customer | Không | `<<extend>>` UC-11 | Cho phép khách hàng áp dụng mã giảm giá hợp lệ từ ví voucher cá nhân (`CustomerVouchers`) để giảm trừ tiền trực tiếp trên đơn hàng. | `CustomerVouchers`, `Vouchers`, `Orders` |
| **UC-12** | Manage Order | Customer | Không | Base UC | Cung cấp giao diện để khách hàng theo dõi tiến trình và quản lý toàn bộ các đơn hàng đã đặt của mình. | `Orders` |
| **UC-12.1** | View Order List | Customer | Không | `<<extend>>` UC-12 | Hiển thị danh sách toàn bộ các đơn hàng đã đặt của khách hàng, kèm bộ lọc theo trạng thái (Chờ xác nhận, Đang chuẩn bị, Đang giao, Hoàn thành, Đã hủy). | `Orders` |
| **UC-12.2** | View Order Detail | Customer | Không | `<<extend>>` UC-12 | Hiển thị chi tiết từng đơn hàng bao gồm danh sách SKU snapshot, đơn giá lúc mua, snapshot địa chỉ nhận, mã giảm giá và lịch sử cập nhật trạng thái. | `Orders`, `OrderItems`, `OrderStatusHistories`, `PaymentTransactions` |
| **UC-12.3** | Cancel Order | Customer | Không | `<<extend>>` UC-12 | Cho phép khách hàng tự hủy đơn khi đơn còn ở `PENDING_CONFIRMATION` (COD) hoặc `PENDING_PAYMENT` (VNPay), tự động hoàn trả tồn kho và voucher ví. | `Orders`, `OrderStatusHistories`, `ProductVariants`, `CustomerVouchers`, `Vouchers` |
| **UC-13** | Manage Feedback | Customer | Không | Base UC | Cho phép khách hàng đánh giá chất lượng sản phẩm đã mua và xem lại các phản hồi mình đã gửi. | `Reviews` |
| **UC-13.1** | Feedback Product | Customer | Không | `<<extend>>` UC-13 | Cho phép khách hàng chấm điểm từ 1 đến 5 sao và viết nhận xét cho mặt hàng thuộc đơn hàng đã hoàn thành (`COMPLETED`). | `Reviews`, `Orders`, `OrderItems` |
| **UC-13.2** | View My Feedback | Customer | Không | `<<extend>>` UC-13 | Hiển thị danh sách các đánh giá sản phẩm mà khách hàng đã từng gửi kèm trạng thái hiển thị của chúng. | `Reviews`, `Products` |
| **UC-14** | Manage Wishlist | Customer | Không | Độc lập (CRUD) | Cho phép khách hàng thêm sản phẩm vào danh sách yêu thích, xem danh sách và xóa sản phẩm khỏi danh sách yêu thích cá nhân. | `WishlistItems`, `Products` |
| **UC-15** | Manage Voucher | Customer | Không | Base UC | Cung cấp chức năng quản lý ví voucher cá nhân của khách hàng để phục vụ việc tích lũy và áp dụng khi đặt hàng. | `CustomerVouchers`, `Vouchers` |
| **UC-15.1** | Save Public Voucher | Customer | Không | `<<extend>>` UC-15 | Cho phép khách hàng thu thập và lưu mã voucher công khai đang có hiệu lực vào ví voucher của tài khoản *(sửa lỗi trùng mã UC-15.1)*. | `CustomerVouchers`, `Vouchers` |
| **UC-15.2** | View My Voucher | Customer | Không | `<<extend>>` UC-15 | Hiển thị danh sách các voucher trong ví cá nhân kèm trạng thái khả dụng, đã sử dụng hay hết hạn *(sửa lỗi trùng mã UC-15.1)*. | `CustomerVouchers`, `Vouchers` |
| **UC-15.3** | Delete My Voucher | Customer | Không | `<<extend>>` UC-15 | Cho phép khách hàng gỡ bỏ voucher khỏi ví nếu không còn nhu cầu sử dụng hoặc voucher đã hết hiệu lực *(sửa lỗi trùng mã UC-15.1)*. | `CustomerVouchers` |
| **UC-16** | Login | Customer, Staff, Admin | Không | Độc lập | Xác thực người dùng bằng thông tin đăng nhập (Email/SĐT và mật khẩu) để cấp quyền truy cập hệ thống theo đúng vai trò. | `Users`, `Roles` |

---

### 3.3. PHÂN HỆ STAFF — NHÂN VIÊN VẬN HÀNH (UC-17 đến UC-20 + UC-6, UC-16)
*Gồm 4 Use Case cha chuyên biệt và 14 Use Case con chuyên biệt (Tổng: 18 Use Cases chuyên biệt, cộng 2 Use Case dùng chung UC-6 & UC-16)*

| Mã UC | Tên Use Case | Tác nhân chính | Tác nhân phụ | Quan hệ UML | Mô tả chức năng (1–2 câu tiếng Việt) | Bảng CSDL / View |
|---|---|---|---|---|---|---|
| *(UC-6)* | *(Logout)* | Staff | Không | Dùng chung | Đăng xuất khỏi hệ thống quản trị nhân viên. | `Users` |
| *(UC-16)* | *(Login)* | Staff | Không | Dùng chung | Đăng nhập vào phân hệ quản trị dành cho nhân viên vận hành. | `Users`, `Roles` |
| **UC-17** | Manage Profile | Staff | Không | Base UC | Quản lý thông tin hồ sơ tài khoản làm việc của nhân viên trong giao diện quản trị. | `Users` |
| **UC-17.1** | View Profile | Staff | Không | `<<extend>>` UC-17 | Hiển thị thông tin cá nhân của nhân viên bao gồm họ tên, email, số điện thoại và vai trò công tác. | `Users` |
| **UC-17.2** | Update Profile | Staff | Không | `<<extend>>` UC-17 | Cho phép nhân viên cập nhật thông tin liên hệ như họ tên và số điện thoại cá nhân. | `Users` |
| **UC-17.3** | Change Password | Staff | Không | `<<extend>>` UC-17 | Cho phép nhân viên thay đổi mật khẩu tài khoản làm việc để tăng cường an toàn thông tin nội bộ. | `Users` |
| **UC-18** | Manage Product | Staff | Không | Base UC (CRUD) | Cho phép nhân viên tạo mới, chỉnh sửa thông tin mô tả, quản lý ảnh và trạng thái bán của các sản phẩm thú cưng. | `Products`, `Categories`, `Brands` |
| **UC-18.1** | View Detail Product | Staff | Không | `<<extend>>` UC-18 | Hiển thị toàn diện thông số kỹ thuật, mô tả, danh mục, hãng sản xuất, bộ sưu tập ảnh và các biến thể SKU của sản phẩm. | `Products`, `ProductImages`, `ProductVariants` |
| **UC-18.2** | Hide/Stop Selling Product | Staff | Không | `<<extend>>` UC-18 | Cho phép nhân viên tạm ẩn hoặc ngừng kinh doanh sản phẩm trên gian hàng công khai khi hết nguồn cung. | `Products` |
| **UC-18.3** | Manage Product Images | Staff | Không | `<<extend>>` UC-18 | Cho phép nhân viên tải lên ảnh mới, xóa ảnh cũ, sắp xếp thứ tự hiển thị và đặt ảnh đại diện chính (`IsPrimary`) cho sản phẩm *(Bổ sung mới)*. | `ProductImages` |
| **UC-18.4** | Manage Product Variants/SKU | Staff | Không | `<<extend>>` UC-18 | Cho phép nhân viên tạo mới, cập nhật thông tin biến thể SKU (kích cỡ, trọng lượng, hương vị), đơn giá bán, giá gốc và tồn kho ban đầu *(Bổ sung mới)*. | `ProductVariants` |
| **UC-19** | Manage Inventory | Staff | Không | Base UC | Phân hệ theo dõi số lượng tồn kho, lập phiếu nhập hàng mới và giám sát mức tồn kho an toàn của từng mặt hàng. | `StockImports`, `StockImportItems`, `ProductVariants` |
| **UC-19.1** | View Inventory | Staff | Không | `<<extend>>` UC-19 | Hiển thị bảng tổng hợp số lượng tồn kho thực tế của tất cả các biến thể SKU trong hệ thống kèm trạng thái kinh doanh. | `ProductVariants`, `vw_LowStockVariants` |
| **UC-19.2** | Record Stock Receipt | Staff | Không | `<<extend>>` UC-19 | Cho phép nhân viên lập phiếu nhập kho mới (`StockImports`), ghi nhận danh sách chi tiết các SKU nhập (`StockImportItems`) và tự động cộng dồn số lượng tồn kho. | `StockImports`, `StockImportItems`, `ProductVariants` |
| **UC-19.3** | View Inventory History | Staff | Không | `<<extend>>` UC-19 | Cho phép nhân viên tra cứu danh sách các phiếu nhập hàng đã thực hiện, lọc theo ngày tháng và xem chi tiết phiếu nhập. | `StockImports`, `StockImportItems` |
| **UC-19.4** | View Low Stock Alerts | Staff | Không | `<<extend>>` UC-19 | Hiển thị danh sách các SKU có số lượng tồn kho bằng hoặc thấp hơn ngưỡng cảnh báo (`StockQuantity <= LowStockThreshold`) dựa trên view `vw_LowStockVariants` *(Bổ sung mới)*. | `ProductVariants`, `vw_LowStockVariants` |
| **UC-20** | Manage Order | Staff | Không | Base UC | Cho phép nhân viên vận hành xử lý quy trình đóng gói, bàn giao vận chuyển và theo dõi toàn bộ đơn hàng của khách. | `Orders` |
| **UC-20.1** | View Order List | Staff | Không | `<<extend>>` UC-20 | Hiển thị danh sách đơn hàng cần xử lý kèm các bộ lọc trạng thái (Chờ duyệt COD, Đang chuẩn bị, Đang giao, Hoàn thành, Đã hủy). | `Orders` |
| **UC-20.2** | View Order Detail | Staff | Không | `<<extend>>` UC-20 | Hiển thị chi tiết đơn hàng gồm thông tin khách nhận, danh sách SKU cần đóng gói, ghi chú đơn hàng và lịch sử chuyển trạng thái. | `Orders`, `OrderItems`, `OrderStatusHistories`, `PaymentTransactions` |
| **UC-20.3** | Change Status Order | Staff | Không | `<<extend>>` UC-20 | Cho phép nhân viên cập nhật trạng thái đơn: Duyệt COD → Đang chuẩn bị → Đang giao → Hoàn thành, hoặc Từ chối đơn COD (bắt buộc nhập lý do từ chối). | `Orders`, `OrderStatusHistories`, `ProductVariants` |

---

### 3.4. PHÂN HỆ ADMIN — QUẢN TRỊ VIÊN (UC-21 đến UC-30, bỏ UC-28; kế thừa toàn bộ Staff)
*Gồm 9 Use Case cha chuyên biệt và 9 Use Case con chuyên biệt (Tổng: 18 Use Cases chuyên biệt, cộng quyền kế thừa Staff & UC-6, UC-16)*

| Mã UC | Tên Use Case | Tác nhân chính | Tác nhân phụ | Quan hệ UML | Mô tả chức năng (1–2 câu tiếng Việt) | Bảng CSDL / View |
|---|---|---|---|---|---|---|
| *(UC-6)* | *(Logout)* | Admin | Không | Dùng chung | Đăng xuất khỏi hệ thống quản trị cao cấp. | `Users` |
| *(UC-16)* | *(Login)* | Admin | Không | Dùng chung | Đăng nhập tài khoản quản trị viên tối cao. | `Users`, `Roles` |
| **UC-21** | Manage Account | Admin | Email Service | Base UC | Admin tra cứu theo tab loại tài khoản, cập nhật thông tin, cấp STAFF qua email chứa link đặt mật khẩu một lần và gửi lại lời mời. Chỉ hỗ trợ mật khẩu STAFF ACTIVE đã nhận tài khoản; mật khẩu/phiên cũ giữ nguyên đến khi Staff xác nhận mật khẩu mới. Không reset Customer/Admin từ màn hình này (tự dùng UC-7). Vai trò cố định, không chuyển CUSTOMER ↔ STAFF hoặc cấp ADMIN. | `Users`, `Roles`, `PasswordResetTokens` |
| **UC-21.1** | Lock/Unlock Account | Admin | Không | `<<extend>>` UC-21 | Cho phép Admin kích hoạt hoặc vô hiệu hóa tài khoản người dùng (`ACTIVE` / `LOCKED`) khi phát hiện vi phạm hoặc theo yêu cầu bảo mật. | `Users` |
| **UC-22** | Manage Category | Admin | Không | Độc lập (CRUD) | Cho phép Admin thêm mới, chỉnh sửa tên/mã, bật/tắt hoạt động và quản lý cây phân cấp 2 cấp của danh mục sản phẩm. | `Categories` |
| **UC-23** | Manage Brand | Admin | Không | Độc lập (CRUD) | Cho phép Admin thêm mới, chỉnh sửa thông tin và bật/tắt hoạt động của các thương hiệu thú cưng trên website. | `Brands` |
| **UC-24** | Manage Product | Admin | Không | Độc lập (Toàn quyền CRUD) | Cung cấp toàn quyền quản trị danh mục sản phẩm ở cấp Admin, cho phép can thiệp giá bán, chiến dịch sản phẩm và xóa mềm sản phẩm. | `Products`, `ProductVariants` |
| **UC-25** | Manage Order | Admin | Không | Base UC | Cho phép Admin giám sát toàn diện luồng đơn hàng trong hệ thống và can thiệp phê duyệt đơn hàng ở cấp quản trị. | `Orders` |
| **UC-25.1** | View Order List | Admin | Không | `<<extend>>` UC-25 | Hiển thị bảng tổng hợp toàn bộ đơn hàng trong hệ thống kèm bộ lọc nâng cao theo doanh số, trạng thái và ngày đặt. | `Orders` |
| **UC-25.2** | View Order Detail | Admin | Không | `<<extend>>` UC-25 | Hiển thị chi tiết toàn bộ chứng từ đơn hàng, thông tin thanh toán VNPay, địa chỉ giao nhận và lịch sử chuyển trạng thái. | `Orders`, `OrderItems`, `OrderStatusHistories`, `PaymentTransactions` |
| **UC-25.3** | Confirm Order | Admin | Không | `<<extend>>` UC-25 | Cho phép Admin trực tiếp duyệt và xác nhận các đơn hàng COD đang chờ duyệt (`PENDING_CONFIRMATION`) chuyển sang `CONFIRMED`. | `Orders`, `OrderStatusHistories` |
| **UC-26** | Manage Voucher | Admin | Không | Độc lập (CRUD) | Admin tạo, chỉnh sửa, bật/tắt, xóa mềm và phục hồi voucher. Mã duy nhất kể cả mã đã xóa; dùng lại bằng phục hồi bản ghi cũ về INACTIVE, giữ thời hạn/lượt đã dùng và lịch sử đơn/ví voucher. Danh sách mặc định không có bộ lọc Chưa xóa; mở Voucher đã xóa để phục hồi. | `Vouchers`, `Orders`, `CustomerVouchers` |
| **UC-27** | Manage Review | Admin | Không | Base UC | Cung cấp giao diện cho Admin kiểm duyệt chất lượng nội dung đánh giá sản phẩm của khách hàng trước khi hiển thị công khai. | `Reviews` |
| **UC-27.1** | View Review | Admin | Không | `<<extend>>` UC-27 | Hiển thị danh sách tất cả các đánh giá của người mua kèm số sao, nhận xét text, tên sản phẩm và tên khách hàng. | `Reviews`, `Products`, `Users` |
| **UC-27.2** | Hide/Show Review | Admin | Không | `<<extend>>` UC-27 | Cho phép Admin thay đổi trạng thái hiển thị của đánh giá giữa `VISIBLE` (hiện) và `HIDDEN` (ẩn đánh giá tiêu cực hoặc vi phạm) *(Sửa từ Reply Review)*. | `Reviews` |
| ~~**UC-28**~~ | ~~Manage Banner~~ | ~~Admin~~ | — | **ĐÃ XÓA BỎ** | *Đã loại bỏ hoàn toàn do bảng Banners bị cắt giảm khỏi phạm vi cơ sở dữ liệu.* | *(Không có)* |
| **UC-29** | View Report | Admin | Không | Base UC | Cung cấp cho Admin hệ thống báo cáo phân tích kinh doanh để nắm bắt hiệu quả tài chính và vận hành của cửa hàng. | `Orders`, `OrderItems`, `ProductVariants`, `StockImports` |
| **UC-29.1** | View Inventory Report | Admin | Không | `<<extend>>` UC-29 | Cung cấp báo cáo phân tích tổng lượng hàng tồn, giá trị vốn tồn kho theo danh mục và tốc độ luân chuyển hàng hóa. | `ProductVariants`, `StockImports`, `vw_StockReconciliation` |
| **UC-29.2** | View Product Sales Report | Admin | Không | `<<extend>>` UC-29 | Cung cấp bảng thống kê và biểu đồ các mặt hàng bán chạy nhất dựa trên số lượng và doanh thu tích lũy từ các đơn hàng thành công (`COMPLETED`). | `OrderItems`, `Orders`, `Products` |
| **UC-29.3** | View Completed Order Revenue | Admin | Không | `<<extend>>` UC-29 | Hiển thị biểu đồ và bảng số liệu doanh thu thực tế theo ngày/tháng từ các đơn hàng giao thành công dựa trên view `vw_RevenueByDay`. | `Orders`, `vw_RevenueByDay` |
| **UC-30** | Manage Shipping Fee | Admin | Không | Độc lập | Cho phép Admin xem và cấu hình mức phí vận chuyển cố định áp dụng cho mọi đơn hàng toàn hệ thống (mức mặc định là 30.000 VNĐ) *(Bổ sung mới)*. | Cấu hình ứng dụng (`application.yml` / Spring Bean) |

---

## 4. BẢNG THỐNG KÊ SỐ LƯỢNG USE CASE TOÀN HỆ THỐNG

### 4.1. Phân loại theo cấu trúc Parent / Sub-Use Case

| Phân hệ Actor | Số Use Case cha (Parent UCs) | Số Use Case con (Sub-UCs) | Tổng số Use Case phân hệ |
|---|:---:|:---:|:---:|
| **Guest** | 5 (UC-1, UC-2, UC-3, UC-4, UC-5) | 2 (UC-2.1, UC-2.2) | **7** |
| **Customer** | 11 (UC-6, UC-7, UC-8, UC-9, UC-10, UC-11, UC-12, UC-13, UC-14, UC-15, UC-16) | 19 (UC-8.1..8.3, UC-9.1..9.2, UC-10.1..10.4, UC-11.1..11.2, UC-12.1..12.3, UC-13.1..13.2, UC-15.1..15.3) | **30** |
| **Staff** (Chuyên biệt) | 4 (UC-17, UC-18, UC-19, UC-20) | 14 (UC-17.1..17.3, UC-18.1..18.4, UC-19.1..19.4, UC-20.1..20.3) | **18** |
| **Admin** (Chuyên biệt) | 9 (UC-21, UC-22, UC-23, UC-24, UC-25, UC-26, UC-27, UC-29, UC-30) | 9 (UC-21.1, UC-25.1..25.3, UC-27.1..27.2, UC-29.1..29.3) | **18** |
| **TỔNG CỘNG DUY NHẤT TOÀN HỆ THỐNG** | **29 Use Cases cha** | **44 Use Cases con** | **73 Use Cases** |

> **Xác nhận số lượng (Count Verification)**:
> - Tổng số Use Case duy nhất (bao gồm cả Use Case cha và con) là **73**.
> - Thỏa mãn điều kiện đề bài: **Strictly between 55 and 75** ($55 \le 73 \le 75$).
> - Không xảy ra trùng lặp mã số; mã `UC-28` đã được loại bỏ hoàn toàn; mã `UC-2.1`, `UC-2.2` và `UC-15.1`, `UC-15.2`, `UC-15.3` được phân định độc lập.

---

## 5. MA TRẬN ÁNH XẠ USE CASE VÀ THỰC THỂ CƠ SỞ DỮ LIỆU (CSDL 21 BẢNG)

| Nhóm chức năng | Use Cases liên đới | Các bảng & View CSDL chịu tác động |
|---|---|---|
| **1. Xác thực & Tài khoản** | UC-5, UC-6, UC-7, UC-8, UC-8.1..8.3, UC-9, UC-9.1..9.2, UC-16, UC-17, UC-17.1..17.3, UC-21, UC-21.1 | `Roles`, `Users`, `UserAddresses`, `PasswordResetTokens` |
| **2. Danh mục & Thương hiệu** | UC-1, UC-22, UC-23 | `Categories`, `Brands` |
| **3. Sản phẩm & Biến thể SKU** | UC-2, UC-2.1, UC-2.2, UC-3, UC-18, UC-18.1..18.4, UC-24 | `Products`, `ProductImages`, `ProductVariants`, `vw_ProductSummary` |
| **4. Giỏ hàng & Yêu thích** | UC-10, UC-10.1..10.4, UC-14 | `Carts`, `CartItems`, `WishlistItems` |
| **5. Voucher & Khuyến mãi** | UC-4, UC-11.2, UC-15, UC-15.1..15.3, UC-26 | `Vouchers`, `CustomerVouchers` |
| **6. Đặt hàng & Xử lý đơn** | UC-11, UC-12, UC-12.1..12.3, UC-20, UC-20.1..20.3, UC-25, UC-25.1..25.3 | `Orders`, `OrderItems`, `OrderStatusHistories` |
| **7. Thanh toán trực tuyến & COD** | UC-11.1 | `Orders`, `PaymentTransactions` |
| **8. Quản lý kho & Nhập hàng** | UC-19, UC-19.1..19.4 | `StockImports`, `StockImportItems`, `ProductVariants`, `vw_LowStockVariants`, `vw_StockReconciliation` |
| **9. Đánh giá sản phẩm** | UC-13, UC-13.1..13.2, UC-27, UC-27.1..27.2 | `Reviews` |
| **10. Báo cáo & Cấu hình** | UC-29, UC-29.1..29.3, UC-30 | `vw_RevenueByDay`, Cấu hình hệ thống (Shipping Fee Bean) |
