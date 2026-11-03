# TÀI LIỆU PRODUCT BACKLOG DỰ ÁN PET SHOP SWP391
## Kế Hoạch Phát Triển & Phân Công Lát Cắt Dọc 5 Thành Viên (Master Product Backlog)

> **Môn học**: SWP391 - Kỹ thuật Phần mềm (Đại học FPT)  
> **Nhóm thực hiện**: SE2003 - G03 (5 thành viên)  
> **Đề tài**: Nền tảng thương mại điện tử Pet Shop (Sản phẩm chăm sóc Chó và Mèo)  
> **Kiến trúc kỹ thuật**: Java 17, Spring Boot 3.3.4 (SSR Monolith), Spring Security, Spring Data JPA, Thymeleaf, SQL Server 2019+  
> **Khung thời gian**: 4 Sprints (7 – 9 tuần học kỳ SWP391)  
> **Ngôn ngữ đặc tả**: Tiếng Việt (Vietnamese)  
> **Mô hình tổ chức**: Phân rã phân cấp **Function (Phân hệ) > Feature (Tính năng) > Related Use Cases (Ca sử dụng)** theo 5 Lát cắt dọc (Vertical Slices)  
> **Độ phủ yêu cầu**: Bao phủ 100% danh mục 73 Use Cases từ `PetShop_UseCases_Final.md`, loại bỏ hoàn toàn UC-28 Banner, thay thế SMS OTP bằng Email Service, tích hợp VNPay Sandbox, khôi phục bảng CustomerVouchers.

---

## 1. TỔNG QUAN PHƯƠNG PHÁP LUẬN VÀ CẤU TRÚC PHÂN CẤP BACKLOG

Hệ thống Pet Shop E-Commerce được xây dựng theo phương pháp luận Agile/Scrum kết hợp kiến trúc phân chia **Lát cắt dọc (Vertical Slices)**. Mỗi thành viên trong nhóm 5 người đảm nhận toàn diện một lát cắt chức năng từ Cơ sở dữ liệu (Tables, Constraints, Indexes), Data Access (Spring Data JPA Repositories), Nghiệp vụ (Services, Transactions), Điều hướng (Controllers, DTOs, Validation) đến Giao diện người dùng (Thymeleaf Templates, Ajax).

Toàn bộ khối lượng công việc được phân rã theo 3 cấp độ:
1. **10 Phân hệ cốt lõi (Core Functions)**: Định nghĩa các miền nghiệp vụ lớn của sàn thương mại điện tử.
2. **26 Tính năng độc lập (`FEAT-01` đến `FEAT-26`)**: Các đơn vị giá trị nghiệp vụ có thể kiểm thử độc lập và bàn giao trong từng Sprint.
3. **73 Ca sử dụng (Related Use Cases)**: Ánh xạ trực tiếp từ tài liệu chuẩn `PetShop_UseCases_Final.md`.

---

## 2. BẢNG TỔNG THỂ PRODUCT BACKLOG (MASTER PRODUCT BACKLOG TABLE)

Bảng tổng hợp 26 tính năng của dự án với đầy đủ 6 cột bắt buộc:

| Phân hệ (Function) | Tính năng (Feature ID & Name) | Ca sử dụng liên quan (Related UCs) | Mức độ ưu tiên (Priority) | Sprint dự kiến (Estimated Sprint) | Thành viên phụ trách (Assignee) |
|---|---|---|:---:|:---:|:---:|
| **Authentication** | **FEAT-01**: Đăng ký, Đăng nhập & Đăng xuất hệ thống | UC-5, UC-6, UC-16 | **Must Have** | Sprint 1 | **Member 1** |
| **Authentication** | **FEAT-05**: Quên mật khẩu & Khôi phục qua Email Token | UC-7 | **Should Have** | Sprint 3 | **Member 1** |
| **Account Management** | **FEAT-02**: Quản lý hồ sơ cá nhân & Đổi mật khẩu | UC-8, UC-8.1, UC-8.2, UC-8.3, UC-17, UC-17.1, UC-17.2, UC-17.3 | **Must Have** | Sprint 1 | **Member 1** |
| **Account Management** | **FEAT-03**: Quản lý sổ địa chỉ giao hàng nhận hàng | UC-9, UC-9.1, UC-9.2 | **Must Have** | Sprint 2 | **Member 1** |
| **Account Management** | **FEAT-04**: Quản trị tài khoản người dùng & Cấp tài khoản nhân viên | UC-21, UC-21.1 | **Should Have** | Sprint 2 | **Member 1** |
| **Product Catalog** | **FEAT-06**: Xem danh mục & Khám phá danh sách sản phẩm | UC-1, UC-2, UC-2.1 | **Must Have** | Sprint 1 | **Member 2** |
| **Product Catalog** | **FEAT-07**: Xem thông tin chi tiết sản phẩm & Biến thể SKU | UC-2.2 | **Must Have** | Sprint 1 | **Member 2** |
| **Product Catalog** | **FEAT-08**: Tìm kiếm sản phẩm & Bộ lọc nâng cao đa tiêu chí | UC-3 | **Should Have** | Sprint 2 | **Member 2** |
| **Product Catalog** | **FEAT-09**: Quản lý thông tin sản phẩm cha & Trạng thái kinh doanh | UC-18, UC-18.1, UC-18.2, UC-24 | **Must Have** | Sprint 2 | **Member 2** |
| **Product Catalog** | **FEAT-10**: Quản lý thư viện ảnh sản phẩm, Danh mục & Thương hiệu | UC-18.3, UC-22, UC-23 | **Should Have** | Sprint 3 | **Member 2** |
| **Cart & Wishlist** | **FEAT-11**: Quản lý giỏ hàng mua sắm | UC-10, UC-10.1, UC-10.2, UC-10.3, UC-10.4 | **Must Have** | Sprint 1 | **Member 3** |
| **Cart & Wishlist** | **FEAT-15**: Quản lý danh sách sản phẩm yêu thích (Wishlist) | UC-14 | **Nice to Have** | Sprint 4 | **Member 3** |
| **Voucher / Promotion** | **FEAT-12**: Quản trị chiến dịch Voucher khuyến mãi của Admin | UC-26 | **Should Have** | Sprint 2 | **Member 3** |
| **Voucher / Promotion** | **FEAT-13**: Xem khuyến mãi công khai & Áp dụng Voucher tại Checkout | UC-4, UC-11.2 | **Must Have** | Sprint 2 | **Member 3** |
| **Voucher / Promotion** | **FEAT-14**: Quản lý ví Voucher cá nhân của khách hàng | UC-15, UC-15.1, UC-15.2, UC-15.3 | **Should Have** | Sprint 3 | **Member 3** |
| **Checkout & Payment** | **FEAT-16**: Khung cấu trúc đơn hàng & Khởi tạo giao dịch | Kiến trúc nền tảng Orders & Items | **Must Have** | Sprint 1 | **Member 4** |
| **Checkout & Payment** | **FEAT-17**: Đặt hàng Checkout & Thanh toán COD | UC-11, UC-11.1 (phần COD) | **Must Have** | Sprint 2 | **Member 4** |
| **Checkout & Payment** | **FEAT-19**: Tích hợp cổng thanh toán trực tuyến VNPay Sandbox | UC-11.1 (phần VNPay) | **Should Have** | Sprint 3 | **Member 4** |
| **Order Management** | **FEAT-18**: Vận hành xử lý đơn hàng & Điều chuyển trạng thái (Staff/Admin) | UC-20, UC-20.1, UC-20.2, UC-20.3, UC-25, UC-25.1, UC-25.2, UC-25.3 | **Must Have** | Sprint 2 | **Member 4** |
| **Order Management** | **FEAT-20**: Khách hàng theo dõi đơn, Hủy đơn & Quét đơn quá hạn | UC-12, UC-12.1, UC-12.2, UC-12.3, System Sweeper | **Should Have** | Sprint 3 | **Member 4** |
| **Inventory / Stock** | **FEAT-21**: Quản lý biến thể SKU, Định giá & Ngưỡng tồn thấp | UC-18.4 | **Must Have** | Sprint 1 | **Member 5** |
| **Inventory / Stock** | **FEAT-22**: Lập phiếu nhập kho hàng & Xem lịch sử nhập | UC-19.2, UC-19.3 | **Must Have** | Sprint 2 | **Member 5** |
| **Inventory / Stock** | **FEAT-23**: Giám sát tồn kho & Bảng cảnh báo tồn thấp | UC-19, UC-19.1, UC-19.4 | **Should Have** | Sprint 2 | **Member 5** |
| **Review / Feedback** | **FEAT-24**: Đánh giá sản phẩm đã mua & Kiểm duyệt đánh giá | UC-13, UC-13.1, UC-13.2, UC-27, UC-27.1, UC-27.2 | **Should Have** | Sprint 3 | **Member 5** |
| **Reports & Config** | **FEAT-25**: Báo cáo thống kê kinh doanh & Dashboard tổng quan | UC-29, UC-29.1, UC-29.2, UC-29.3 | **Nice to Have** | Sprint 4 | **Member 5** |
| **Reports & Config** | **FEAT-26**: Cấu hình phí vận chuyển hệ thống | UC-30 | **Nice to Have** | Sprint 4 | **Member 5** |

---

## 3. ĐẶC TẢ CHI TIẾT 26 TÍNH NĂNG (DETAILED FEATURE SPECIFICATIONS)

### 3.1. PHÂN HỆ 1: AUTHENTICATION (XÁC THỰC & BẢO MẬT)

#### `FEAT-01`: Đăng ký, Đăng nhập & Đăng xuất hệ thống
- **Phân hệ**: Authentication
- **User Story**: Là một Khách vãng lai / Người dùng hệ thống, tôi muốn đăng ký tài khoản mới bằng Email/SĐT và đăng nhập vào hệ thống để có thể thực hiện mua hàng hoặc thao tác quản trị theo quyền hạn của mình.
- **Mô tả nghiệp vụ**: Khách vãng lai điền Họ tên, Email, Số điện thoại và Mật khẩu. Hệ thống gửi link xác nhận đăng ký có hạn 24 giờ; chỉ khi xác nhận thành công mới tạo CUSTOMER, lưu hash BCrypt và khởi tạo giỏ hàng rỗng trong `Carts`. Spring Security xử lý Form Login, quản lý Session, phân quyền Role-based (`ROLE_CUSTOMER`, `ROLE_STAFF`, `ROLE_ADMIN`), bảo vệ CSRF và hỗ trợ Đăng xuất an toàn. Token ký chỉ dùng xác nhận đăng ký, không dùng làm cookie đăng nhập.
- **Use Cases liên quan**: `UC-5` (Register Account), `UC-6` (Logout), `UC-16` (Login).
- **Mức ưu tiên**: **Must Have** | **Sprint**: **Sprint 1** | **Phụ trách**: **Member 1**
- **Thực thể CSDL & Thành phần kỹ thuật**: `Roles`, `Users`, `Carts`; `SecurityConfig`, `AuthController`, `auth/login.html`, `auth/register.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Chưa xác nhận email thì chưa tạo Users/Carts; xác nhận thành công tạo CUSTOMER ACTIVE và giỏ hàng rỗng. SMTP lỗi không tạo tài khoản. Link sai/hết hạn/đã kích hoạt không tạo thêm tài khoản.
  - Chặn đăng ký trùng Email hoặc Số điện thoại (báo lỗi trường tương ứng).
  - Đăng nhập sai quá số lần hoặc tài khoản bị khóa (`LOCKED`) hiển thị thông báo lỗi rõ ràng.
  - Phân quyền điều hướng chính xác sau đăng nhập: Customer về trang chủ, Staff/Admin về trang quản trị tương ứng.

---

#### `FEAT-05`: Quên mật khẩu & Khôi phục qua Email Token
- **Phân hệ**: Authentication
- **User Story**: Là một Khách hàng quên mật khẩu, tôi muốn yêu cầu gửi liên kết đặt lại mật khẩu về Email để thiết lập mật khẩu mới một cách an toàn mà không cần hỗ trợ thủ công từ quản trị viên.
- **Mô tả nghiệp vụ**: Khách hàng nhập email đã đăng ký. Hệ thống kiểm tra, tạo mã Token ngẫu nhiên có thời hạn (15–30 phút, băm SHA-256) lưu trong bảng `PasswordResetTokens`, đồng thời gọi `Email Service` (Spring Boot JavaMailSender) gửi email chứa link đặt lại mật khẩu. Khách hàng truy cập link, nhập mật khẩu mới và xác nhận để cập nhật. Tuyệt đối không sử dụng dịch vụ SMS OTP.
- **Use Cases liên quan**: `UC-7` (Forgot Password — Tác nhân phụ: Email Service).
- **Mức ưu tiên**: **Should Have** | **Sprint**: **Sprint 3** | **Phụ trách**: **Member 1**
- **Thực thể CSDL & Thành phần kỹ thuật**: `Users`, `PasswordResetTokens`; `ForgotPasswordController`, `EmailSenderService`, `auth/forgot-password.html`, `auth/reset-password.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Token đặt lại mật khẩu là duy nhất, hết hạn sau 15–30 phút hoặc ngay sau khi đã sử dụng 1 lần.
  - Gửi email định dạng HTML chuẩn với liên kết xác thực an toàn.
  - Sau khi đổi mật khẩu thành công, token cũ bị vô hiệu hóa và tài khoản có thể đăng nhập ngay với mật khẩu mới.

---

### 3.2. PHÂN HỆ 2: ACCOUNT MANAGEMENT (QUẢN LÝ HỒ SƠ & SỔ ĐỊA CHỈ)

#### `FEAT-02`: Quản lý hồ sơ cá nhân & Đổi mật khẩu
- **Phân hệ**: Account Management
- **User Story**: Là một Khách hàng hoặc Nhân viên, tôi muốn xem thông tin cá nhân, cập nhật số điện thoại, ảnh đại diện và chủ động đổi mật khẩu định kỳ để bảo vệ tài khoản của mình.
- **Mô tả nghiệp vụ**: Cho phép người dùng đã đăng nhập xem hồ sơ, chỉnh sửa Họ tên, Số điện thoại, Avatar URL. Cung cấp chức năng Đổi mật khẩu: người dùng phải xác thực mật khẩu hiện tại trước khi nhập mật khẩu mới (kiểm tra độ dài $\ge 8$ ký tự, khớp xác nhận và khác mật khẩu cũ). Áp dụng cho cả Customer và Staff.
- **Use Cases liên quan**: `UC-8`, `UC-8.1`, `UC-8.2`, `UC-8.3` (Customer Profile), `UC-17`, `UC-17.1`, `UC-17.2`, `UC-17.3` (Staff Profile).
- **Mức ưu tiên**: **Must Have** | **Sprint**: **Sprint 1** | **Phụ trách**: **Member 1**
- **Thực thể CSDL & Thành phần kỹ thuật**: `Users`; `ProfileController`, `customer/profile.html`, `customer/change-password.html`, `staff/profile.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Cập nhật thông tin lưu đúng vào bảng `Users`.
  - Đổi mật khẩu yêu cầu xác thực đúng mật khẩu cũ; mật khẩu mới được băm BCrypt trước khi lưu.
  - Phân tách rõ ràng giao diện hồ sơ của Khách hàng và Nhân viên.

---

#### `FEAT-03`: Quản lý sổ địa chỉ giao hàng nhận hàng
- **Phân hệ**: Account Management
- **User Story**: Là một Khách hàng, tôi muốn lưu nhiều địa chỉ nhận hàng và chỉ định một địa chỉ mặc định để thuận tiện lựa chọn khi đặt hàng mà không phải nhập lại nhiều lần.
- **Mô tả nghiệp vụ**: Khách hàng quản lý danh sách địa chỉ nhận hàng trong `UserAddresses`. Các trường thông tin bao gồm Tỉnh/Thành phố, Quận/Huyện, Phường/Xã, Địa chỉ chi tiết, Tên người nhận, SĐT người nhận. Hỗ trợ Thêm mới địa chỉ (chuyển từ UC-8.4 sang UC-9.1), Sửa, Xóa mềm và Thiết lập một địa chỉ duy nhất làm Mặc định (`IsDefault = 1`, UC-9.2).
- **Use Cases liên quan**: `UC-9` (Manage Address Base UC), `UC-9.1` (Create Address), `UC-9.2` (Set Default Address).
- **Mức ưu tiên**: **Must Have** | **Sprint**: **Sprint 2** | **Phụ trách**: **Member 1**
- **Thực thể CSDL & Thành phần kỹ thuật**: `UserAddresses`; `AddressController`, `customer/address-list.html`, `customer/address-form.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Một khách hàng có thể có nhiều địa chỉ nhưng tại một thời điểm chỉ có duy nhất 1 địa chỉ có `IsDefault = 1`.
  - Dữ liệu địa chỉ độc lập không phụ thuộc API ngoài, lưu thông tin text chuẩn hóa.
  - Xóa địa chỉ mặc định sẽ tự động chọn địa chỉ hợp lệ gần nhất làm mặc định thay thế hoặc cảnh báo khách hàng.

---

#### `FEAT-04`: Quản trị tài khoản người dùng & Cấp tài khoản nhân viên qua email
- **Phân hệ**: Account Management
- **User Story**: Là một Quản trị viên (Admin), tôi muốn tra cứu danh sách người dùng, cấp tài khoản cho nhân viên mới, khóa/mở khóa tài khoản để kiểm soát an toàn vận hành hệ thống.
- **Mô tả nghiệp vụ**: Admin tìm kiếm tài khoản theo Họ tên, Email, SĐT; trang quản lý chung chia tab Tất cả/Khách hàng/Nhân viên/Quản trị viên và lọc ACTIVE/LOCKED. Cấp tài khoản STAFF bằng họ tên, email bắt buộc và SĐT. Hệ thống gửi liên kết dùng một lần, hết hạn sau 30 phút, để Staff tự đặt mật khẩu; Admin không nhập hoặc nhìn thấy mật khẩu. Staff chưa nhận tài khoản dùng Gửi lại lời mời, vô hiệu hóa link cũ. Admin chỉ được gửi link hỗ trợ mật khẩu cho STAFF ACTIVE đã nhận tài khoản; không hỗ trợ reset Customer/Admin tại màn hình quản lý. Gửi link giữ nguyên mật khẩu và phiên hiện tại đến khi Staff tự xác nhận mật khẩu mới; SMTP lỗi rollback token. Customer/Admin tự dùng Quên mật khẩu (UC-7). Không hỗ trợ đổi CUSTOMER ↔ STAFF hoặc thăng cấp ADMIN; vai trò được cố định khi tạo, dữ liệu lịch sử không chuyển chủ.
- **Use Cases liên quan**: `UC-21` (Tra cứu, cập nhật, cấp Staff và hỗ trợ truy cập Staff qua email), `UC-21.1` (Lock/Unlock Account).
- **Mức ưu tiên**: **Should Have** | **Sprint**: **Sprint 2** | **Phụ trách**: **Member 1**
- **Thực thể CSDL & Thành phần kỹ thuật**: `Roles`, `Users`; `AdminUserController`, `admin/user-list.html`, `admin/user-detail.html`, `admin/staff-create.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Khóa tài khoản có hiệu lực ngay lập tức (người dùng bị khóa không thể đăng nhập hoặc tiếp tục thao tác).
  - Chặn Admin tự khóa chính mình (ném Business Exception và hiển thị cảnh báo).
  - Tạo tài khoản Staff gán chính xác vai trò `ROLE_STAFF`; email/SĐT không trùng.
  - Staff nhận thư và tự đặt mật khẩu qua link một lần trong 30 phút; SMTP lỗi thì rollback thao tác tạo và báo lỗi.
  - Gửi lại lời mời hoặc đổi email làm link cũ mất hiệu lực. Tài khoản bị khóa không được đặt mật khẩu bằng link.
  - Chỉ Admin ACTIVE gửi link hỗ trợ cho STAFF ACTIVE đã đặt mật khẩu. Customer/Admin, Staff bị khóa hoặc đang chờ nhận tài khoản bị chặn cả ở backend.
  - Gửi link không đổi hash/cờ mật khẩu hoặc thu hồi phiên. Staff xác nhận mật khẩu mới mới làm phiên cũ hết hiệu lực; token một lần, hạn 30 phút.
  - Giao diện không có chọn role; gửi thêm roleCode/roleId qua POST không làm đổi vai trò hoặc chủ sở hữu dữ liệu.

---

### 3.3. PHÂN HỆ 3: PRODUCT CATALOG (DANH MỤC & TÌM KIẾM SẢN PHẨM)

#### `FEAT-06`: Xem danh mục & Khám phá danh sách sản phẩm
- **Phân hệ**: Product Catalog
- **User Story**: Là một Khách hàng / Khách vãng lai, tôi muốn duyệt cây danh mục và xem danh sách các sản phẩm dành cho Chó hoặc Mèo để dễ dàng tìm kiếm sản phẩm phù hợp với thú cưng của mình.
- **Mô tả nghiệp vụ**: Hiển thị trang chủ với các danh mục sản phẩm nổi bật, danh sách sản phẩm mới về, sản phẩm bán chạy. Hỗ trợ xem danh sách sản phẩm phân trang theo danh mục hoặc theo loài (`PetType`: 'DOG', 'CAT', 'BOTH').
- **Use Cases liên quan**: `UC-1` (View Categories), `UC-2` (View Product Base UC), `UC-2.1` (View Products List).
- **Mức ưu tiên**: **Must Have** | **Sprint**: **Sprint 1** | **Phụ trách**: **Member 2**
- **Thực thể CSDL & Thành phần kỹ thuật**: `Categories`, `Products`, `ProductVariants`, `ProductImages`, `vw_ProductSummary`; `PublicCatalogController`, `public/home.html`, `public/catalog.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Tải danh sách sản phẩm mượt mà, phân trang rõ ràng (12-16 sản phẩm/trang).
  - Hiển thị đúng ảnh thumbnail chính (`IsPrimary = 1`), tên sản phẩm, khoảng giá min-max từ các biến thể SKU còn hàng.

---

#### `FEAT-07`: Xem thông tin chi tiết sản phẩm & Biến thể SKU
- **Phân hệ**: Product Catalog
- **User Story**: Là một Khách hàng, tôi muốn xem thông tin chi tiết của sản phẩm bao gồm mô tả, thương hiệu, thư viện ảnh và lựa chọn biến thể SKU (trọng lượng, mùi vị) để biết giá bán và tình trạng tồn kho cụ thể.
- **Mô tả nghiệp vụ**: Hiển thị toàn bộ thông tin sản phẩm dựa trên `ProductId` hoặc `Slug`. Tích hợp slider bộ sưu tập ảnh `ProductImages`. Khi khách hàng click chọn biến thể SKU, giao diện tự động cập nhật đơn giá bán, giá gạch (nếu có khuyến mãi), và số lượng tồn kho khả dụng của biến thể đó. Hiển thị danh sách sản phẩm liên quan cùng danh mục hoặc cùng loài.
- **Use Cases liên quan**: `UC-2.2` (View Product Detail).
- **Mức ưu tiên**: **Must Have** | **Sprint**: **Sprint 1** | **Phụ trách**: **Member 2**
- **Thực thể CSDL & Thành phần kỹ thuật**: `Products`, `ProductVariants`, `ProductImages`, `Brands`; `ProductDetailController`, `public/product-detail.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Đổi biến thể SKU phản hồi ngay lập tức về giá và trạng thái còn hàng/hết hàng trên giao diện.
  - Báo lỗi 404 thân thiện nếu sản phẩm bị ngừng kinh doanh hoặc không tồn tại.

---

#### `FEAT-08`: Tìm kiếm sản phẩm & Bộ lọc nâng cao đa tiêu chí
- **Phân hệ**: Product Catalog
- **User Story**: Là một Khách hàng, tôi muốn tìm kiếm theo từ khóa và lọc sản phẩm theo nhiều tiêu chí kết hợp (Danh mục, Thương hiệu, Loài Chó/Mèo, Khoảng giá, Còn hàng, Sắp xếp) để nhanh chóng chọn được mặt hàng ưng ý.
- **Mô tả nghiệp vụ**: Cung cấp ô tìm kiếm từ khóa khớp với tên và mô tả sản phẩm. Thanh lọc đa tiêu chí (Faceted Search) sử dụng Spring Data JPA Specifications kết hợp: Danh mục (`CategoryId`), Thương hiệu (`BrandId`), Loài (`PetType`), Khoảng giá (`minPrice` - `maxPrice`), Trạng thái còn hàng (`inStockOnly`). Hỗ trợ sắp xếp: Giá tăng dần, Giá giảm dần, Mới nhất, Bán chạy nhất.
- **Use Cases liên quan**: `UC-3` (Search Products).
- **Mức ưu tiên**: **Should Have** | **Sprint**: **Sprint 2** | **Phụ trách**: **Member 2**
- **Thực thể CSDL & Thành phần kỹ thuật**: `Products`, `ProductVariants`, `Categories`, `Brands`; `CatalogFilterService`, `public/search-results.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Tìm kiếm không phân biệt chữ hoa/thường, hỗ trợ tiếng Việt có dấu và không dấu.
  - Kết hợp đồng thời nhiều bộ lọc trả về tập kết quả chính xác, giữ nguyên trạng thái filter khi chuyển trang.

---

#### `FEAT-09`: Quản lý thông tin sản phẩm cha & Trạng thái kinh doanh
- **Phân hệ**: Product Catalog
- **User Story**: Là một Nhân viên hoặc Quản trị viên, tôi muốn tạo mới, chỉnh sửa thông tin sản phẩm cha và chuyển trạng thái kinh doanh (Đang bán / Tạm ẩn) để điều phối danh mục hàng hóa.
- **Mô tả nghiệp vụ**: Staff/Admin thao tác trên màn hình quản trị sản phẩm: Thêm mới sản phẩm, cập nhật Tên, Slug SEO, Mô tả chi tiết, Danh mục, Thương hiệu, Phân loại loài (`DOG`/`CAT`/`BOTH`). Quản lý trạng thái kinh doanh `Status`: `ACTIVE` (đang kinh doanh công khai) hoặc `HIDDEN` (tạm ẩn khỏi danh mục). Áp dụng quy tắc Soft-delete, không xóa cứng nếu sản phẩm đã phát sinh giao dịch.
- **Use Cases liên quan**: `UC-18` (Manage Product Base UC), `UC-18.1` (View Detail Product), `UC-18.2` (Hide/Stop Selling Product), `UC-24` (Admin Manage Product).
- **Mức ưu tiên**: **Must Have** | **Sprint**: **Sprint 2** | **Phụ trách**: **Member 2**
- **Thực thể CSDL & Thành phần kỹ thuật**: `Products`; `AdminProductController`, `admin/product-list.html`, `admin/product-form.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Tự động sinh Slug từ Tên sản phẩm, chặn trùng lặp Slug.
  - Sản phẩm chuyển `HIDDEN` ngay lập tức không xuất hiện trên catalog công khai của Guest/Customer.

---

#### `FEAT-10`: Quản lý thư viện ảnh sản phẩm, Danh mục & Thương hiệu
- **Phân hệ**: Product Catalog
- **User Story**: Là một Nhân viên / Quản trị viên, tôi muốn tải lên nhiều hình ảnh cho sản phẩm (chỉ định ảnh đại diện chính) và quản lý cây danh mục 2 cấp, quản lý thương hiệu để cấu trúc hệ thống khoa học.
- **Mô tả nghiệp vụ**: Quản lý ảnh sản phẩm trong `ProductImages`: upload ảnh, xóa ảnh, thay đổi thứ tự hiển thị (`DisplayOrder`), đặt cờ ảnh chính (`IsPrimary = 1`). Quản trị danh mục trong `Categories` (hỗ trợ quan hệ cha-con 2 cấp, icon, slug). Quản trị thương hiệu trong `Brands` (tên hãng, logo, xuất xứ).
- **Use Cases liên quan**: `UC-18.3` (Manage Product Images - Staff NEW), `UC-22` (Manage Category CRUD - Admin), `UC-23` (Manage Brand CRUD - Admin).
- **Mức ưu tiên**: **Should Have** | **Sprint**: **Sprint 3** | **Phụ trách**: **Member 2**
- **Thực thể CSDL & Thành phần kỹ thuật**: `ProductImages`, `Categories`, `Brands`; `AdminTaxonomyController`, `admin/category-list.html`, `admin/brand-list.html`, `admin/product-gallery.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Mỗi sản phẩm có đúng 1 ảnh chính `IsPrimary = 1`.
  - Không cho phép xóa hoặc ẩn danh mục/thương hiệu nếu đang có sản phẩm kinh doanh trực thuộc.

---

### 3.4. PHÂN HỆ 4: CART & WISHLIST (GIỎ HÀNG & YÊU THÍCH)

#### `FEAT-11`: Quản lý giỏ hàng mua sắm
- **Phân hệ**: Cart & Wishlist
- **User Story**: Là một Khách hàng, tôi muốn thêm các biến thể SKU vào giỏ hàng, cập nhật số lượng, xóa sản phẩm và chọn các mặt hàng cần mua để tiến hành thanh toán.
- **Mô tả nghiệp vụ**: Khách hàng đã đăng nhập quản lý giỏ hàng (`Carts` và `CartItems`). Thao tác: Thêm SKU vào giỏ (kiểm tra tồn kho `StockQuantity > 0` và trạng thái `ACTIVE`), Xem danh sách mặt hàng trong giỏ, Tăng/giảm số lượng từng dòng hàng (chặn số lượng vượt tồn kho thực tế), Xóa từng mặt hàng hoặc làm rỗng giỏ hàng. Hỗ trợ tick chọn (`IsSelected`) các mặt hàng muốn thanh toán. Tự động tính tổng tiền tạm tính (`SubtotalAmount`).
- **Use Cases liên quan**: `UC-10` (Manage Cart Base UC), `UC-10.1` (Add Product to Cart), `UC-10.2` (View Cart), `UC-10.3` (Update Cart), `UC-10.4` (Remove Cart Item).
- **Mức ưu tiên**: **Must Have** | **Sprint**: **Sprint 1** | **Phụ trách**: **Member 3**
- **Thực thể CSDL & Thành phần kỹ thuật**: `Carts`, `CartItems`, `ProductVariants`; `CartController`, `customer/cart.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Cập nhật số lượng bằng Ajax mượt mà, phản hồi ngay lập tức tiền hàng tạm tính.
  - Chặn thêm số lượng vượt quá tồn kho thực tế của SKU.

---

#### `FEAT-15`: Quản lý danh sách sản phẩm yêu thích (Wishlist)
- **Phân hệ**: Cart & Wishlist
- **User Story**: Là một Khách hàng, tôi muốn lưu các sản phẩm yêu thích vào danh sách riêng để dễ dàng theo dõi và mua lại sau này.
- **Mô tả nghiệp vụ**: Khách hàng click biểu tượng "Trái tim" trên thẻ sản phẩm hoặc trang chi tiết để thêm sản phẩm vào `WishlistItems`. Xem danh sách các sản phẩm yêu thích, hỗ trợ xóa khỏi danh sách hoặc bấm "Chuyển vào giỏ hàng" nhanh chóng.
- **Use Cases liên quan**: `UC-14` (Manage Wishlist CRUD).
- **Mức ưu tiên**: **Nice to Have** | **Sprint**: **Sprint 4** | **Phụ trách**: **Member 3**
- **Thực thể CSDL & Thành phần kỹ thuật**: `WishlistItems`, `Products`; `WishlistController`, `customer/wishlist.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Khách hàng không thể thêm trùng lặp 1 sản phẩm vào danh sách yêu thích (ràng buộc unique trên `CustomerId` và `ProductId`).
  - Xóa sản phẩm khỏi yêu thích trực tiếp qua Ajax không cần tải lại trang.

---

### 3.5. PHÂN HỆ 5: VOUCHER / PROMOTION (KHUYẾN MÃI & VÍ VOUCHER)

#### `FEAT-12`: Quản trị chiến dịch Voucher khuyến mãi của Admin
- **Phân hệ**: Voucher / Promotion
- **User Story**: Là một Quản trị viên, tôi muốn tạo và cấu hình các chiến dịch mã giảm giá (giảm phần trăm hoặc giảm số tiền cố định, giới hạn lượt dùng, đơn tối thiểu, hạn mức) để thu hút khách hàng mua sắm.
- **Mô tả nghiệp vụ**: Admin tạo và chỉnh sửa thông tin trong bảng `Vouchers`: Mã code (viết hoa duy nhất, kể cả voucher đã xóa), Tiêu đề, Loại giảm giá (`PERCENT` kèm trần giảm tối đa `MaxDiscountAmount`, hoặc `FIXED`), Giá trị giảm (`DiscountValue`), Giá trị đơn hàng tối thiểu (`MinOrderAmount`), Ngày bắt đầu (`StartDate`), Ngày kết thúc (`EndDate`), Tổng số lượt sử dụng tối đa (`TotalUsageLimit`), Giới hạn lượt dùng trên mỗi khách (`PerCustomerLimit`). Bật/tắt (`ACTIVE`/`INACTIVE`), xóa mềm và phục hồi voucher. Danh sách mặc định chỉ hiển thị voucher chưa xóa, không có lựa chọn lọc Chưa xóa; nút Voucher đã xóa mở danh sách phục hồi. Phục hồi đúng bản ghi và mã cũ về INACTIVE, giữ lịch sử, thời hạn và lượt đã dùng; Admin kiểm tra/chỉnh sửa trước khi kích hoạt lại. Tạo hoặc đổi sang mã của voucher đã xóa bị từ chối và hướng dẫn phục hồi.
- **Use Cases liên quan**: `UC-26` (Manage Voucher CRUD - Admin).
- **Mức ưu tiên**: **Should Have** | **Sprint**: **Sprint 2** | **Phụ trách**: **Member 3**
- **Thực thể CSDL & Thành phần kỹ thuật**: `Vouchers`; `AdminVoucherController`, `admin/voucher-list.html`, `admin/voucher-form.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Ràng buộc hợp lệ chặt chẽ: `StartDate < EndDate`, `DiscountValue > 0`, phần trăm $\le 100\%$, mã Code duy nhất.
  - Không cho phép giảm `TotalUsageLimit` nhỏ hơn số lượng đã dùng thực tế `UsedCount`.
  - Chỉ Admin được xóa/phục hồi bằng POST có CSRF. Phục hồi đặt IsDeleted=false, DeletedAt=null và INACTIVE; không tạo bản ghi mới, không đổi mã/CreatedAt/thời hạn/UsedCount/giới hạn hoặc dữ liệu đơn hàng/ví voucher.
  - Phục hồi lặp lại không tắt voucher đã được kích hoạt lại. Voucher hết hạn/hết quota vẫn không dùng được đến khi Admin sửa điều kiện hợp lệ; không tự gia hạn hoặc cộng lại lượt sử dụng.

---

#### `FEAT-13`: Xem khuyến mãi công khai & Áp dụng Voucher tại Checkout
- **Phân hệ**: Voucher / Promotion
- **User Story**: Là một Khách hàng, tôi muốn xem các chương trình khuyến mãi đang diễn ra và nhập mã voucher hoặc chọn từ danh sách khi thanh toán để được giảm trừ tiền hàng hợp lệ.
- **Mô tả nghiệp vụ**: Khách vãng lai và khách hàng xem danh sách voucher công khai đang có hiệu lực. Tại màn hình Checkout, khách hàng nhập mã code hoặc chọn voucher: hệ thống kiểm tra trạng thái `ACTIVE`, trong khoảng thời gian hiệu lực, chưa hết quota toàn cục, đơn hàng thỏa mãn `MinOrderAmount`, và khách chưa vượt quá `PerCustomerLimit`. Tính toán số tiền chiết khấu `DiscountAmount` (xử lý trần giảm tối đa và chặn giảm âm tiền hàng).
- **Use Cases liên quan**: `UC-4` (View Promotions), `UC-11.2` (Apply Voucher at Checkout).
- **Mức ưu tiên**: **Must Have** | **Sprint**: **Sprint 2** | **Phụ trách**: **Member 3**
- **Thực thể CSDL & Thành phần kỹ thuật**: `Vouchers`, `CustomerVouchers`; `VoucherService`, `public/promotions.html`, modal chọn voucher trên `customer/checkout.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Tính giảm giá chính xác theo 2 quy tắc:
    - Giảm % có trần: $\min(\text{Subtotal} \times \text{DiscountValue}\%, \text{MaxDiscountAmount})$.
    - Giảm cố định không làm âm tiền hàng: $\min(\text{DiscountValue}, \text{Subtotal})$.
  - Hiển thị thông báo lỗi cụ thể nếu mã không hợp lệ, hết hạn, hoặc đơn hàng chưa đạt giá trị tối thiểu.

---

#### `FEAT-14`: Quản lý ví Voucher cá nhân của khách hàng
- **Phân hệ**: Voucher / Promotion
- **User Story**: Là một Khách hàng, tôi muốn lưu các mã giảm giá vào ví cá nhân để dành cho các đơn hàng sau và dễ dàng quản lý những voucher mình đang sở hữu.
- **Mô tả nghiệp vụ**: Khách hàng bấm "Lưu mã" từ danh sách khuyến mãi công khai để nạp vào bảng `CustomerVouchers`. Quản lý ví voucher cá nhân theo các trạng thái: Khả dụng (`IsUsed = 0` và voucher còn hạn), Đã sử dụng (`IsUsed = 1`), Hết hạn. Hỗ trợ xóa voucher không còn nhu cầu khỏi ví. Tại Checkout, các voucher trong ví tự động hiển thị để khách chọn ngay mà không cần nhớ mã code.
- **Use Cases liên quan**: `UC-15` (Manage Voucher Base UC), `UC-15.1` (Save Public Voucher), `UC-15.2` (View My Voucher), `UC-15.3` (Delete My Voucher).
- **Mức ưu tiên**: **Should Have** | **Sprint**: **Sprint 3** | **Phụ trách**: **Member 3**
- **Thực thể CSDL & Thành phần kỹ thuật**: `CustomerVouchers`, `Vouchers`; `VoucherWalletController`, `customer/voucher-wallet.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Ràng buộc `UQ_CV_Customer_Voucher` ngăn chặn lưu trùng lặp 1 voucher nhiều lần vào ví cá nhân.
  - Khi voucher được áp dụng cho đơn hàng hoàn tất đặt, trạng thái `CustomerVouchers.IsUsed` chuyển sang 1 và ghi nhận thời điểm `UsedAt`.

---

### 3.6. PHÂN HỆ 6: CHECKOUT & PAYMENT (ĐẶT HÀNG & THANH TOÁN)

#### `FEAT-16`: Khung cấu trúc đơn hàng & Khởi tạo giao dịch
- **Phân hệ**: Checkout & Payment
- **User Story**: Là một Nhà phát triển hệ thống, tôi muốn thiết lập kiến trúc thực thể đơn hàng, state machine và các giao thức giao dịch chuẩn mực để làm nền tảng cho việc đặt hàng và thanh toán.
- **Mô tả nghiệp vụ**: Xây dựng cấu trúc dữ liệu cho `Orders`, `OrderItems`, `OrderStatusHistories` và `PaymentTransactions`. Định nghĩa State Machine chuẩn 7 trạng thái đơn hàng (`PENDING_CONFIRMATION`, `PENDING_PAYMENT`, `CONFIRMED`, `PREPARING`, `SHIPPING`, `COMPLETED`, `CANCELLED`). Thiết lập cơ chế chống deadlock khi trừ tồn kho (sắp xếp danh sách biến thể tăng dần theo `VariantId ASC` trước khi xin khóa cập nhật - Global Lock Ordering).
- **Use Cases liên quan**: Kiến trúc nền tảng cho luồng Đơn hàng và Thanh toán.
- **Mức ưu tiên**: **Must Have** | **Sprint**: **Sprint 1** | **Phụ trách**: **Member 4**
- **Thực thể CSDL & Thành phần kỹ thuật**: `Orders`, `OrderItems`, `OrderStatusHistories`; `Order`, `OrderItem`, `OrderStatus` (Java Enums), `OrderRepository`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Sinh mã đơn hàng duy nhất theo định dạng `PS` + Ngày tháng + Số ngẫu nhiên (ví dụ `PS202609210089`).
  - Thiết lập đầy đủ các quan hệ thực thể JPA và các ràng buộc dữ liệu toàn vẹn.

---

#### `FEAT-17`: Đặt hàng Checkout & Thanh toán COD
- **Phân hệ**: Checkout & Payment
- **User Story**: Là một Khách hàng, tôi muốn tiến hành đặt hàng từ giỏ hàng, chọn địa chỉ nhận hàng, chọn hình thức thanh toán COD và hoàn tất đơn hàng một cách nhanh chóng.
- **Mô tả nghiệp vụ**: Khách hàng xác nhận thông tin tại màn hình Checkout: Chọn địa chỉ từ sổ địa chỉ, kiểm tra danh sách mặt hàng, áp dụng voucher (nếu có), nhập ghi chú giao hàng, chọn phương thức COD (Thanh toán khi nhận hàng). Thực thi Transaction đặt hàng nguyên tử `@Transactional`: Xác thực lại tồn kho và voucher trực tiếp từ CSDL; Trừ tồn kho có điều kiện `WHERE StockQuantity >= @Quantity`; Cập nhật quota voucher; Chụp snapshot bất biến vào `Orders` và `OrderItems` (tên SP, tên biến thể, đơn giá mua, snapshot voucher code, discount amount, địa chỉ nhận hàng); Khởi tạo trạng thái đơn `PENDING_CONFIRMATION` và thanh toán `UNPAID`; Xóa giỏ hàng đã mua; Chuyển hướng đến trang thông báo đặt hàng thành công.
- **Use Cases liên quan**: `UC-11` (Checkout Base UC), `UC-11.1` (phần COD Payment).
- **Mức ưu tiên**: **Must Have** | **Sprint**: **Sprint 2** | **Phụ trách**: **Member 4**
- **Thực thể CSDL & Thành phần kỹ thuật**: `Orders`, `OrderItems`, `OrderStatusHistories`, `UserAddresses`; `CheckoutController`, `OrderPlacementService`, `customer/checkout.html`, `customer/order-success.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Công thức tính tiền đơn hàng chính xác tuyệt đối: $\text{TotalAmount} = \text{SubtotalAmount} - \text{DiscountAmount} + \text{ShippingFee}$ (phí ship mặc định 30.000 VNĐ).
  - Tồn kho bị trừ ngay lập tức khi tạo đơn thành công. Rollback toàn bộ nếu có bất kỳ biến thể nào không đủ tồn kho.

---

#### `FEAT-19`: Tích hợp cổng thanh toán trực tuyến VNPay Sandbox
- **Phân hệ**: Checkout & Payment
- **User Story**: Là một Khách hàng, tôi muốn thanh toán đơn hàng trực tuyến an toàn qua Cổng VNPay Sandbox bằng thẻ test ngân hàng để hoàn tất thanh toán ngay khi đặt hàng.
- **Mô tả nghiệp vụ**: Khi khách hàng chọn hình thức VNPay tại Checkout, đơn hàng khởi tạo ở trạng thái `PENDING_PAYMENT` và trạng thái thanh toán `UNPAID`. Hệ thống sinh URL chuyển hướng sang VNPay Sandbox kèm chuỗi băm bảo mật HMAC-SHA512 (`vnp_SecureHash`). Tiếp nhận kết quả thanh toán tại Return URL (`/payment/vnpay-return`) và IPN URL (`/api/payment/vnpay-ipn`). Thực hiện quy trình xác thực 4 bước nghiêm ngặt: Kiểm tra Checksum hợp lệ $\rightarrow$ Kiểm tra mã phản hồi thành công `vnp_ResponseCode == "00"` $\rightarrow$ **Kiểm tra khớp số tiền tuyệt đối (Strict Amount Matching: `vnp_Amount / 100 == Order.TotalAmount`)** $\rightarrow$ Cập nhật đơn hàng sang `CONFIRMED` và thanh toán sang `PAID`, đồng thời ghi nhật ký vào `PaymentTransactions`. Nếu thanh toán thất bại hoặc khách hủy giao dịch trên VNPay, đơn được xử lý theo quy định hủy và hoàn trả tồn kho.
- **Use Cases liên quan**: `UC-11.1` (phần VNPay Online Payment Gateway).
- **Mức ưu tiên**: **Should Have** | **Sprint**: **Sprint 3** | **Phụ trách**: **Member 4**
- **Thực thể CSDL & Thành phần kỹ thuật**: `Orders`, `PaymentTransactions`, `OrderStatusHistories`; `VNPayService`, `VNPayPaymentController`, `payment/vnpay-result.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Xác thực chữ ký HMAC-SHA512 chính xác 100%, bảo vệ chống giả mạo số tiền và tham số callback.
  - Giao dịch thanh toán ghi nhận đầy đủ mã giao dịch VNPay, ngân hàng, thời gian thanh toán.

---

### 3.7. PHÂN HỆ 7: ORDER MANAGEMENT (QUẢN LÝ ĐƠN HÀNG)

#### `FEAT-18`: Vận hành xử lý đơn hàng & Điều chuyển trạng thái (Staff/Admin)
- **Phân hệ**: Order Management
- **User Story**: Là một Nhân viên hoặc Quản trị viên, tôi muốn theo dõi danh sách đơn hàng toàn hệ thống, xác nhận/từ chối đơn COD và điều chuyển trạng thái chuẩn bị, giao hàng, hoàn tất để đảm bảo đơn hàng được phục vụ nhanh chóng.
- **Mô tả nghiệp vụ**: Staff/Admin tra cứu hàng đợi đơn hàng, lọc theo trạng thái, tìm kiếm theo mã đơn, tên khách, SĐT. Thực hiện duyệt đơn COD ở `PENDING_CONFIRMATION` sang `CONFIRMED` (hoặc Từ chối `CANCELLED` kèm lý do từ chối). Điều chuyển trạng thái theo luồng tuần tự: `CONFIRMED` $\rightarrow$ `PREPARING` (Đang chuẩn bị hàng) $\rightarrow$ `SHIPPING` (Đang giao hàng) $\rightarrow$ `COMPLETED` (Giao hàng thành công). Khi chuyển sang `COMPLETED`, hệ thống tự động đổi trạng thái thanh toán của đơn COD sang `PAID`. Ghi nhật ký đầy đủ mỗi lần chuyển đổi vào `OrderStatusHistories`. Chặn mọi hành vi bỏ bước hoặc đi lùi trạng thái.
- **Use Cases liên quan**: `UC-20`, `UC-20.1`, `UC-20.2`, `UC-20.3` (Staff Order Processing), `UC-25`, `UC-25.1`, `UC-25.2`, `UC-25.3` (Admin Order Management).
- **Mức ưu tiên**: **Must Have** | **Sprint**: **Sprint 2** | **Phụ trách**: **Member 4**
- **Thực thể CSDL & Thành phần kỹ thuật**: `Orders`, `OrderItems`, `OrderStatusHistories`; `StaffOrderController`, `staff/order-queue.html`, `staff/order-detail.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Chặn hoàn toàn hành vi nhảy cóc hoặc đảo ngược trạng thái đơn.
  - Khi Staff từ chối đơn COD, tự động hoàn trả tồn kho và khôi phục voucher cá nhân của khách.

---

#### `FEAT-20`: Khách hàng theo dõi đơn, Hủy đơn & Quét đơn quá hạn
- **Phân hệ**: Order Management
- **User Story**: Là một Khách hàng, tôi muốn xem lịch sử đơn hàng, xem chi tiết tiến trình giao hàng và chủ động hủy đơn khi đơn chưa được xử lý để linh hoạt trong việc mua sắm.
- **Mô tả nghiệp vụ**: Khách hàng xem danh sách đơn hàng cá nhân, lọc theo trạng thái. Xem chi tiết đơn: danh sách sản phẩm lúc mua, giá lúc mua, snapshot voucher, timeline lịch sử trạng thái. Khách hàng có quyền Bấm Hủy đơn khi đơn còn ở trạng thái `PENDING_CONFIRMATION` (COD) hoặc `PENDING_PAYMENT` (VNPay). Khi hủy đơn, kích hoạt **cơ chế khôi phục kép (Dual-Restoration)**: chuyển đơn sang `CANCELLED`, hoàn trả số lượng tồn kho cho các biến thể SKU (`RESTORE`), khôi phục voucher ví của khách về trạng thái `AVAILABLE`, và giảm `UsedCount` toàn cục của voucher. Xây dựng tiến trình nền tự động (Background Sweeper) chạy định kỳ mỗi 5 phút quét các đơn VNPay treo quá 15 phút để tự động hủy và giải phóng tồn kho.
- **Use Cases liên quan**: `UC-12` (Manage Order Base UC), `UC-12.1` (View Order List), `UC-12.2` (View Order Detail), `UC-12.3` (Cancel Order), System Automated Sweeper.
- **Mức ưu tiên**: **Should Have** | **Sprint**: **Sprint 3** | **Phụ trách**: **Member 4**
- **Thực thể CSDL & Thành phần kỹ thuật**: `Orders`, `ProductVariants`, `CustomerVouchers`, `Vouchers`, `OrderStatusHistories`; `CustomerOrderController`, `OrderSweeperTask`, `customer/order-list.html`, `customer/order-detail.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Khách hàng không thể hủy đơn một khi đơn đã chuyển sang `CONFIRMED` hoặc các trạng thái sau.
  - Khôi phục tồn kho và voucher hoàn toàn tự động, dữ liệu kho và ví khớp chính xác sau khi hủy đơn.

---

### 3.8. PHÂN HỆ 8: INVENTORY / STOCK (QUẢN LÝ KHO HÀNG & NHẬP KHO)

#### `FEAT-21`: Quản lý biến thể SKU, Định giá & Ngưỡng tồn thấp
- **Phân hệ**: Inventory / Stock
- **User Story**: Là một Nhân viên / Quản trị viên, tôi muốn quản lý các biến thể SKU cho sản phẩm cha (trọng lượng bao bì, hương vị), thiết lập mã SKU duy nhất, đơn giá bán, giá gạch và ngưỡng tồn kho thấp để kiểm soát kinh doanh chi tiết.
- **Mô tả nghiệp vụ**: Cung cấp giao diện quản lý biến thể SKU cho từng sản phẩm cha trong bảng `ProductVariants`. Thiết lập Tên biến thể (ví dụ: "Bao 2kg - Vị Cá hồi"), Mã `SkuCode` duy nhất, Đơn giá bán (`Price`), Giá gốc/giá gạch (`OriginalPrice`), Ngưỡng cảnh báo tồn thấp (`LowStockThreshold`, mặc định ví dụ 10 sản phẩm). Bật/tắt trạng thái biến thể `Status` (`ACTIVE`/`INACTIVE`).
- **Use Cases liên quan**: `UC-18.4` (Manage Product Variants/SKU - Staff NEW).
- **Mức ưu tiên**: **Must Have** | **Sprint**: **Sprint 1** | **Phụ trách**: **Member 5**
- **Thực thể CSDL & Thành phần kỹ thuật**: `ProductVariants`, `Products`; `VariantManagementController`, `admin/variant-list.html`, `admin/variant-form.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Ràng buộc mã SKU duy nhất toàn hệ thống.
  - Giá bán phải lớn hơn 0; ngưỡng tồn kho thấp không được âm.

---

#### `FEAT-22`: Lập phiếu nhập kho hàng & Xem lịch sử nhập
- **Phân hệ**: Inventory / Stock
- **User Story**: Là một Nhân viên kho, tôi muốn lập phiếu nhập hàng cho một hoặc nhiều biến thể SKU (kèm số lượng, giá nhập, lý do nhập) và xem lịch sử các lần nhập hàng để theo dõi nguồn hàng về kho.
- **Mô tả nghiệp vụ**: Nhân viên kho lập phiếu nhập hàng theo mô hình Header-Detail chuyên biệt: `StockImports` (Mã phiếu nhập, Người lập, Ngày nhập, Tổng giá trị nhập, Ghi chú nhà cung cấp) và `StockImportItems` (Chi tiết từng SKU nhập, Số lượng nhập, Đơn giá nhập). Khi lưu phiếu nhập, hệ thống tự động cộng dồn số lượng tồn kho thực tế vào `ProductVariants.StockQuantity`. Cung cấp trang xem danh sách các phiếu nhập đã thực hiện, chi tiết từng phiếu nhập và phân trang lịch sử nhập kho.
- **Use Cases liên quan**: `UC-19.2` (Record Stock Receipt via StockImports + StockImportItems), `UC-19.3` (View Inventory History).
- **Mức ưu tiên**: **Must Have** | **Sprint**: **Sprint 2** | **Phụ trách**: **Member 5**
- **Thực thể CSDL & Thành phần kỹ thuật**: `StockImports`, `StockImportItems`, `ProductVariants`; `StockImportController`, `staff/stock-import-form.html`, `staff/stock-import-list.html`, `staff/stock-import-detail.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Nhập hàng cập nhật tăng `StockQuantity` ngay lập tức trong cùng transaction.
  - Số lượng nhập bắt buộc lớn hơn 0; hỗ trợ nhập nhiều biến thể trong cùng một phiếu nhập.

---

#### `FEAT-23`: Giám sát tồn kho & Bảng cảnh báo tồn thấp
- **Phân hệ**: Inventory / Stock
- **User Story**: Là một Nhân viên / Quản trị viên, tôi muốn theo dõi lượng tồn kho thực tế của tất cả các biến thể và nhận danh sách cảnh báo các mặt hàng sắp hết hoặc đã hết để kịp thời nhập thêm hàng.
- **Mô tả nghiệp vụ**: Màn hình quản lý tồn kho tổng thể hiển thị số lượng tồn của toàn bộ SKU, phân loại theo sản phẩm cha và danh mục. Tích hợp màn hình "Cảnh báo tồn kho thấp" dựa trên View `vw_LowStockVariants`, lọc tự động tất cả các biến thể có `StockQuantity <= LowStockThreshold`. Hiển thị nhãn cảnh báo trực quan: Đỏ (Hết hàng - `StockQuantity = 0`), Vàng (Sắp hết hàng - `StockQuantity <= LowStockThreshold`), Xanh (Còn hàng an toàn).
- **Use Cases liên quan**: `UC-19` (Manage Inventory Base UC), `UC-19.1` (View Inventory), `UC-19.4` (View Low Stock Alerts - Staff NEW).
- **Mức ưu tiên**: **Should Have** | **Sprint**: **Sprint 2** | **Phụ trách**: **Member 5**
- **Thực thể CSDL & Thành phần kỹ thuật**: `ProductVariants`, `vw_LowStockVariants`; `InventoryMonitorController`, `staff/inventory-list.html`, `staff/low-stock-alerts.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - View `vw_LowStockVariants` truy vấn nhanh chóng, phản ánh chính xác các mặt hàng chạm ngưỡng tồn thấp.
  - Cho phép xuất báo cáo nhanh hoặc liên kết trực tiếp sang nút "Tạo phiếu nhập hàng" cho biến thể đó.

---

### 3.9. PHÂN HỆ 9: REVIEW / FEEDBACK (ĐÁNH GIÁ & KIỂM DUYỆT)

#### `FEAT-24`: Đánh giá sản phẩm đã mua & Kiểm duyệt đánh giá
- **Phân hệ**: Review / Feedback
- **User Story**: Là một Khách hàng, tôi muốn đánh giá sao và viết nhận xét cho sản phẩm tôi đã mua; và là một Admin, tôi muốn trả lời khách và kiểm duyệt ẩn/hiện các đánh giá vi phạm hoặc spam.
- **Mô tả nghiệp vụ**: Khách hàng chỉ được gửi đánh giá đối với mặt hàng thuộc đơn hàng có trạng thái `COMPLETED` của chính mình. Đánh giá dạng Text-only gồm số sao (1 đến 5 sao) và nội dung nhận xét (tối thiểu 10 ký tự). Mỗi mặt hàng trong đơn chỉ được đánh giá đúng 1 lần duy nhất (`OrderItemId` độc nhất trong bảng `Reviews`). Admin xem danh sách toàn bộ đánh giá trên hệ thống và thao tác kiểm duyệt bằng cách chuyển cờ `Status` giữa `VISIBLE` (hiển thị) và `HIDDEN` (tạm ẩn đối với nhận xét phản cảm, spam). Chỉ những đánh giá `VISIBLE` mới được hiển thị trên trang chi tiết sản phẩm và tính vào số sao trung bình.
- **Use Cases liên quan**: `UC-13` (Manage Feedback Base UC), `UC-13.1` (Feedback Product), `UC-13.2` (View My Feedback), `UC-27` (Manage Review Admin Base UC), `UC-27.1` (View Review Admin), `UC-27.2` (Reply & Moderate Feedback Admin; chốt 29/10/2026).
- **Mức ưu tiên**: **Should Have** | **Sprint**: **Sprint 3** | **Phụ trách**: **Member 5**
- **Thực thể CSDL & Thành phần kỹ thuật**: `Reviews`, `OrderItems`, `Orders`; `ReviewController`, `ReviewManageController`, `guest/product-detail.html`, `customer/reviews.html`, `admin/review-list.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Chặn tuyệt đối đánh giá cho đơn hàng chưa `COMPLETED` hoặc đánh giá lại lần 2 cho cùng 1 `OrderItemId`.
  - Admin ẩn đánh giá: cả nhận xét và câu trả lời không còn công khai hoặc tính vào sao trung bình; My Feedback của chủ đánh giá vẫn giữ lịch sử.
  - Admin đang ACTIVE trả lời một lần, 1–1000 ký tự; ghi rõ người trả lời/thời điểm, không sửa nhận xét khách. Staff/Customer không có quyền trả lời. Gửi lặp/đồng thời không ghi đè; CSRF và HTML escaping được kiểm tra.
  - Dùng cùng bảng `Reviews`; chạy `database/migrations/V20261029__review_reply.sql` để thêm đúng ba cột nullable theo quyết định 29/10/2026. Xem [quy tắc và demo](FEEDBACK_REPLY_20261029.md).

---

### 3.10. PHÂN HỆ 10: REPORTS & CONFIG (BÁO CÁO & CẤU HÌNH)

#### `FEAT-25`: Báo cáo thống kê kinh doanh & Dashboard tổng quan
- **Phân hệ**: Reports & Config
- **User Story**: Là một Quản trị viên, tôi muốn xem biểu đồ doanh thu theo thời gian, thống kê đơn hàng thành công, sản phẩm bán chạy nhất và báo cáo biến động tồn kho để nắm bắt tình hình kinh doanh và đưa ra chiến lược phù hợp.
- **Mô tả nghiệp vụ**: Màn hình Dashboard quản trị hiển thị các chỉ số KPI then chốt: Tổng doanh thu lũy kế từ các đơn `COMPLETED`, Số đơn hàng hoàn tất, Số đơn bị hủy, Tổng số lượng khách hàng. Biểu đồ doanh thu theo ngày/tuần/tháng (sử dụng View `vw_RevenueByDay`). Báo cáo Top 10 sản phẩm bán chạy nhất. Báo cáo đối chiếu biến động kho (`vw_StockReconciliation`) đối chiếu giữa tổng nhập từ `StockImportItems` và tổng xuất qua `OrderItems` với tồn kho hiện tại.
- **Use Cases liên quan**: `UC-29` (View Report Base UC), `UC-29.1` (Inventory Report), `UC-29.2` (Product Sales Report), `UC-29.3` (Completed Order Revenue).
- **Mức ưu tiên**: **Nice to Have** | **Sprint**: **Sprint 4** | **Phụ trách**: **Member 5**
- **Thực thể CSDL & Thành phần kỹ thuật**: `Orders`, `OrderItems`, `StockImports`, `StockImportItems`, `vw_RevenueByDay`, `vw_StockReconciliation`; `AdminDashboardController`, `AdminReportService`, `admin/dashboard.html`, `admin/reports-sales.html`, `admin/reports-inventory.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Doanh thu chỉ được tính từ các đơn hàng có trạng thái `COMPLETED`. Các đơn hủy tuyệt đối không cộng vào doanh thu.
  - Biểu đồ trực quan, hỗ trợ chọn khoảng ngày lọc báo cáo linh hoạt.

---

#### `FEAT-26`: Cấu hình phí vận chuyển hệ thống
- **Phân hệ**: Reports & Config
- **User Story**: Là một Quản trị viên, tôi muốn xem và cấu hình mức phí vận chuyển cố định toàn hệ thống (mặc định là 30.000 VNĐ) để hệ thống tự động áp dụng chính xác cho mọi đơn hàng mới.
- **Mô tả nghiệp vụ**: Cung cấp giao diện cấu hình cho Admin xem mức phí giao hàng hiện tại (`petshop.order.default-shipping-fee`, mặc định là 30.000 VNĐ). Cho phép điều chỉnh giá trị phí ship và nạp lại vào Spring Boot Configuration Bean quản lý trong bộ nhớ (hoặc lưu tham số hệ thống), đảm bảo mọi đơn hàng được tính đúng phí ship khi checkout.
- **Use Cases liên quan**: `UC-30` (Manage Shipping Fee - Admin NEW).
- **Mức ưu tiên**: **Nice to Have** | **Sprint**: **Sprint 4** | **Phụ trách**: **Member 5**
- **Thực thể CSDL & Thành phần kỹ thuật**: `SystemConfigService`, `ShippingConfigBean`; `AdminConfigController`, `admin/system-settings.html`.
- **Tiêu chí nghiệm thu (Definition of Done - DoD)**:
  - Phí giao hàng không được là số âm.
  - Khi thay đổi, đơn hàng tạo mới nhận đúng mức phí ship mới mà không cần khởi động lại server.

---

## 4. MA TRẬN PHÂN CHIA LÁT CẮT DỌC 5 THÀNH VIÊN (WORKLOAD BALANCE)

```
+---------------------------------------------------------------------------------------------------+
|                        SE2003-G03 PET SHOP 5-MEMBER VERTICAL SLICE ARCHITECTURE                   |
+-------------------+--------------------+--------------------+--------------------+----------------+
|   THÀNH VIÊN 1    |    THÀNH VIÊN 2    |    THÀNH VIÊN 3    |    THÀNH VIÊN 4    |  THÀNH VIÊN 5  |
|   Auth & Account  |  Catalog & Search  | Cart, Wishlist &   | Order Fulfillment  | Inventory, Rev |
|                   |                    |  Voucher Wallet    |   & VNPay Gateway  | & Admin Dash   |
+-------------------+--------------------+--------------------+--------------------+----------------+
| PHÂN HỆ SỞ HỮU:   | PHÂN HỆ SỞ HỮU:    | PHÂN HỆ SỞ HỮU:    | PHÂN HỆ SỞ HỮU:    | PHÂN HỆ SỞ HỮU:|
| • Authentication  | • Product Catalog  | • Cart & Wishlist  | • Checkout & Pay   | • Inventory    |
| • Account Mgmt    |                    | • Voucher/Promotion| • Order Mgmt       | • Reviews      |
|                   |                    |                    |                    | • Reports/Conf |
+-------------------+--------------------+--------------------+--------------------+----------------+
| BẢNG DỮ LIỆU:     | BẢNG DỮ LIỆU:      | BẢNG DỮ LIỆU:      | BẢNG DỮ LIỆU:      | BẢNG DỮ LIỆU:  |
| • Roles           | • Categories       | • Carts            | • Orders           | • ProductVar   |
| • Users           | • Brands           | • CartItems        | • OrderItems       | • StockImports |
| • UserAddresses   | • Products         | • WishlistItems    | • OrderStatusHist  | • StockImportIt|
| • PasswordReset   | • ProductImages    | • Vouchers         | • PaymentTxns      | • Reviews      |
|                   | • Cột PetType      | • CustomerVouchers |                    | • System Views |
+-------------------+--------------------+--------------------+--------------------+----------------+
| SỐ TÍNH NĂNG:     | SỐ TÍNH NĂNG:      | SỐ TÍNH NĂNG:      | SỐ TÍNH NĂNG:      | SỐ TÍNH NĂNG:  |
| 5 Tính năng       | 5 Tính năng        | 5 Tính năng        | 5 Tính năng        | 6 Tính năng    |
| (FEAT-01 -> 05)   | (FEAT-06 -> 10)    | (FEAT-11 -> 15)    | (FEAT-16 -> 20)    | (FEAT-21 -> 26)|
+-------------------+--------------------+--------------------+--------------------+----------------+
| SỐ USE CASES:     | SỐ USE CASES:      | SỐ USE CASES:      | SỐ USE CASES:      | SỐ USE CASES:  |
| 13 UCs/sub-UCs    | 12 UCs/sub-UCs     | 13 UCs/sub-UCs     | 12 UCs/sub-UCs     | 12 UCs/sub-UCs |
+-------------------+--------------------+--------------------+--------------------+----------------+
```

### Chi tiết phạm vi từng thành viên:
1. **Thành viên 1 (Member 1 - Auth & User Account Lead)**:
   - *Phạm vi*: Chịu trách nhiệm bảo mật Spring Security, Form Login, Session, BCrypt, gửi Email token quên mật khẩu, sổ địa chỉ nhận hàng và quản trị tài khoản người dùng toàn hệ thống.
   - *Tính năng sở hữu*: `FEAT-01`, `FEAT-02`, `FEAT-03`, `FEAT-04`, `FEAT-05` (5 tính năng).
   - *Use Cases phụ trách*: `UC-5`, `UC-6`, `UC-7`, `UC-8`, `UC-8.1`, `UC-8.2`, `UC-8.3`, `UC-9`, `UC-9.1`, `UC-9.2`, `UC-16`, `UC-17`, `UC-17.1`, `UC-17.2`, `UC-17.3`, `UC-21`, `UC-21.1` (tương đương 13 đơn vị nghiệp vụ Use Case).
2. **Thành viên 2 (Member 2 - Catalog & Taxonomy Lead)**:
   - *Phạm vi*: Chịu trách nhiệm toàn bộ hiển thị sản phẩm công khai, cây danh mục 2 cấp, thương hiệu, tìm kiếm từ khóa, bộ lọc đa tiêu chí (JPA Specifications), quản lý gallery ảnh sản phẩm và CRUD sản phẩm cấp Staff/Admin.
   - *Tính năng sở hữu*: `FEAT-06`, `FEAT-07`, `FEAT-08`, `FEAT-09`, `FEAT-10` (5 tính năng).
   - *Use Cases phụ trách*: `UC-1`, `UC-2`, `UC-2.1`, `UC-2.2`, `UC-3`, `UC-18`, `UC-18.1`, `UC-18.2`, `UC-18.3`, `UC-22`, `UC-23`, `UC-24` (12 UCs/sub-UCs).
3. **Thành viên 3 (Member 3 - Cart, Wishlist & Voucher Promotion Lead)**:
   - *Phạm vi*: Chịu trách nhiệm giỏ hàng mua sắm, danh sách sản phẩm yêu thích (Wishlist), vòng đời chiến dịch Voucher (Admin CRUD), ví Voucher cá nhân của khách hàng (`CustomerVouchers`), và thuật toán tính giảm giá chiết khấu tại Checkout.
   - *Tính năng sở hữu*: `FEAT-11`, `FEAT-12`, `FEAT-13`, `FEAT-14`, `FEAT-15` (5 tính năng).
   - *Use Cases phụ trách*: `UC-4`, `UC-10`, `UC-10.1`, `UC-10.2`, `UC-10.3`, `UC-10.4`, `UC-11.2`, `UC-14`, `UC-15`, `UC-15.1`, `UC-15.2`, `UC-15.3`, `UC-26` (13 UCs/sub-UCs).
4. **Thành viên 4 (Member 4 - Checkout, VNPay & Order Management Lead)**:
   - *Phạm vi*: Chịu trách nhiệm kiến trúc State Machine đơn hàng 7 trạng thái, luồng tạo đơn hàng Checkout nguyên tử, tích hợp cổng thanh toán trực tuyến VNPay Sandbox, xác nhận/từ chối đơn COD, tiến trình giao hàng, hủy đơn khôi phục kho/voucher và tiến trình nền Sweeper quét đơn quá hạn.
   - *Tính năng sở hữu*: `FEAT-16`, `FEAT-17`, `FEAT-18`, `FEAT-19`, `FEAT-20` (5 tính năng).
   - *Use Cases phụ trách*: `UC-11`, `UC-11.1`, `UC-12`, `UC-12.1`, `UC-12.2`, `UC-12.3`, `UC-20`, `UC-20.1`, `UC-20.2`, `UC-20.3`, `UC-25`, `UC-25.1`, `UC-25.2`, `UC-25.3` (12 UCs/sub-UCs cốt lõi + Sweeper nền tảng).
5. **Thành viên 5 (Member 5 - Inventory, Review, Reports & Shipping Lead)**:
   - *Phạm vi*: Chịu trách nhiệm quản lý biến thể SKU và giá bán, hệ thống phiếu nhập kho Header-Detail (`StockImports` & `StockImportItems`), cảnh báo tồn kho thấp (`vw_LowStockVariants`), hệ thống đánh giá sản phẩm text 1-5 sao và kiểm duyệt ẩn/hiện của Admin, báo cáo doanh thu KPI Dashboard (`vw_RevenueByDay`), và cấu hình phí vận chuyển.
   - *Tính năng sở hữu*: `FEAT-21`, `FEAT-22`, `FEAT-23`, `FEAT-24`, `FEAT-25`, `FEAT-26` (6 tính năng).
   - *Use Cases phụ trách*: `UC-13`, `UC-13.1`, `UC-13.2`, `UC-18.4`, `UC-19`, `UC-19.1`, `UC-19.2`, `UC-19.3`, `UC-19.4`, `UC-27`, `UC-27.1`, `UC-27.2`, `UC-29`, `UC-29.1`, `UC-29.2`, `UC-29.3`, `UC-30` (12 nhóm nghiệp vụ UC chuyên sâu).

---

## 5. KẾ HOẠCH PHÁT TRIỂN 4 SPRINTS (7–9 TUẦN SWP391)

| Sprint | Thời lượng | Trọng tâm phân phối | Tính năng hoàn thành (Features Delivered) | Mức độ ưu tiên | Mục tiêu kiểm thử & Đầu ra cốt lõi |
|:---:|:---:|---|---|:---:|---|
| **Sprint 1** | Tuần 1 – Tuần 2 (2 tuần) | **Nền tảng hệ thống, Xác thực & Catalog cơ bản** | `FEAT-01`, `FEAT-02`, `FEAT-06`, `FEAT-07`, `FEAT-11`, `FEAT-16`, `FEAT-21` | **Must Have** | CSDL 21 bảng sạch chạy thành công; Nạp Seed Data; Spring Security Form Login & Session; Đăng ký, Đăng nhập, Hồ sơ cá nhân; Duyệt danh mục và chi tiết sản phẩm; Giỏ hàng cơ bản; Quản lý biến thể SKU; Khung đơn hàng. |
| **Sprint 2** | Tuần 3 – Tuần 4 (2 tuần) | **Luồng mua hàng cốt lõi (Core Purchase Flow với COD) & Quản trị kho** | `FEAT-03`, `FEAT-04`, `FEAT-08`, `FEAT-09`, `FEAT-12`, `FEAT-13`, `FEAT-17`, `FEAT-18`, `FEAT-22`, `FEAT-23` | **Must Have & Should Have** | Sổ địa chỉ giao hàng; Tìm kiếm & Lọc đa tiêu chí; Quản lý sản phẩm Staff/Admin; Quản lý Voucher Admin; Áp voucher & Đặt hàng Checkout COD hoàn tất; Vận hành duyệt đơn COD; Phiếu nhập hàng Header-Detail; Cảnh báo tồn thấp. |
| **Sprint 3** | Tuần 5 – Tuần 6 (2 tuần) | **Cổng thanh toán VNPay, Ví Voucher, Hủy đơn & Đánh giá** | `FEAT-05`, `FEAT-10`, `FEAT-14`, `FEAT-19`, `FEAT-20`, `FEAT-24` | **Should Have** | Tích hợp cổng VNPay Sandbox (HMAC-SHA512 & IPN); Quản lý ví voucher cá nhân; Hủy đơn khôi phục kép (Dual-Restore kho và voucher); Sweeper quét đơn quá hạn 15 phút; Đổi mật khẩu qua email token; Đánh giá sản phẩm Text-only & Admin kiểm duyệt. |
| **Sprint 4** | Tuần 7 – Tuần 8/9 (2–3 tuần) | **Báo cáo kinh doanh, Yêu thích, Cấu hình & Bảo vệ đồ án** | `FEAT-15`, `FEAT-25`, `FEAT-26` + Tối ưu hóa toàn diện | **Nice to Have** | Dashboard báo cáo KPI doanh thu & kho tồn; Danh sách yêu thích Wishlist; Cấu hình phí ship 30.000đ; Kiểm thử tải & xử lý tranh chấp (concurrency); Hoàn thiện báo cáo FPT SRS/Product Backlog; Demo bảo vệ đồ án. |

---

## 6. BẢNG MA TRẬN TRUY XUẤT NGUỒN GỐC USE CASE (TRACEABILITY MATRIX)

Bảng xác nhận toàn bộ 73 Use Cases từ `PetShop_UseCases_Final.md` được ánh xạ đầy đủ 100% vào các Tính năng trong Product Backlog:

| Mã Use Case | Tên Use Case | Tác nhân chính | Tính năng Backlog chứa (Feature ID) | Thành viên phụ trách |
|---|---|---|:---:|:---:|
| **UC-1** | View Categories | Guest | `FEAT-06` | Member 2 |
| **UC-2** | View Product | Guest | `FEAT-06` | Member 2 |
| **UC-2.1** | View Products List | Guest | `FEAT-06` | Member 2 |
| **UC-2.2** | View Product Detail | Guest | `FEAT-07` | Member 2 |
| **UC-3** | Search Products | Guest | `FEAT-08` | Member 2 |
| **UC-4** | View Promotions | Guest, Customer | `FEAT-13` | Member 3 |
| **UC-5** | Register Account | Guest | `FEAT-01` | Member 1 |
| **UC-6** | Logout | Customer, Staff, Admin | `FEAT-01` | Member 1 |
| **UC-7** | Forgot Password (Email Service) | Customer | `FEAT-05` | Member 1 |
| **UC-8** | Manage Profile | Customer | `FEAT-02` | Member 1 |
| **UC-8.1** | View Profile | Customer | `FEAT-02` | Member 1 |
| **UC-8.2** | Update Profile | Customer | `FEAT-02` | Member 1 |
| **UC-8.3** | Change Password | Customer | `FEAT-02` | Member 1 |
| **UC-9** | Manage Address | Customer | `FEAT-03` | Member 1 |
| **UC-9.1** | Create Address | Customer | `FEAT-03` | Member 1 |
| **UC-9.2** | Set Default Address | Customer | `FEAT-03` | Member 1 |
| **UC-10** | Manage Cart | Customer | `FEAT-11` | Member 3 |
| **UC-10.1** | Add Product to Cart | Customer | `FEAT-11` | Member 3 |
| **UC-10.2** | View Cart | Customer | `FEAT-11` | Member 3 |
| **UC-10.3** | Update Cart | Customer | `FEAT-11` | Member 3 |
| **UC-10.4** | Remove Cart Item | Customer | `FEAT-11` | Member 3 |
| **UC-11** | Checkout | Customer | `FEAT-17` | Member 4 |
| **UC-11.1** | Payment (COD & VNPay Sandbox) | Customer | `FEAT-17`, `FEAT-19` | Member 4 |
| **UC-11.2** | Apply Voucher | Customer | `FEAT-13` | Member 3 |
| **UC-12** | Manage Order | Customer | `FEAT-20` | Member 4 |
| **UC-12.1** | View Order List | Customer | `FEAT-20` | Member 4 |
| **UC-12.2** | View Order Detail | Customer | `FEAT-20` | Member 4 |
| **UC-12.3** | Cancel Order | Customer | `FEAT-20` | Member 4 |
| **UC-13** | Manage Feedback | Customer | `FEAT-24` | Member 5 |
| **UC-13.1** | Feedback Product | Customer | `FEAT-24` | Member 5 |
| **UC-13.2** | View My Feedback | Customer | `FEAT-24` | Member 5 |
| **UC-14** | Manage Wishlist | Customer | `FEAT-15` | Member 3 |
| **UC-15** | Manage Voucher | Customer | `FEAT-14` | Member 3 |
| **UC-15.1** | Save Public Voucher | Customer | `FEAT-14` | Member 3 |
| **UC-15.2** | View My Voucher | Customer | `FEAT-14` | Member 3 |
| **UC-15.3** | Delete My Voucher | Customer | `FEAT-14` | Member 3 |
| **UC-16** | Login | Customer, Staff, Admin | `FEAT-01` | Member 1 |
| **UC-17** | Manage Profile | Staff | `FEAT-02` | Member 1 |
| **UC-17.1** | View Profile | Staff | `FEAT-02` | Member 1 |
| **UC-17.2** | Update Profile | Staff | `FEAT-02` | Member 1 |
| **UC-17.3** | Change Password | Staff | `FEAT-02` | Member 1 |
| **UC-18** | Manage Product | Staff | `FEAT-09` | Member 2 |
| **UC-18.1** | View Detail Product | Staff | `FEAT-09` | Member 2 |
| **UC-18.2** | Hide/Stop Selling Product | Staff | `FEAT-09` | Member 2 |
| **UC-18.3** | Manage Product Images (NEW) | Staff | `FEAT-10` | Member 2 |
| **UC-18.4** | Manage Product Variants/SKU (NEW)| Staff | `FEAT-21` | Member 5 |
| **UC-19** | Manage Inventory | Staff | `FEAT-23` | Member 5 |
| **UC-19.1** | View Inventory | Staff | `FEAT-23` | Member 5 |
| **UC-19.2** | Record Stock Receipt | Staff | `FEAT-22` | Member 5 |
| **UC-19.3** | View Inventory History | Staff | `FEAT-22` | Member 5 |
| **UC-19.4** | View Low Stock Alerts (NEW) | Staff | `FEAT-23` | Member 5 |
| **UC-20** | Manage Order | Staff | `FEAT-18` | Member 4 |
| **UC-20.1** | View Order List | Staff | `FEAT-18` | Member 4 |
| **UC-20.2** | View Order Detail | Staff | `FEAT-18` | Member 4 |
| **UC-20.3** | Change Status Order | Staff | `FEAT-18` | Member 4 |
| **UC-21** | Manage Account | Admin | `FEAT-04` | Member 1 |
| **UC-21.1** | Lock/Unlock Account | Admin | `FEAT-04` | Member 1 |
| **UC-22** | Manage Category | Admin | `FEAT-10` | Member 2 |
| **UC-23** | Manage Brand | Admin | `FEAT-10` | Member 2 |
| **UC-24** | Manage Product (Admin) | Admin | `FEAT-09` | Member 2 |
| **UC-25** | Manage Order (Admin) | Admin | `FEAT-18` | Member 4 |
| **UC-25.1** | View Order List | Admin | `FEAT-18` | Member 4 |
| **UC-25.2** | View Order Detail | Admin | `FEAT-18` | Member 4 |
| **UC-25.3** | Confirm Order | Admin | `FEAT-18` | Member 4 |
| **UC-26** | Manage Voucher (Admin) | Admin | `FEAT-12` | Member 3 |
| **UC-27** | Manage Review (Admin) | Admin | `FEAT-24` | Member 5 |
| **UC-27.1** | View Review | Admin | `FEAT-24` | Member 5 |
| **UC-27.2** | Reply & Moderate Feedback| Admin | `FEAT-24` | Member 5 |
| ~~**UC-28**~~| ~~Manage Banner~~ | ~~Admin~~ | **ĐÃ XÓA HOÀN TOÀN** | *Không phân công* |
| **UC-29** | View Report | Admin | `FEAT-25` | Member 5 |
| **UC-29.1** | View Inventory Report | Admin | `FEAT-25` | Member 5 |
| **UC-29.2** | View Product Sales Report | Admin | `FEAT-25` | Member 5 |
| **UC-29.3** | View Completed Order Revenue | Admin | `FEAT-25` | Member 5 |
| **UC-30** | Manage Shipping Fee (NEW) | Admin | `FEAT-26` | Member 5 |
