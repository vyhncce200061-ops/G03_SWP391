/* =====================================================================
   PETSHOP E-COMMERCE — DATABASE SCRIPT (FINAL)
   Môn: SWP391 | Nhóm SE2003-G03 | SQL Server 2019+
   ---------------------------------------------------------------------
   21 bảng | 8 module | đã chỉnh theo góp ý của cô & UC-15:
     - THÊM LẠI CustomerVouchers (Ví voucher khách hàng: UC-15; Orders không FK bảng này)
     - BỎ  InventoryTransactions (Orders/OrderItems đã là "xuất kho")
     - THÊM StockImports + StockImportItems (phiếu NHẬP KHO)
     - THÊM PasswordResetTokens  (chức năng Quên mật khẩu)
   Tài khoản mẫu:  admin@petshop.vn / Admin@123
                   staff@petshop.vn / Staff@123
                   customer@petshop.vn / Customer@123
   Công thức tồn kho:  StockQuantity = Tổng nhập - Tổng bán (đơn chưa hủy)
   ===================================================================== */

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;   -- bắt buộc cho filtered index
SET ANSI_NULLS ON;
GO

IF DB_ID(N'PetShopDB') IS NULL
    CREATE DATABASE PetShopDB COLLATE Vietnamese_CI_AS;
GO
USE PetShopDB;
GO
SET QUOTED_IDENTIFIER ON;
GO

/* ---------------------------------------------------------------------
   0. DỌN DẸP (chạy lại script nhiều lần được)
   BOOTSTRAP/RESET: phần dưới xóa bảng và dữ liệu. Database đã có dữ liệu
   phải dùng database/migrations và script sửa riêng, không chạy lại file này.
   --------------------------------------------------------------------- */
DROP VIEW IF EXISTS dbo.vw_RevenueByDay;
DROP VIEW IF EXISTS dbo.vw_StockReconciliation;
DROP VIEW IF EXISTS dbo.vw_LowStockVariants;
DROP VIEW IF EXISTS dbo.vw_ProductSummary;
GO
DROP TABLE IF EXISTS dbo.Reviews;
DROP TABLE IF EXISTS dbo.PaymentTransactions;
DROP TABLE IF EXISTS dbo.OrderStatusHistories;
DROP TABLE IF EXISTS dbo.OrderItems;
DROP TABLE IF EXISTS dbo.Orders;
DROP TABLE IF EXISTS dbo.StockImportItems;
DROP TABLE IF EXISTS dbo.StockImports;
DROP TABLE IF EXISTS dbo.WishlistItems;
DROP TABLE IF EXISTS dbo.CartItems;
DROP TABLE IF EXISTS dbo.Carts;
DROP TABLE IF EXISTS dbo.CustomerVouchers;
DROP TABLE IF EXISTS dbo.Vouchers;
DROP TABLE IF EXISTS dbo.ProductImages;
DROP TABLE IF EXISTS dbo.ProductVariants;
DROP TABLE IF EXISTS dbo.Products;
DROP TABLE IF EXISTS dbo.Brands;
DROP TABLE IF EXISTS dbo.Categories;
DROP TABLE IF EXISTS dbo.PasswordResetTokens;
DROP TABLE IF EXISTS dbo.UserAddresses;
DROP TABLE IF EXISTS dbo.Users;
DROP TABLE IF EXISTS dbo.Roles;
GO

/* =====================================================================
   MODULE 1 — TÀI KHOẢN (4 bảng)
   ===================================================================== */

CREATE TABLE dbo.Roles (
    Id        INT IDENTITY(1,1) NOT NULL,
    Code      VARCHAR(20)   NOT NULL,
    Name      NVARCHAR(100) NOT NULL,
    CreatedAt DATETIME2(0)  NOT NULL CONSTRAINT DF_Roles_CreatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT PK_Roles PRIMARY KEY (Id),
    CONSTRAINT UQ_Roles_Code UNIQUE (Code),
    CONSTRAINT CK_Roles_Code CHECK (Code IN ('CUSTOMER','STAFF','ADMIN'))
);
GO

CREATE TABLE dbo.Users (
    Id           BIGINT IDENTITY(1,1) NOT NULL,
    RoleId       INT            NOT NULL,
    Email        NVARCHAR(255)  NULL,
    Phone        VARCHAR(20)    NULL,
    PasswordHash NVARCHAR(500)  NOT NULL,           -- BCrypt
    MustChangePassword BIT NOT NULL CONSTRAINT DF_Users_MustChangePassword DEFAULT 0,
    FullName     NVARCHAR(150)  NOT NULL,
    AvatarUrl    NVARCHAR(1000) NULL,
    Status       VARCHAR(20)    NOT NULL CONSTRAINT DF_Users_Status DEFAULT 'ACTIVE',
    LastLoginAt  DATETIME2(0)   NULL,
    CreatedAt    DATETIME2(0)   NOT NULL CONSTRAINT DF_Users_CreatedAt DEFAULT SYSDATETIME(),
    UpdatedAt    DATETIME2(0)   NOT NULL CONSTRAINT DF_Users_UpdatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT PK_Users PRIMARY KEY (Id),
    CONSTRAINT FK_Users_Roles FOREIGN KEY (RoleId) REFERENCES dbo.Roles(Id),
    CONSTRAINT CK_Users_Status  CHECK (Status IN ('ACTIVE','LOCKED')),
    CONSTRAINT CK_Users_Contact CHECK (Email IS NOT NULL OR Phone IS NOT NULL)
);
CREATE UNIQUE INDEX UX_Users_Email ON dbo.Users(Email) WHERE Email IS NOT NULL;
CREATE UNIQUE INDEX UX_Users_Phone ON dbo.Users(Phone) WHERE Phone IS NOT NULL;
CREATE INDEX IX_Users_Role ON dbo.Users(RoleId);
GO

CREATE TABLE dbo.UserAddresses (
    Id             BIGINT IDENTITY(1,1) NOT NULL,
    CustomerId     BIGINT        NOT NULL,
    RecipientName  NVARCHAR(150) NOT NULL,
    RecipientPhone VARCHAR(20)   NOT NULL,
    Province       NVARCHAR(100) NOT NULL,
    District       NVARCHAR(100) NOT NULL,
    Ward           NVARCHAR(100) NOT NULL,
    AddressLine    NVARCHAR(300) NOT NULL,
    IsDefault      BIT           NOT NULL CONSTRAINT DF_UserAddresses_IsDefault DEFAULT 0,
    IsDeleted      BIT           NOT NULL CONSTRAINT DF_UserAddresses_IsDeleted DEFAULT 0,
    CreatedAt      DATETIME2(0)  NOT NULL CONSTRAINT DF_UserAddresses_CreatedAt DEFAULT SYSDATETIME(),
    UpdatedAt      DATETIME2(0)  NOT NULL CONSTRAINT DF_UserAddresses_UpdatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT PK_UserAddresses PRIMARY KEY (Id),
    CONSTRAINT FK_UserAddresses_Users FOREIGN KEY (CustomerId) REFERENCES dbo.Users(Id)
);
-- Mỗi khách chỉ có tối đa 1 địa chỉ mặc định (chưa xóa)
CREATE UNIQUE INDEX UX_UserAddresses_Default
    ON dbo.UserAddresses(CustomerId) WHERE IsDefault = 1 AND IsDeleted = 0;
CREATE INDEX IX_UserAddresses_Customer ON dbo.UserAddresses(CustomerId, IsDeleted);
GO

-- Quên mật khẩu: lưu HASH của token gửi qua email (không lưu token gốc)
CREATE TABLE dbo.PasswordResetTokens (
    Id        BIGINT IDENTITY(1,1) NOT NULL,
    UserId    BIGINT       NOT NULL,
    TokenHash VARCHAR(128) NOT NULL,
    ExpiresAt DATETIME2(0) NOT NULL,
    UsedAt    DATETIME2(0) NULL,
    CreatedAt DATETIME2(0) NOT NULL CONSTRAINT DF_PwdReset_CreatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT PK_PasswordResetTokens PRIMARY KEY (Id),
    CONSTRAINT FK_PwdReset_Users FOREIGN KEY (UserId) REFERENCES dbo.Users(Id),
    CONSTRAINT UQ_PwdReset_TokenHash UNIQUE (TokenHash),
    CONSTRAINT CK_PwdReset_Expires CHECK (ExpiresAt > CreatedAt)
);
CREATE INDEX IX_PwdReset_User ON dbo.PasswordResetTokens(UserId);
GO

/* =====================================================================
   MODULE 2 — CATALOG (5 bảng)
   ===================================================================== */

CREATE TABLE dbo.Categories (
    Id               INT IDENTITY(1,1) NOT NULL,
    ParentCategoryId INT            NULL,           -- NULL = danh mục gốc (tối đa 2 cấp)
    Code             VARCHAR(50)    NOT NULL,
    Name             NVARCHAR(150)  NOT NULL,
    Description      NVARCHAR(1000) NULL,
    SortOrder        INT            NOT NULL CONSTRAINT DF_Categories_SortOrder DEFAULT 0,
    IsActive         BIT            NOT NULL CONSTRAINT DF_Categories_IsActive DEFAULT 1,
    CreatedAt        DATETIME2(0)   NOT NULL CONSTRAINT DF_Categories_CreatedAt DEFAULT SYSDATETIME(),
    UpdatedAt        DATETIME2(0)   NOT NULL CONSTRAINT DF_Categories_UpdatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT PK_Categories PRIMARY KEY (Id),
    CONSTRAINT UQ_Categories_Code UNIQUE (Code),
    CONSTRAINT FK_Categories_Parent FOREIGN KEY (ParentCategoryId) REFERENCES dbo.Categories(Id),
    CONSTRAINT CK_Categories_NoSelfParent CHECK (ParentCategoryId IS NULL OR ParentCategoryId <> Id)
);
CREATE INDEX IX_Categories_Parent ON dbo.Categories(ParentCategoryId);
GO

CREATE TABLE dbo.Brands (
    Id          INT IDENTITY(1,1) NOT NULL,
    Name        NVARCHAR(150)  NOT NULL,
    Description NVARCHAR(1000) NULL,
    LogoUrl     NVARCHAR(1000) NULL,
    IsActive    BIT            NOT NULL CONSTRAINT DF_Brands_IsActive DEFAULT 1,
    CreatedAt   DATETIME2(0)   NOT NULL CONSTRAINT DF_Brands_CreatedAt DEFAULT SYSDATETIME(),
    UpdatedAt   DATETIME2(0)   NOT NULL CONSTRAINT DF_Brands_UpdatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT PK_Brands PRIMARY KEY (Id),
    CONSTRAINT UQ_Brands_Name UNIQUE (Name)
);
GO

CREATE TABLE dbo.Products (
    Id                BIGINT IDENTITY(1,1) NOT NULL,
    CategoryId        INT            NOT NULL,
    BrandId           INT            NULL,
    PetType           VARCHAR(10)    NOT NULL CONSTRAINT DF_Products_PetType DEFAULT 'BOTH',
    Name              NVARCHAR(250)  NOT NULL,
    Slug              VARCHAR(300)   NOT NULL,
    ShortDescription  NVARCHAR(1000) NULL,
    Description       NVARCHAR(MAX)  NULL,
    UsageInstructions NVARCHAR(MAX)  NULL,
    Status            VARCHAR(20)    NOT NULL CONSTRAINT DF_Products_Status DEFAULT 'ACTIVE',
    IsFeatured        BIT            NOT NULL CONSTRAINT DF_Products_IsFeatured DEFAULT 0,
    CreatedByUserId   BIGINT         NOT NULL,
    UpdatedByUserId   BIGINT         NULL,
    CreatedAt         DATETIME2(0)   NOT NULL CONSTRAINT DF_Products_CreatedAt DEFAULT SYSDATETIME(),
    UpdatedAt         DATETIME2(0)   NOT NULL CONSTRAINT DF_Products_UpdatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT PK_Products PRIMARY KEY (Id),
    CONSTRAINT UQ_Products_Slug UNIQUE (Slug),
    CONSTRAINT FK_Products_Categories FOREIGN KEY (CategoryId) REFERENCES dbo.Categories(Id),
    CONSTRAINT FK_Products_Brands     FOREIGN KEY (BrandId)    REFERENCES dbo.Brands(Id),
    CONSTRAINT FK_Products_CreatedBy  FOREIGN KEY (CreatedByUserId) REFERENCES dbo.Users(Id),
    CONSTRAINT FK_Products_UpdatedBy  FOREIGN KEY (UpdatedByUserId) REFERENCES dbo.Users(Id),
    CONSTRAINT CK_Products_PetType CHECK (PetType IN ('DOG','CAT','BOTH')),
    CONSTRAINT CK_Products_Status  CHECK (Status IN ('ACTIVE','HIDDEN','DISCONTINUED'))
);
CREATE INDEX IX_Products_Category ON dbo.Products(CategoryId, Status);
CREATE INDEX IX_Products_Brand    ON dbo.Products(BrandId);
CREATE INDEX IX_Products_Listing  ON dbo.Products(Status, PetType, IsFeatured, CreatedAt DESC);
GO

CREATE TABLE dbo.ProductImages (
    Id        BIGINT IDENTITY(1,1) NOT NULL,
    ProductId BIGINT         NOT NULL,
    ImageUrl  NVARCHAR(1000) NOT NULL,
    AltText   NVARCHAR(300)  NULL,
    SortOrder INT            NOT NULL CONSTRAINT DF_ProductImages_SortOrder DEFAULT 0,
    IsPrimary BIT            NOT NULL CONSTRAINT DF_ProductImages_IsPrimary DEFAULT 0,
    CreatedAt DATETIME2(0)   NOT NULL CONSTRAINT DF_ProductImages_CreatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT PK_ProductImages PRIMARY KEY (Id),
    CONSTRAINT FK_ProductImages_Products FOREIGN KEY (ProductId)
        REFERENCES dbo.Products(Id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX UX_ProductImages_Primary ON dbo.ProductImages(ProductId) WHERE IsPrimary = 1;
CREATE INDEX IX_ProductImages_Product ON dbo.ProductImages(ProductId, SortOrder);
GO

CREATE TABLE dbo.ProductVariants (
    Id                BIGINT IDENTITY(1,1) NOT NULL,
    ProductId         BIGINT         NOT NULL,
    SkuCode           VARCHAR(80)    NOT NULL,
    VariantName       NVARCHAR(200)  NOT NULL,       -- "Bao 1kg", "Vị Gà", "Size S"
    Price             DECIMAL(18,2)  NOT NULL,       -- giá bán hiện tại (VNĐ)
    OriginalPrice     DECIMAL(18,2)  NULL,           -- giá gốc gạch ngang (nếu đang giảm giá)
    StockQuantity     INT            NOT NULL CONSTRAINT DF_Variants_Stock DEFAULT 0,
    LowStockThreshold INT            NOT NULL CONSTRAINT DF_Variants_LowStock DEFAULT 5,
    Status            VARCHAR(20)    NOT NULL CONSTRAINT DF_Variants_Status DEFAULT 'ACTIVE',
    CreatedAt         DATETIME2(0)   NOT NULL CONSTRAINT DF_Variants_CreatedAt DEFAULT SYSDATETIME(),
    UpdatedAt         DATETIME2(0)   NOT NULL CONSTRAINT DF_Variants_UpdatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT PK_ProductVariants PRIMARY KEY (Id),
    CONSTRAINT UQ_ProductVariants_Sku UNIQUE (SkuCode),
    CONSTRAINT FK_ProductVariants_Products FOREIGN KEY (ProductId) REFERENCES dbo.Products(Id),
    CONSTRAINT CK_Variants_Price    CHECK (Price >= 0),
    CONSTRAINT CK_Variants_Original CHECK (OriginalPrice IS NULL OR OriginalPrice >= Price),
    CONSTRAINT CK_Variants_Stock    CHECK (StockQuantity >= 0),
    CONSTRAINT CK_Variants_LowStock CHECK (LowStockThreshold >= 0),
    CONSTRAINT CK_Variants_Status   CHECK (Status IN ('ACTIVE','INACTIVE','DISCONTINUED'))
);
CREATE INDEX IX_ProductVariants_Product ON dbo.ProductVariants(ProductId, Status);
GO

/* =====================================================================
   MODULE 3 — GIỎ HÀNG & YÊU THÍCH (3 bảng)
   ===================================================================== */

CREATE TABLE dbo.Carts (
    Id        BIGINT IDENTITY(1,1) NOT NULL,
    UserId    BIGINT       NOT NULL,
    CreatedAt DATETIME2(0) NOT NULL CONSTRAINT DF_Carts_CreatedAt DEFAULT SYSDATETIME(),
    UpdatedAt DATETIME2(0) NOT NULL CONSTRAINT DF_Carts_UpdatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT PK_Carts PRIMARY KEY (Id),
    CONSTRAINT UQ_Carts_User UNIQUE (UserId),          -- 1 User : 1 Cart
    CONSTRAINT FK_Carts_Users FOREIGN KEY (UserId) REFERENCES dbo.Users(Id)
);
GO

CREATE TABLE dbo.CartItems (
    Id         BIGINT IDENTITY(1,1) NOT NULL,
    CartId     BIGINT       NOT NULL,
    VariantId  BIGINT       NOT NULL,
    Quantity   INT          NOT NULL,
    IsSelected BIT          NOT NULL CONSTRAINT DF_CartItems_IsSelected DEFAULT 1,
    CreatedAt  DATETIME2(0) NOT NULL CONSTRAINT DF_CartItems_CreatedAt DEFAULT SYSDATETIME(),
    UpdatedAt  DATETIME2(0) NOT NULL CONSTRAINT DF_CartItems_UpdatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT PK_CartItems PRIMARY KEY (Id),
    CONSTRAINT FK_CartItems_Carts    FOREIGN KEY (CartId)    REFERENCES dbo.Carts(Id) ON DELETE CASCADE,
    CONSTRAINT FK_CartItems_Variants FOREIGN KEY (VariantId) REFERENCES dbo.ProductVariants(Id),
    CONSTRAINT UQ_CartItems_CartVariant UNIQUE (CartId, VariantId),
    CONSTRAINT CK_CartItems_Quantity CHECK (Quantity > 0)
);
CREATE INDEX IX_CartItems_Variant ON dbo.CartItems(VariantId);
GO

CREATE TABLE dbo.WishlistItems (
    CustomerId BIGINT       NOT NULL,
    ProductId  BIGINT       NOT NULL,
    CreatedAt  DATETIME2(0) NOT NULL CONSTRAINT DF_Wishlist_CreatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT PK_WishlistItems PRIMARY KEY (CustomerId, ProductId),
    CONSTRAINT FK_Wishlist_Users    FOREIGN KEY (CustomerId) REFERENCES dbo.Users(Id),
    CONSTRAINT FK_Wishlist_Products FOREIGN KEY (ProductId)  REFERENCES dbo.Products(Id) ON DELETE CASCADE
);
CREATE INDEX IX_Wishlist_Product ON dbo.WishlistItems(ProductId);
GO

/* =====================================================================
   MODULE 4 — VOUCHER (2 bảng)
   ===================================================================== */

CREATE TABLE dbo.Vouchers (
    Id                BIGINT IDENTITY(1,1) NOT NULL,
    Code              VARCHAR(50)    NOT NULL,
    Title             NVARCHAR(150)  NOT NULL,
    Description       NVARCHAR(1000) NULL,
    DiscountType      VARCHAR(20)    NOT NULL,          -- PERCENT | FIXED
    DiscountValue     DECIMAL(18,2)  NOT NULL,          -- 10 = 10%  hoặc 50000 = 50.000đ
    MinOrderAmount    DECIMAL(18,2)  NOT NULL CONSTRAINT DF_Vouchers_MinOrder DEFAULT 0,
    MaxDiscountAmount DECIMAL(18,2)  NULL,              -- trần giảm (cho PERCENT)
    StartDate         DATETIME2(0)   NOT NULL,
    EndDate           DATETIME2(0)   NOT NULL,
    TotalUsageLimit   INT            NOT NULL,
    UsedCount         INT            NOT NULL CONSTRAINT DF_Vouchers_UsedCount DEFAULT 0,
    PerCustomerLimit  INT            NOT NULL CONSTRAINT DF_Vouchers_PerCustomer DEFAULT 1,
    Status            VARCHAR(20)    NOT NULL CONSTRAINT DF_Vouchers_Status DEFAULT 'ACTIVE',
    IsDeleted         BIT            NOT NULL CONSTRAINT DF_Vouchers_IsDeleted DEFAULT 0,
    DeletedAt         DATETIME2(0)   NULL,
    CreatedAt         DATETIME2(0)   NOT NULL CONSTRAINT DF_Vouchers_CreatedAt DEFAULT SYSDATETIME(),
    UpdatedAt         DATETIME2(0)   NOT NULL CONSTRAINT DF_Vouchers_UpdatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT PK_Vouchers PRIMARY KEY (Id),
    CONSTRAINT UQ_Vouchers_Code UNIQUE (Code),
    CONSTRAINT CK_Vouchers_Type         CHECK (DiscountType IN ('PERCENT','FIXED')),
    CONSTRAINT CK_Vouchers_Value        CHECK (DiscountValue > 0),
    CONSTRAINT CK_Vouchers_PercentRange CHECK (DiscountType <> 'PERCENT' OR DiscountValue <= 100),
    CONSTRAINT CK_Vouchers_MinOrder     CHECK (MinOrderAmount >= 0),
    CONSTRAINT CK_Vouchers_MaxDiscount  CHECK (MaxDiscountAmount IS NULL OR MaxDiscountAmount > 0),
    CONSTRAINT CK_Vouchers_Dates        CHECK (EndDate > StartDate),
    CONSTRAINT CK_Vouchers_TotalLimit   CHECK (TotalUsageLimit > 0),
    CONSTRAINT CK_Vouchers_UsedCount    CHECK (UsedCount >= 0 AND UsedCount <= TotalUsageLimit),
    CONSTRAINT CK_Vouchers_PerCustomer  CHECK (PerCustomerLimit > 0),
    CONSTRAINT CK_Vouchers_Status       CHECK (Status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT CK_Vouchers_SoftDelete CHECK
        ((IsDeleted = 0 AND DeletedAt IS NULL) OR (IsDeleted = 1 AND DeletedAt IS NOT NULL AND Status = 'INACTIVE'))
);
GO

/* ---------------------------------------------------------------------
   Table: dbo.CustomerVouchers (Ví voucher khách hàng)
   UC-15: Lưu, xem và sử dụng voucher cá nhân
   --------------------------------------------------------------------- */
CREATE TABLE dbo.CustomerVouchers (
    Id          BIGINT IDENTITY(1,1) NOT NULL,
    CustomerId  BIGINT        NOT NULL,
    VoucherId   BIGINT        NOT NULL,
    SavedAt     DATETIME2(0)  NOT NULL CONSTRAINT DF_CV_SavedAt DEFAULT SYSDATETIME(),
    IsUsed      BIT           NOT NULL CONSTRAINT DF_CV_IsUsed DEFAULT 0,
    UsedAt      DATETIME2(0)  NULL,
    CONSTRAINT PK_CustomerVouchers PRIMARY KEY (Id),
    CONSTRAINT FK_CV_User    FOREIGN KEY (CustomerId) REFERENCES dbo.Users(Id),
    CONSTRAINT FK_CV_Voucher FOREIGN KEY (VoucherId)  REFERENCES dbo.Vouchers(Id),
    CONSTRAINT UQ_CV_Customer_Voucher UNIQUE (CustomerId, VoucherId),
    CONSTRAINT CK_CV_UsedLogic CHECK (
        (IsUsed = 0 AND UsedAt IS NULL) OR
        (IsUsed = 1 AND UsedAt IS NOT NULL)
    )
);

CREATE INDEX IX_CustomerVouchers_Customer_IsUsed 
    ON dbo.CustomerVouchers(CustomerId, IsUsed) 
    INCLUDE (VoucherId, SavedAt, UsedAt);
GO

/* =====================================================================
   MODULE 5 — ĐƠN HÀNG (3 bảng)
   ===================================================================== */

CREATE TABLE dbo.Orders (
    Id                  BIGINT IDENTITY(1,1) NOT NULL,
    OrderCode           VARCHAR(40)    NOT NULL,
    CustomerId          BIGINT         NOT NULL,
    VoucherId           BIGINT         NULL,
    VoucherCodeSnapshot NVARCHAR(50)   NULL,
    -- Snapshot địa chỉ giao hàng
    RecipientName       NVARCHAR(150)  NOT NULL,
    RecipientPhone      VARCHAR(20)    NOT NULL,
    ShippingProvince    NVARCHAR(100)  NOT NULL,
    ShippingDistrict    NVARCHAR(100)  NOT NULL,
    ShippingWard        NVARCHAR(100)  NOT NULL,
    ShippingAddressLine NVARCHAR(300)  NOT NULL,
    CustomerNote        NVARCHAR(1000) NULL,
    -- Tiền
    SubtotalAmount      DECIMAL(18,2)  NOT NULL,
    DiscountAmount      DECIMAL(18,2)  NOT NULL CONSTRAINT DF_Orders_Discount DEFAULT 0,
    ShippingFee         DECIMAL(18,2)  NOT NULL CONSTRAINT DF_Orders_Shipping DEFAULT 30000,
    TotalAmount         DECIMAL(18,2)  NOT NULL,
    -- Trạng thái
    OrderStatus         VARCHAR(30)    NOT NULL,
    PaymentMethod       VARCHAR(30)    NOT NULL,
    PaymentStatus       VARCHAR(30)    NOT NULL CONSTRAINT DF_Orders_PayStatus DEFAULT 'UNPAID',
    -- Hủy / hoàn thành
    CancelType          VARCHAR(30)    NULL,
    CancelReason        NVARCHAR(1000) NULL,
    CancelledByUserId   BIGINT         NULL,          -- NULL nếu hệ thống tự hủy (VNPay hết hạn)
    CancelledAt         DATETIME2(0)   NULL,
    CompletedAt         DATETIME2(0)   NULL,
    CreatedAt           DATETIME2(0)   NOT NULL CONSTRAINT DF_Orders_CreatedAt DEFAULT SYSDATETIME(),
    UpdatedAt           DATETIME2(0)   NOT NULL CONSTRAINT DF_Orders_UpdatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT PK_Orders PRIMARY KEY (Id),
    CONSTRAINT UQ_Orders_Code UNIQUE (OrderCode),
    CONSTRAINT FK_Orders_Customer  FOREIGN KEY (CustomerId)        REFERENCES dbo.Users(Id),
    CONSTRAINT FK_Orders_Voucher   FOREIGN KEY (VoucherId)         REFERENCES dbo.Vouchers(Id),
    CONSTRAINT FK_Orders_CancelBy  FOREIGN KEY (CancelledByUserId) REFERENCES dbo.Users(Id),
    -- Tiền
    CONSTRAINT CK_Orders_CompletedPaid CHECK (OrderStatus <> 'COMPLETED' OR PaymentStatus = 'PAID'),
    CONSTRAINT CK_Orders_Amounts  CHECK (SubtotalAmount >= 0 AND DiscountAmount >= 0 AND ShippingFee >= 0 AND TotalAmount >= 0),
    CONSTRAINT CK_Orders_Discount CHECK (DiscountAmount <= SubtotalAmount),
    CONSTRAINT CK_Orders_Total    CHECK (TotalAmount = SubtotalAmount - DiscountAmount + ShippingFee),
    CONSTRAINT CK_Orders_Voucher  CHECK (VoucherId IS NOT NULL OR (VoucherCodeSnapshot IS NULL AND DiscountAmount = 0)),
    -- Giá trị hợp lệ
    CONSTRAINT CK_Orders_OrderStatus CHECK (OrderStatus IN
        ('PENDING_CONFIRMATION','PENDING_PAYMENT','CONFIRMED','PREPARING','SHIPPING','COMPLETED','CANCELLED')),
    CONSTRAINT CK_Orders_PayMethod   CHECK (PaymentMethod IN ('COD','VNPAY')),
    CONSTRAINT CK_Orders_PayStatus   CHECK (PaymentStatus IN ('UNPAID','PAID','FAILED','CANCELLED')),
    CONSTRAINT CK_Orders_CancelType  CHECK (CancelType IS NULL OR CancelType IN
        ('CUSTOMER_CANCEL','STAFF_REJECT','PAYMENT_FAILED','PAYMENT_CANCELLED','PAYMENT_EXPIRED')),
    -- Ràng buộc với máy trạng thái
    CONSTRAINT CK_Orders_PendingConfirm CHECK (OrderStatus <> 'PENDING_CONFIRMATION' OR PaymentMethod = 'COD'),
    CONSTRAINT CK_Orders_PendingPayment CHECK (OrderStatus <> 'PENDING_PAYMENT' OR PaymentMethod = 'VNPAY'),
    CONSTRAINT CK_Orders_CodPaid        CHECK (NOT (PaymentMethod = 'COD' AND PaymentStatus = 'PAID' AND OrderStatus <> 'COMPLETED')),
    CONSTRAINT CK_Orders_CancelFields   CHECK (
        (OrderStatus = 'CANCELLED' AND CancelType IS NOT NULL AND CancelledAt IS NOT NULL)
        OR (OrderStatus <> 'CANCELLED' AND CancelType IS NULL AND CancelledAt IS NULL AND CancelReason IS NULL)),
    CONSTRAINT CK_Orders_RejectReason   CHECK (CancelType <> 'STAFF_REJECT' OR CancelReason IS NOT NULL),
    CONSTRAINT CK_Orders_CompletedAt    CHECK (
        (OrderStatus = 'COMPLETED' AND CompletedAt IS NOT NULL)
        OR (OrderStatus <> 'COMPLETED' AND CompletedAt IS NULL))
);
CREATE INDEX IX_Orders_Customer ON dbo.Orders(CustomerId, CreatedAt DESC);
CREATE INDEX IX_Orders_Status   ON dbo.Orders(OrderStatus, CreatedAt DESC);
CREATE INDEX IX_Orders_CompletedAt ON dbo.Orders(CompletedAt)
    INCLUDE (SubtotalAmount, DiscountAmount, ShippingFee, TotalAmount) WHERE OrderStatus = 'COMPLETED';
CREATE INDEX IX_Orders_Voucher  ON dbo.Orders(VoucherId, CustomerId) WHERE VoucherId IS NOT NULL;
GO

CREATE TABLE dbo.OrderItems (
    Id                  BIGINT IDENTITY(1,1) NOT NULL,
    OrderId             BIGINT        NOT NULL,
    VariantId           BIGINT        NOT NULL,
    ProductNameSnapshot NVARCHAR(250) NOT NULL,
    SkuCodeSnapshot     VARCHAR(80)   NOT NULL,
    VariantNameSnapshot NVARCHAR(200) NOT NULL,
    UnitPrice           DECIMAL(18,2) NOT NULL,
    Quantity            INT           NOT NULL,
    LineTotal AS CAST(UnitPrice * Quantity AS DECIMAL(18,2)) PERSISTED,
    CONSTRAINT PK_OrderItems PRIMARY KEY (Id),
    CONSTRAINT FK_OrderItems_Orders   FOREIGN KEY (OrderId)   REFERENCES dbo.Orders(Id),
    CONSTRAINT FK_OrderItems_Variants FOREIGN KEY (VariantId) REFERENCES dbo.ProductVariants(Id),
    CONSTRAINT UQ_OrderItems_OrderVariant UNIQUE (OrderId, VariantId),
    CONSTRAINT CK_OrderItems_Price CHECK (UnitPrice >= 0),
    CONSTRAINT CK_OrderItems_Qty   CHECK (Quantity > 0)
);
CREATE INDEX IX_OrderItems_Variant ON dbo.OrderItems(VariantId);
GO

CREATE TABLE dbo.OrderStatusHistories (
    Id              BIGINT IDENTITY(1,1) NOT NULL,
    OrderId         BIGINT         NOT NULL,
    OldStatus       VARCHAR(30)    NULL,             -- NULL khi tạo đơn
    NewStatus       VARCHAR(30)    NOT NULL,
    Reason          NVARCHAR(1000) NULL,
    ChangedByUserId BIGINT         NULL,             -- NULL nếu hệ thống tự đổi
    ChangedAt       DATETIME2(0)   NOT NULL CONSTRAINT DF_OSH_ChangedAt DEFAULT SYSDATETIME(),
    CONSTRAINT PK_OrderStatusHistories PRIMARY KEY (Id),
    CONSTRAINT FK_OSH_Orders FOREIGN KEY (OrderId)         REFERENCES dbo.Orders(Id),
    CONSTRAINT FK_OSH_Users  FOREIGN KEY (ChangedByUserId) REFERENCES dbo.Users(Id),
    CONSTRAINT CK_OSH_NewStatus CHECK (NewStatus IN
        ('PENDING_CONFIRMATION','PENDING_PAYMENT','CONFIRMED','PREPARING','SHIPPING','COMPLETED','CANCELLED')),
    CONSTRAINT CK_OSH_OldStatus CHECK (OldStatus IS NULL OR OldStatus IN
        ('PENDING_CONFIRMATION','PENDING_PAYMENT','CONFIRMED','PREPARING','SHIPPING','COMPLETED','CANCELLED'))
);
CREATE INDEX IX_OSH_Order ON dbo.OrderStatusHistories(OrderId, ChangedAt);
GO

/* =====================================================================
   MODULE 6 — NHẬP KHO (2 bảng)  — thay cho InventoryTransactions
   Xuất kho = Orders/OrderItems. Nhập kho = StockImports/StockImportItems.
   ===================================================================== */

CREATE TABLE dbo.StockImports (
    Id              BIGINT IDENTITY(1,1) NOT NULL,
    ImportCode      VARCHAR(40)    NOT NULL,          -- VD: IMP-20260928-001
    SupplierName    NVARCHAR(200)  NULL,              -- nhà cung cấp (text, không tách bảng)
    Note            NVARCHAR(1000) NULL,
    ImportedAt      DATETIME2(0)   NOT NULL CONSTRAINT DF_StockImports_ImportedAt DEFAULT SYSDATETIME(),
    CreatedByUserId BIGINT         NOT NULL,
    CreatedAt       DATETIME2(0)   NOT NULL CONSTRAINT DF_StockImports_CreatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT PK_StockImports PRIMARY KEY (Id),
    CONSTRAINT UQ_StockImports_Code UNIQUE (ImportCode),
    CONSTRAINT FK_StockImports_Users FOREIGN KEY (CreatedByUserId) REFERENCES dbo.Users(Id)
);
CREATE INDEX IX_StockImports_Date ON dbo.StockImports(ImportedAt DESC);
GO

CREATE TABLE dbo.StockImportItems (
    Id        BIGINT IDENTITY(1,1) NOT NULL,
    ImportId  BIGINT        NOT NULL,
    VariantId BIGINT        NOT NULL,
    Quantity  INT           NOT NULL,
    UnitCost  DECIMAL(18,2) NULL,                     -- giá nhập (không bắt buộc)
    CONSTRAINT PK_StockImportItems PRIMARY KEY (Id),
    CONSTRAINT FK_SII_Imports  FOREIGN KEY (ImportId)  REFERENCES dbo.StockImports(Id),
    CONSTRAINT FK_SII_Variants FOREIGN KEY (VariantId) REFERENCES dbo.ProductVariants(Id),
    CONSTRAINT UQ_SII_ImportVariant UNIQUE (ImportId, VariantId),
    CONSTRAINT CK_SII_Qty  CHECK (Quantity > 0),
    CONSTRAINT CK_SII_Cost CHECK (UnitCost IS NULL OR UnitCost >= 0)
);
CREATE INDEX IX_SII_Variant ON dbo.StockImportItems(VariantId);
GO

/* =====================================================================
   MODULE 7 — THANH TOÁN (1 bảng)
   ===================================================================== */

CREATE TABLE dbo.PaymentTransactions (
    Id                    BIGINT IDENTITY(1,1) NOT NULL,
    TransactionCode       VARCHAR(80)    NOT NULL,
    OrderId               BIGINT         NOT NULL,
    CustomerId            BIGINT         NOT NULL,
    PaymentMethod         VARCHAR(30)    NOT NULL,
    PaymentEnvironment    VARCHAR(20)    NOT NULL CONSTRAINT DF_Pay_Env DEFAULT 'SANDBOX',
    Amount                DECIMAL(18,2)  NOT NULL,
    TransactionStatus     VARCHAR(20)    NOT NULL CONSTRAINT DF_Pay_Status DEFAULT 'PENDING',
    ProviderOrderReference VARCHAR(100)  NULL,        -- vnp_TxnRef
    ProviderTransactionId  VARCHAR(100)  NULL,        -- vnp_TransactionNo
    ResponseCode          VARCHAR(20)    NULL,
    BankCode              VARCHAR(30)    NULL,
    CardType              VARCHAR(30)    NULL,
    SignatureVerified     BIT            NULL,        -- HMAC-SHA512 hợp lệ?
    FailureReason         NVARCHAR(1000) NULL,
    ExpiresAt             DATETIME2(0)   NULL,        -- hạn thanh toán (job quét 15 phút)
    PaidAt                DATETIME2(0)   NULL,
    CallbackReceivedAt    DATETIME2(0)   NULL,
    CreatedAt             DATETIME2(0)   NOT NULL CONSTRAINT DF_Pay_CreatedAt DEFAULT SYSDATETIME(),
    UpdatedAt             DATETIME2(0)   NOT NULL CONSTRAINT DF_Pay_UpdatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT PK_PaymentTransactions PRIMARY KEY (Id),
    CONSTRAINT UQ_Pay_Code UNIQUE (TransactionCode),
    CONSTRAINT FK_Pay_Orders FOREIGN KEY (OrderId)    REFERENCES dbo.Orders(Id),
    CONSTRAINT FK_Pay_Users  FOREIGN KEY (CustomerId) REFERENCES dbo.Users(Id),
    CONSTRAINT CK_Pay_Method CHECK (PaymentMethod IN ('COD','VNPAY')),
    CONSTRAINT CK_Pay_Env    CHECK (PaymentEnvironment IN ('SANDBOX','INTERNAL')),
    CONSTRAINT CK_Pay_MethodEnv CHECK (
        (PaymentMethod = 'COD' AND PaymentEnvironment = 'INTERNAL')
        OR (PaymentMethod = 'VNPAY' AND PaymentEnvironment = 'SANDBOX')),
    CONSTRAINT CK_Pay_Amount CHECK (Amount >= 0),
    CONSTRAINT CK_Pay_Status CHECK (TransactionStatus IN ('PENDING','SUCCESS','FAILED','CANCELLED','EXPIRED')),
    CONSTRAINT CK_Pay_PaidAt CHECK (TransactionStatus <> 'SUCCESS' OR PaidAt IS NOT NULL)
);
CREATE UNIQUE INDEX UX_Pay_ProviderOrderRef ON dbo.PaymentTransactions(ProviderOrderReference)
    WHERE ProviderOrderReference IS NOT NULL;
CREATE UNIQUE INDEX UX_Pay_ProviderTxnId ON dbo.PaymentTransactions(ProviderTransactionId)
    WHERE ProviderTransactionId IS NOT NULL;
CREATE INDEX IX_Pay_Order ON dbo.PaymentTransactions(OrderId);
-- Hỗ trợ job quét giao dịch VNPay quá hạn
CREATE INDEX IX_Pay_PendingExpiry ON dbo.PaymentTransactions(ExpiresAt)
    WHERE TransactionStatus = 'PENDING' AND ExpiresAt IS NOT NULL;
GO

/* =====================================================================
   MODULE 8 — ĐÁNH GIÁ (1 bảng)
   ===================================================================== */

CREATE TABLE dbo.Reviews (
    Id          BIGINT IDENTITY(1,1) NOT NULL,
    OrderItemId BIGINT         NOT NULL,              -- verified purchase, 1 item = 1 đánh giá
    CustomerId  BIGINT         NOT NULL,
    Rating      INT            NOT NULL,
    Comment     NVARCHAR(1000) NULL,
    Status      VARCHAR(20)    NOT NULL CONSTRAINT DF_Reviews_Status DEFAULT 'VISIBLE',
    CreatedAt   DATETIME2(0)   NOT NULL CONSTRAINT DF_Reviews_CreatedAt DEFAULT SYSDATETIME(),
    UpdatedAt   DATETIME2(0)   NOT NULL CONSTRAINT DF_Reviews_UpdatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT PK_Reviews PRIMARY KEY (Id),
    CONSTRAINT UQ_Reviews_OrderItem UNIQUE (OrderItemId),
    CONSTRAINT FK_Reviews_OrderItems FOREIGN KEY (OrderItemId) REFERENCES dbo.OrderItems(Id),
    CONSTRAINT FK_Reviews_Users      FOREIGN KEY (CustomerId)  REFERENCES dbo.Users(Id),
    CONSTRAINT CK_Reviews_Rating CHECK (Rating BETWEEN 1 AND 5),
    CONSTRAINT CK_Reviews_Status CHECK (Status IN ('VISIBLE','HIDDEN'))
);
CREATE INDEX IX_Reviews_Customer ON dbo.Reviews(CustomerId);
CREATE INDEX IX_Reviews_Status   ON dbo.Reviews(Status);
GO

/* =====================================================================
   VIEW TIỆN ÍCH (giúp code Java query nhanh, không bắt buộc dùng)
   ===================================================================== */

-- Trang danh sách sản phẩm: giá thấp nhất, tồn kho, ảnh chính, điểm đánh giá
CREATE VIEW dbo.vw_ProductSummary AS
SELECT  p.Id AS ProductId, p.Name, p.Slug, p.CategoryId, p.BrandId, p.PetType,
        p.Status, p.IsFeatured, p.CreatedAt,
        v.MinPrice, v.MaxPrice, ISNULL(v.TotalStock, 0) AS TotalStock,
        img.ImageUrl AS PrimaryImageUrl,
        ISNULL(r.AvgRating, 0) AS AvgRating, ISNULL(r.ReviewCount, 0) AS ReviewCount
FROM dbo.Products p
OUTER APPLY (SELECT MIN(Price) AS MinPrice, MAX(Price) AS MaxPrice, SUM(StockQuantity) AS TotalStock
             FROM dbo.ProductVariants WHERE ProductId = p.Id AND Status = 'ACTIVE') v
OUTER APPLY (SELECT TOP 1 ImageUrl FROM dbo.ProductImages
             WHERE ProductId = p.Id AND IsPrimary = 1) img
OUTER APPLY (SELECT CAST(AVG(CAST(rv.Rating AS DECIMAL(5,2))) AS DECIMAL(3,2)) AS AvgRating,
                    COUNT(*) AS ReviewCount
             FROM dbo.Reviews rv
             JOIN dbo.OrderItems oi ON oi.Id = rv.OrderItemId
             JOIN dbo.ProductVariants pv ON pv.Id = oi.VariantId
             WHERE pv.ProductId = p.Id AND rv.Status = 'VISIBLE') r;
GO

-- Cảnh báo sắp hết hàng cho Staff
CREATE VIEW dbo.vw_LowStockVariants AS
SELECT  v.Id AS VariantId, v.SkuCode, v.VariantName, p.Id AS ProductId, p.Name AS ProductName,
        v.StockQuantity, v.LowStockThreshold
FROM dbo.ProductVariants v
JOIN dbo.Products p ON p.Id = v.ProductId
WHERE v.Status = 'ACTIVE' AND p.Status <> 'DISCONTINUED'
  AND v.StockQuantity <= v.LowStockThreshold;
GO

-- Đối chiếu tồn kho: Difference phải luôn = 0
CREATE VIEW dbo.vw_StockReconciliation AS
SELECT  v.Id AS VariantId, v.SkuCode, v.StockQuantity,
        ISNULL(i.Imported, 0) AS TotalImported,
        ISNULL(s.Sold, 0)     AS TotalSold,
        ISNULL(i.Imported, 0) - ISNULL(s.Sold, 0) AS ExpectedStock,
        v.StockQuantity - (ISNULL(i.Imported, 0) - ISNULL(s.Sold, 0)) AS Difference
FROM dbo.ProductVariants v
OUTER APPLY (SELECT SUM(Quantity) AS Imported FROM dbo.StockImportItems WHERE VariantId = v.Id) i
OUTER APPLY (SELECT SUM(oi.Quantity) AS Sold
             FROM dbo.OrderItems oi JOIN dbo.Orders o ON o.Id = oi.OrderId
             WHERE oi.VariantId = v.Id AND o.OrderStatus <> 'CANCELLED') s;
GO

-- Báo cáo doanh thu theo ngày (chỉ đơn COMPLETED)
CREATE VIEW dbo.vw_RevenueByDay AS
SELECT  CAST(CompletedAt AS DATE) AS RevenueDate,
        COUNT(*)                  AS OrderCount,
        SUM(SubtotalAmount)       AS GoodsRevenue,
        SUM(DiscountAmount)       AS TotalDiscount,
        SUM(SubtotalAmount - DiscountAmount) AS TotalRevenue,
        SUM(ShippingFee) AS TotalShipping, SUM(TotalAmount) AS TotalCollected
FROM dbo.Orders
WHERE OrderStatus = 'COMPLETED'
GROUP BY CAST(CompletedAt AS DATE);
GO

/* =====================================================================
   SEED DATA
   ===================================================================== */

INSERT INTO dbo.Roles (Code, Name) VALUES
 ('CUSTOMER', N'Khách hàng'), ('STAFF', N'Nhân viên'), ('ADMIN', N'Quản trị viên');

INSERT INTO dbo.Users (RoleId, Email, Phone, PasswordHash, FullName)
SELECT r.Id, x.Email, x.Phone, x.PasswordHash, x.FullName
FROM (VALUES
 ('ADMIN',    N'admin@petshop.vn',    '0900000001', N'$2a$10$Kom3YM9lNCLpqjr35R0y9.qA4KrFIcvNqLkcs5o.mGWrbOT71oeVm', N'Quản Trị Viên'),
 ('STAFF',    N'staff@petshop.vn',    '0900000002', N'$2a$10$VPMBpnEaWXEpvQoTby9XW.dR0YFLHwRimA4ly.3FjU2ieMI691OSa', N'Nhân Viên Kho'),
 ('CUSTOMER', N'customer@petshop.vn', '0900000003', N'$2a$10$wjOqbAN51vbM5Ryd2.pjyeMHgsuVFvnN4T0vfKvLuv.8d9.Tc1sC.', N'Nguyễn Văn Khách')
) AS x(RoleCode, Email, Phone, PasswordHash, FullName)
JOIN dbo.Roles r ON r.Code = x.RoleCode;

INSERT INTO dbo.Carts (UserId) SELECT Id FROM dbo.Users WHERE Email = N'customer@petshop.vn';

INSERT INTO dbo.UserAddresses (CustomerId, RecipientName, RecipientPhone, Province, District, Ward, AddressLine, IsDefault)
SELECT Id, N'Nguyễn Văn Khách', '0900000003', N'TP. Hồ Chí Minh', N'Thủ Đức', N'Linh Trung', N'Khu phố 6, Đại học FPT', 1
FROM dbo.Users WHERE Email = N'customer@petshop.vn';

-- Danh mục gốc
INSERT INTO dbo.Categories (Code, Name, SortOrder) VALUES
 ('THUC-AN',     N'Thức ăn',       1),
 ('CAT-VE-SINH', N'Cát vệ sinh',   2),
 ('DO-CHOI',     N'Đồ chơi',       3),
 ('PHU-KIEN',    N'Phụ kiện',      4),
 ('CHAM-SOC',    N'Chăm sóc',      5),
 ('CHUONG-NEM',  N'Chuồng & nệm',  6),
 ('Y-TE',        N'Y tế',          7);

-- Danh mục con (mẫu)
INSERT INTO dbo.Categories (ParentCategoryId, Code, Name, SortOrder)
SELECT p.Id, x.Code, x.Name, x.SortOrder
FROM (VALUES
 ('THUC-AN',  'THUC-AN-HAT', N'Thức ăn hạt',        1),
 ('THUC-AN',  'THUC-AN-UOT', N'Pate & thức ăn ướt', 2),
 ('DO-CHOI',  'DO-CHOI-NHAI',N'Đồ chơi gặm',        1),
 ('PHU-KIEN', 'PK-VONG-CO',  N'Vòng cổ & dây dắt',  1)
) AS x(ParentCode, Code, Name, SortOrder)
JOIN dbo.Categories p ON p.Code = x.ParentCode;

INSERT INTO dbo.Brands (Name) VALUES
 (N'Royal Canin'), (N'Whiskas'), (N'Me-O'), (N'Pedigree'), (N'SmartHeart');

-- Sản phẩm mẫu
INSERT INTO dbo.Products (CategoryId, BrandId, PetType, Name, Slug, ShortDescription, IsFeatured, CreatedByUserId)
SELECT c.Id, b.Id, x.PetType, x.Name, x.Slug, x.ShortDesc, x.IsFeatured, u.Id
FROM (VALUES
 ('THUC-AN-HAT',  N'Royal Canin', 'DOG', N'Hạt Royal Canin Poodle Adult',      'hat-royal-canin-poodle-adult',  N'Thức ăn hạt dành cho chó Poodle trưởng thành', 1),
 ('THUC-AN-UOT',  N'Whiskas',     'CAT', N'Pate Whiskas vị cá thu',            'pate-whiskas-ca-thu',           N'Pate dạng túi cho mèo trưởng thành',           1),
 ('CAT-VE-SINH',  N'Me-O',        'CAT', N'Cát vệ sinh Me-O hương chanh',      'cat-ve-sinh-me-o-huong-chanh',  N'Cát vón cục, khử mùi tốt',                     0),
 ('DO-CHOI-NHAI', NULL,           'DOG', N'Bóng cao su gặm cho chó',           'bong-cao-su-gam-cho-cho',       N'Cao su tự nhiên, an toàn cho răng',            0),
 ('PK-VONG-CO',   NULL,           'BOTH',N'Bộ vòng cổ và dây dắt',             'bo-vong-co-day-dat',            N'Chất liệu dù bền, nhiều size',                 1)
) AS x(CatCode, BrandName, PetType, Name, Slug, ShortDesc, IsFeatured)
JOIN dbo.Categories c ON c.Code = x.CatCode
LEFT JOIN dbo.Brands b ON b.Name = x.BrandName
CROSS JOIN (SELECT TOP 1 Id FROM dbo.Users WHERE Email = N'admin@petshop.vn') u;

INSERT INTO dbo.ProductImages (ProductId, ImageUrl, AltText, SortOrder, IsPrimary)
SELECT Id, N'/images/products/' + Slug + N'.jpg', Name, 0, 1 FROM dbo.Products;

-- Biến thể (tồn kho = 0, sẽ được cộng bởi phiếu nhập mẫu bên dưới)
INSERT INTO dbo.ProductVariants (ProductId, SkuCode, VariantName, Price, OriginalPrice, LowStockThreshold)
SELECT p.Id, x.Sku, x.VName, x.Price, x.OrigPrice, 5
FROM (VALUES
 ('hat-royal-canin-poodle-adult', 'RC-POODLE-500G',  N'Túi 500g',       165000, NULL),
 ('hat-royal-canin-poodle-adult', 'RC-POODLE-1500G', N'Túi 1.5kg',      420000, 450000),
 ('pate-whiskas-ca-thu',          'WHK-MACKEREL-85G',N'Túi 85g',         18000, NULL),
 ('pate-whiskas-ca-thu',          'WHK-MACKEREL-24P',N'Thùng 24 túi',   410000, 432000),
 ('cat-ve-sinh-me-o-huong-chanh', 'MEO-LITTER-8L',   N'Túi 8 lít',       95000, NULL),
 ('bong-cao-su-gam-cho-cho',      'BALL-RUBBER-S',   N'Size S',          35000, NULL),
 ('bong-cao-su-gam-cho-cho',      'BALL-RUBBER-M',   N'Size M',          55000, NULL),
 ('bo-vong-co-day-dat',           'LEASH-SET-S',     N'Size S',          85000, NULL),
 ('bo-vong-co-day-dat',           'LEASH-SET-M',     N'Size M',          99000, NULL)
) AS x(Slug, Sku, VName, Price, OrigPrice)
JOIN dbo.Products p ON p.Slug = x.Slug;

-- Voucher mẫu (hiệu lực 1 năm kể từ lúc chạy script)
INSERT INTO dbo.Vouchers (Code, Title, Description, DiscountType, DiscountValue, MinOrderAmount, MaxDiscountAmount,
                          StartDate, EndDate, TotalUsageLimit, PerCustomerLimit) VALUES
 ('PETWELCOME10', N'Chào mừng thành viên mới', N'Giảm 10% tối đa 50.000đ',  'PERCENT', 10,     200000,  50000,
   SYSDATETIME(), DATEADD(YEAR, 1, SYSDATETIME()), 1000, 1),
 ('PETVIP50K',    N'Giảm 50.000đ',             N'Đơn từ 500.000đ',           'FIXED',   50000,  500000,  NULL,
   SYSDATETIME(), DATEADD(YEAR, 1, SYSDATETIME()), 500, 2),
 ('MEGASAVE100K', N'Siêu tiết kiệm 100.000đ',  N'Đơn từ 1.000.000đ',         'FIXED',   100000, 1000000, NULL,
   SYSDATETIME(), DATEADD(YEAR, 1, SYSDATETIME()), 200, 1),
 ('FREESHIP30K',  N'Miễn phí vận chuyển',      N'Giảm 30.000đ phí ship, đơn từ 300.000đ', 'FIXED', 30000, 300000, NULL,
   SYSDATETIME(), DATEADD(YEAR, 1, SYSDATETIME()), 1000, 3);

-- Ví voucher mẫu cho khách hàng (customer@petshop.vn)
INSERT INTO dbo.CustomerVouchers (CustomerId, VoucherId, SavedAt, IsUsed, UsedAt)
SELECT u.Id, v.Id, DATEADD(DAY, -2, SYSDATETIME()), 0, NULL
FROM dbo.Users u
CROSS JOIN dbo.Vouchers v
WHERE u.Email = N'customer@petshop.vn' AND v.Code = 'PETWELCOME10';

INSERT INTO dbo.CustomerVouchers (CustomerId, VoucherId, SavedAt, IsUsed, UsedAt)
SELECT u.Id, v.Id, DATEADD(DAY, -1, SYSDATETIME()), 0, NULL
FROM dbo.Users u
CROSS JOIN dbo.Vouchers v
WHERE u.Email = N'customer@petshop.vn' AND v.Code = 'FREESHIP30K';

-- Phiếu nhập kho ban đầu (vừa là dữ liệu mẫu, vừa nạp tồn kho)
INSERT INTO dbo.StockImports (ImportCode, SupplierName, Note, CreatedByUserId)
SELECT 'IMP-INIT-001', N'Nhà phân phối mẫu', N'Nhập kho ban đầu', Id
FROM dbo.Users WHERE Email = N'staff@petshop.vn';

INSERT INTO dbo.StockImportItems (ImportId, VariantId, Quantity, UnitCost)
SELECT i.Id, v.Id, x.Qty, x.Cost
FROM (VALUES
 ('RC-POODLE-500G',  50, 120000), ('RC-POODLE-1500G', 30, 320000),
 ('WHK-MACKEREL-85G',200, 12000), ('WHK-MACKEREL-24P',20, 300000),
 ('MEO-LITTER-8L',   40, 65000),  ('BALL-RUBBER-S',   60, 20000),
 ('BALL-RUBBER-M',   60, 35000),  ('LEASH-SET-S',     25, 55000),
 ('LEASH-SET-M',     25, 65000)
) AS x(Sku, Qty, Cost)
JOIN dbo.ProductVariants v ON v.SkuCode = x.Sku
CROSS JOIN (SELECT Id FROM dbo.StockImports WHERE ImportCode = 'IMP-INIT-001') i;

-- Đồng bộ tồn kho từ phiếu nhập
UPDATE v SET v.StockQuantity = s.Total
FROM dbo.ProductVariants v
JOIN (SELECT VariantId, SUM(Quantity) AS Total FROM dbo.StockImportItems GROUP BY VariantId) s
  ON s.VariantId = v.Id;
GO

-- Kiểm tra nhanh sau khi chạy: mọi dòng Difference phải = 0
-- SELECT * FROM dbo.vw_StockReconciliation;
-- SELECT COUNT(*) AS TableCount FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE';  -- = 21
PRINT N'PetShopDB đã được tạo xong: 21 bảng, 4 view.';
GO
