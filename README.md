# 🐾 PetShop E-Commerce System (SWP391)

> **Dự án / Project**: Hệ thống Thương Mại Điện Tử Bán Đồ Thú Cưng & Dịch Vụ Chăm Sóc PetShop (PetShop E-Commerce Monolith)  
> **Môn học / Course**: SWP391 — FPT University  
> **Nhóm thực hiện / Team**: 5 sinh viên (SE2003 - Group 03)  
> **Kiến trúc / Architecture**: Monolithic Server-Side Rendering (SSR) Spring Boot 3.3.4 MVC + Thymeleaf Layout Dialect + Spring Security 6  
> **Cơ sở dữ liệu / Database**: Microsoft SQL Server 2019+ (`PetShopDB`, 20 bảng, 4 views)  

---

## 📋 Mục lục / Table of Contents
1. [Yêu cầu hệ thống (Prerequisites)](#1-yêu-cầu-hệ-thống-prerequisites)
2. [Cài đặt cơ sở dữ liệu (Database Setup)](#2-cài-đặt-cơ-sở-dữ-liệu-database-setup)
3. [Cấu hình ứng dụng (Application Configuration & SSL Trust)](#3-cấu-hình-ứng-dụng-application-configuration--ssl-trust)
4. [Khởi chạy ứng dụng (Run Application)](#4-khởi-chạy-ứng-dụng-run-application)
5. [Tài khoản mẫu đăng nhập (Seed Credentials)](#5-tài-khoản-mẫu-đăng-nhập-seed-credentials)
6. [Quy tắc Git & Phân chia công việc 5 thành viên (Git Branching & Team Workflows)](#6-quy-tắc-git--phân-chia-công-việc-5-thành-viên-git-branching--team-workflows)
7. [Khắc phục lỗi thường gặp (Troubleshooting)](#7-khắc-phục-lỗi-thường-gặp-troubleshooting)

---

## 1. Yêu cầu hệ thống (Prerequisites)

Mỗi thành viên trong nhóm cần cài đặt đầy đủ các công cụ sau trước khi bắt đầu / Each team member must install:

| Công cụ / Tool | Phiên bản khuyến nghị / Recommended Version | Ghi chú / Notes |
|---|---|---|
| **JDK** | Java 17 LTS (Eclipse Temurin hoặc Oracle JDK) | Kiểm tra bằng / Check: `java -version` |
| **Apache Maven** | Maven 3.8+ (hoặc dùng Maven tích hợp sẵn trong IDE) | Kiểm tra bằng / Check: `mvn -version` |
| **DBMS** | Microsoft SQL Server 2019, 2022 hoặc SQL Server Express | Chạy trên cổng mặc định / Port: `1433` |
| **Database Tool** | SQL Server Management Studio (SSMS) 19+ hoặc DBeaver | Dùng để chạy script và kiểm tra dữ liệu |
| **IDE** | IntelliJ IDEA (Ultimate / Community) hoặc VS Code | Cài plugin Lombok & Thymeleaf |
| **Git** | Git 2.30+ | Dùng cho quản lý mã nguồn |

---

## 2. Cài đặt cơ sở dữ liệu (Database Setup)

Toàn bộ cấu trúc bảng, ràng buộc toàn vẹn, kiểm tra máy trạng thái và dữ liệu mẫu được định nghĩa tập trung trong file:  
👉 `PetShop_Database_Final.sql` (nằm ở thư mục gốc của repository: `../PetShop_Database_Final.sql`).

### Các bước chạy Script trong SSMS:
1. Mở **SSMS** (SQL Server Management Studio) và kết nối tới SQL Server của bạn (`localhost` hoặc `.` hoặc `.\SQLEXPRESS`).
2. Mở file `PetShop_Database_Final.sql` trong SSMS.
3. Bấm **Execute** (hoặc phím `F5`). Script sẽ tự động:
   - Tạo cơ sở dữ liệu `PetShopDB` với collation tiếng Việt (`Vietnamese_CI_AS`).
   - Tạo chính xác **20 bảng** quan hệ (Modules 1 đến 8).
   - Tạo **4 views** nghiệp vụ (`vw_ProductSummary`, `vw_LowStockVariants`, `vw_StockReconciliation`, `vw_RevenueByDay`).
   - Nạp dữ liệu mẫu (Roles, Admin/Staff/Customer accounts, Danh mục, Thương hiệu, Sản phẩm, Biến thể SKU, Voucher, Phiếu nhập kho ban đầu).
4. **Kiểm tra tính toàn vẹn ngay sau khi chạy (Data Integrity Verification)**:
   Mở một Query mới và chạy lệnh:
   ```sql
   USE PetShopDB;
   -- 1. Kiểm tra số lượng bảng (phải đúng 20 bảng):
   SELECT COUNT(*) AS TableCount FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE';

   -- 2. Kiểm tra đối soát kho (Difference phải bằng 0):
   SELECT * FROM dbo.vw_StockReconciliation WHERE Difference <> 0;
   ```
   Nếu `TableCount = 20` và kết quả truy vấn `Difference <> 0` trả về 0 dòng, cơ sở dữ liệu đã hoàn toàn sẵn sàng!

---

## 3. Cấu hình ứng dụng (Application Configuration & SSL Trust)

File cấu hình chính của dự án nằm tại: `src/main/resources/application.yml`.

### Cấu hình kết nối SQL Server mặc định (Default Connection String):
```yaml
spring:
  datasource:
    # ssl trust parameters: encrypt=true;trustServerCertificate=true;sendStringParametersAsUnicode=true;
    url: jdbc:sqlserver://localhost:1433;databaseName=PetShopDB;encrypt=true;trustServerCertificate=true;sendStringParametersAsUnicode=true;
    username: sa
    password: ${DB_PASSWORD:123456}
```

### Cách đặt mật khẩu DB riêng mà KHÔNG bị xung đột Git (Local Credentials Override):
1. Tạo một file tên là `application-local.yml` tại `src/main/resources/` (file này đã được đưa vào `.gitignore`, sẽ không bao giờ bị push lên Git).
2. Điền thông tin tài khoản SQL Server cá nhân của bạn:
   ```yaml
   spring:
     datasource:
       username: sa
       password: MatKhauCuaBan123
   ```
3. Khi chạy bằng dòng lệnh hoặc cấu hình IDE, kích hoạt profile `local`:
   `-Dspring.profiles.active=dev,local`

> ⚠️ **LƯU Ý CỰC KỲ QUAN TRỌNG VỀ HIBERNATE DDL-AUTO**:
> Trong `application.yml`, thuộc tính `spring.jpa.hibernate.ddl-auto` được đặt là **`none`** hoặc **`validate`**.  
> **TUYỆT ĐỐI KHÔNG** đổi thành `update` hoặc `create`.  
> *Lý do*: Cơ sở dữ liệu đã được thiết lập chặt chẽ bởi script SQL (có cột tính toán `LineTotal`, filtered index `UX_Users_Email`, `UX_UserAddresses_Default`, các ràng buộc CHECK phức tạp). Nếu để Hibernate tự `update`, nó có thể xóa hoặc làm hỏng các index và constraint này!

---

## 4. Khởi chạy ứng dụng (Run Application)

### Cách 1: Chạy bằng Maven CLI
Mở terminal tại thư mục `petshop-app` và chạy:
```bash
mvn clean spring-boot:run
```

### Cách 2: Chạy trực tiếp trong IDE
1. Mở thư mục `petshop-app` bằng **IntelliJ IDEA** hoặc **VS Code**.
2. Đợi Maven tải xong toàn bộ thư viện dependencies.
3. Tìm đến file: `src/main/java/com/petshop/PetShopApplication.java`.
4. Nhấp chuột phải và chọn **Run 'PetShopApplication'** (hoặc bấm biểu tượng Run xanh).

Sau khi ứng dụng khởi động thành công (thấy log `Started PetShopApplication in ... seconds`), truy cập hệ thống tại trình duyệt:  
👉 **URL**: [http://localhost:8080](http://localhost:8080)

---

## 5. Tài khoản mẫu đăng nhập (Seed Credentials)

Hệ thống đã nạp sẵn 3 tài khoản mặc định đại diện cho 3 Role với mật khẩu đã mã hóa BCrypt (`$2a$10$...`):

| Vai trò / Role | Email đăng nhập | Số điện thoại | Mật khẩu mặc định / Password | Quyền truy cập các tuyến đường / Access Scope |
|---|---|---|---|---|
| **ADMIN** | `admin@petshop.vn` | `0900000001` | `Admin@123` | Toàn quyền hệ thống, dashboard quản trị (`/admin/**`), phân quyền, voucher, danh mục, báo cáo |
| **STAFF** | `staff@petshop.vn` | `0900000002` | `Staff@123` | Quản lý sản phẩm, biến thể SKU, xử lý đơn hàng, nhập kho (`/staff/**`) |
| **CUSTOMER** | `customer@petshop.vn` | `0900000003` | `Customer@123` | Mua hàng, giỏ hàng (`/cart/**`), đặt hàng (`/checkout/**`), lịch sử đơn hàng, sổ địa chỉ |

> *Gợi ý*: Trang đăng nhập hỗ trợ nhập **Email** hoặc **Số điện thoại** vào ô tài khoản.

---

## 6. Quy tắc Git & Phân chia công việc 5 thành viên (Git Branching & Team Workflows)

Để tránh hoàn toàn xung đột mã nguồn (merge conflicts) trong suốt 7-9 tuần làm việc, nhóm tuân thủ nghiêm ngặt mô hình **Git Feature Branch Workflow** kết hợp với kiến trúc **Actor-Based Package Isolation**:

### Phân công trách nhiệm 5 thành viên (SWP391 Team):

| Thành viên | Vai trò & Trách nhiệm chính | Package Controller sở hữu | Thư mục Giao diện sở hữu | Nhánh Git làm việc |
|---|---|---|---|---|
| **Member 1 (Lead)** | Kiến trúc chung, Xác thực (Auth), Bảo mật, Hồ sơ Khách hàng | `controller.guest.AuthController`<br>`controller.customer.CustomerProfileController` | `templates/guest/login.html`<br>`register.html`<br>`templates/customer/profile.html` | `feat/m1-auth-profile` |
| **Member 2** | Danh mục, Thương hiệu, Sản phẩm & Biến thể SKU, Trang chủ | `controller.guest.HomeController`<br>`controller.guest.ProductCatalogController`<br>`controller.staff.ProductManageController` | `templates/guest/home.html`<br>`product-detail.html`<br>`templates/staff/product-*.html` | `feat/m2-catalog-product` |
| **Member 3** | Giỏ hàng, Danh sách yêu thích (Wishlist), Luồng Đặt hàng (Checkout) & VNPay | `controller.customer.CartController`<br>`controller.customer.CheckoutController` | `templates/customer/cart.html`<br>`checkout.html`<br>`order-success.html` | `feat/m3-cart-checkout` |
| **Member 4** | Xử lý Đơn hàng (Staff/Admin), Lịch sử đơn (Customer), Nhập kho (Stock Import) | `controller.staff.OrderProcessController`<br>`controller.staff.StockImportController`<br>`controller.customer.OrderHistoryController` | `templates/staff/order-*.html`<br>`stock-import.html`<br>`templates/customer/order-history.html` | `feat/m4-orders-stock` |
| **Member 5** | Quản trị Admin, Quản lý tài khoản, Voucher khuyến mãi, Đánh giá (Reviews), Báo cáo doanh thu | `controller.admin.*`<br>`controller.guest.ReviewController` | `templates/admin/*`<br>`templates/customer/review-*.html` | `feat/m5-admin-voucher-report` |

### Quy tắc Branching & Commit:
1. **Nhánh `main`**: Chỉ chứa mã nguồn ổn định nhất để nộp bài hoặc demo với giảng viên. **CẤM COMMIT TRỰC TIẾP LÊN MAIN**.
2. **Nhánh `dev`**: Nhánh tích hợp chung của cả nhóm.
3. Khi làm bất kỳ tính năng nào:
   ```bash
   git checkout dev
   git pull origin dev
   git checkout -b feat/<ten-tinh-nang>
   ```
4. Sau khi hoàn thành và test kỹ trên máy cá nhân:
   ```bash
   git add .
   git commit -m "feat(module): mo ta ngan gon chuc nang da lam"
   git push origin feat/<ten-tinh-nang>
   ```
5. Tạo **Pull Request (PR)** từ `feat/<ten-tinh-nang>` vào `dev`. Trưởng nhóm hoặc ít nhất 1 thành viên khác review trước khi merge!

---

## 7. Khắc phục lỗi thường gặp (Troubleshooting)

### 🔴 Lỗi 1: `The driver could not establish a secure connection to SQL Server by using Secure Sockets Layer (SSL) encryption. Error: "PKIX path building failed"`
- **Nguyên nhân**: SQL Server sử dụng chứng chỉ SSL tự ký (self-signed certificate) mà Java 17 mặc định không tin cậy.
- **Giải pháp**: Đảm bảo chuỗi kết nối trong `application.yml` luôn có tham số:  
  `;encrypt=true;trustServerCertificate=true;` (đã được cấu hình sẵn trong project).

### 🔴 Lỗi 2: `Cannot insert explicit value for identity column in table ... when IDENTITY_INSERT is set to OFF`
- **Nguyên nhân**: Cố tình gán `Id` thủ công khi lưu entity.
- **Giải pháp**: Tất cả các bảng (ngoại trừ khóa chính phức hợp `WishlistItems`) đều dùng `IDENTITY(1,1)`. Đảm bảo entity sử dụng `@GeneratedValue(strategy = GenerationType.IDENTITY)` và không set id khi tạo mới.

### 🔴 Lỗi 3: `The column "LineTotal" cannot be modified because it is either a computed column...`
- **Nguyên nhân**: Entity `OrderItem` cố gắng cập nhật trường `LineTotal`.
- **Giải pháp**: `LineTotal` là cột tính toán tự động trong SQL Server (`UnitPrice * Quantity`). Trong `OrderItem.java`, bắt buộc phải có annotation:  
  `@Column(name = "LineTotal", insertable = false, updatable = false)`.

### 🔴 Lỗi 4: Thymeleaf không cập nhật thay đổi khi sửa file `.html`
- **Nguyên nhân**: Cache template đang bật.
- **Giải pháp**: Trong `application.yml`, đảm bảo `spring.thymeleaf.cache: false`. Nếu dùng IntelliJ, bấm `Ctrl + F9` (Build Project) sau khi sửa HTML để trình duyệt tự nhận thay đổi.
