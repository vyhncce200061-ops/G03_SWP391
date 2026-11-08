-- Optional demo catalog. Run after the required schema migrations.
-- No DDL, no password changes. Existing prices, stock and order snapshots are preserved.
-- New prices, opening quantities and unit costs are DEMO values, not supplier invoices.
USE [PetShopDB];
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @Admin BIGINT=(SELECT TOP 1 u.Id FROM dbo.Users u JOIN dbo.Roles r ON r.Id=u.RoleId WHERE r.Code='ADMIN' AND u.Status='ACTIVE' ORDER BY u.Id);
DECLARE @Staff BIGINT=(SELECT TOP 1 u.Id FROM dbo.Users u JOIN dbo.Roles r ON r.Id=u.RoleId WHERE r.Code IN('STAFF','ADMIN') AND u.Status='ACTIVE' ORDER BY CASE WHEN r.Code='STAFF' THEN 0 ELSE 1 END,u.Id);
IF @Admin IS NULL OR @Staff IS NULL THROW 50001,N'Cần tài khoản Admin và người nhập kho đang hoạt động.',1;

UPDATE dbo.[Categories] SET [Name]=N'Đồ chơi' WHERE [Code]=N'DO-CHOI' AND [Name] COLLATE Latin1_General_100_BIN2=N'Äá»“ chÆ¡i' COLLATE Latin1_General_100_BIN2;
UPDATE dbo.[Categories] SET [Name]=N'Đồ chơi gặm' WHERE [Code]=N'DO-CHOI-NHAI' AND [Name] COLLATE Latin1_General_100_BIN2=N'Äá»“ chÆ¡i gáº·m' COLLATE Latin1_General_100_BIN2;
UPDATE dbo.[Products] SET [ShortDescription]=N'Chất liệu dù bền, nhiều size' WHERE [Slug]=N'bo-vong-co-day-dat' AND [ShortDescription] COLLATE Latin1_General_100_BIN2=N'Cháº¥t liá»‡u dÃ¹ bá»n, nhiá»u size' COLLATE Latin1_General_100_BIN2;

-- Retire only the original unverified demo identities; never remove historical references.

UPDATE dbo.Products SET Status='DISCONTINUED',IsFeatured=0,UpdatedAt=SYSDATETIME() WHERE Slug=N'cat-ve-sinh-me-o-huong-chanh' AND Name=N'Cát vệ sinh Me-O hương chanh';
DELETE pi FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'cat-ve-sinh-me-o-huong-chanh' AND p.Status='DISCONTINUED' AND pi.ImageUrl=N'/images/products/cat-ve-sinh-me-o-huong-chanh.jpg';
UPDATE dbo.Products SET Status='DISCONTINUED',IsFeatured=0,UpdatedAt=SYSDATETIME() WHERE Slug=N'bong-cao-su-gam-cho-cho' AND Name=N'Bóng cao su gặm cho chó';
DELETE pi FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'bong-cao-su-gam-cho-cho' AND p.Status='DISCONTINUED' AND pi.ImageUrl=N'/images/products/bong-cao-su-gam-cho-cho.jpg';
UPDATE dbo.Products SET Status='DISCONTINUED',IsFeatured=0,UpdatedAt=SYSDATETIME() WHERE Slug=N'bo-vong-co-day-dat' AND Name=N'Bộ vòng cổ và dây dắt';
DELETE pi FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'bo-vong-co-day-dat' AND p.Status='DISCONTINUED' AND pi.ImageUrl=N'/images/products/bo-vong-co-day-dat.jpg';
DECLARE @NewVariants TABLE(Id BIGINT PRIMARY KEY,SkuCode VARCHAR(80));
IF NOT EXISTS(SELECT 1 FROM dbo.Brands WITH(UPDLOCK,HOLDLOCK) WHERE Name=N'Royal Canin') INSERT dbo.Brands(Name) VALUES(N'Royal Canin');
IF NOT EXISTS(SELECT 1 FROM dbo.Categories WHERE Code=N'THUC-AN-HAT' AND IsActive=1) THROW 50002,N'Thiếu danh mục hoạt động: THUC-AN-HAT',1;
IF EXISTS(SELECT 1 FROM dbo.Products WHERE Slug=N'royal-canin-mini-adult' AND Name COLLATE Latin1_General_100_BIN2<>N'Hạt Royal Canin Mini Adult' COLLATE Latin1_General_100_BIN2) THROW 50003,N'Slug trùng với sản phẩm khác, cần kiểm tra thủ công.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.Products WITH(UPDLOCK,HOLDLOCK) WHERE Slug=N'royal-canin-mini-adult')
INSERT dbo.Products(CategoryId,BrandId,PetType,Name,Slug,ShortDescription,Description,UsageInstructions,IsFeatured,CreatedByUserId)
SELECT c.Id,b.Id,N'DOG',N'Hạt Royal Canin Mini Adult',N'royal-canin-mini-adult',N'Thức ăn hạt cho chó trưởng thành giống nhỏ, trên 10 tháng tuổi.',N'Dành cho chó trưởng thành thuộc giống nhỏ, cân nặng tối đa 10 kg. Chọn đúng quy cách túi khi đặt hàng.',N'Dùng theo hướng dẫn trên bao bì; luôn có nước sạch. Bảo quản nơi khô ráo, đóng kín sau khi mở.',1,@Admin FROM dbo.Categories c CROSS JOIN dbo.Brands b WHERE c.Code=N'THUC-AN-HAT' AND b.Name=N'Royal Canin';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'RC-MINI-ADULT-800G' AND p.Slug<>N'royal-canin-mini-adult') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'RC-MINI-ADULT-800G')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'RC-MINI-ADULT-800G',N'Túi 800 g',195000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'royal-canin-mini-adult';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'RC-MINI-ADULT-2KG' AND p.Slug<>N'royal-canin-mini-adult') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'RC-MINI-ADULT-2KG')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'RC-MINI-ADULT-2KG',N'Túi 2 kg',425000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'royal-canin-mini-adult';
IF NOT EXISTS(SELECT 1 FROM dbo.Brands WITH(UPDLOCK,HOLDLOCK) WHERE Name=N'Royal Canin') INSERT dbo.Brands(Name) VALUES(N'Royal Canin');
IF NOT EXISTS(SELECT 1 FROM dbo.Categories WHERE Code=N'THUC-AN-HAT' AND IsActive=1) THROW 50002,N'Thiếu danh mục hoạt động: THUC-AN-HAT',1;
IF EXISTS(SELECT 1 FROM dbo.Products WHERE Slug=N'royal-canin-mini-puppy' AND Name COLLATE Latin1_General_100_BIN2<>N'Hạt Royal Canin Mini Puppy' COLLATE Latin1_General_100_BIN2) THROW 50003,N'Slug trùng với sản phẩm khác, cần kiểm tra thủ công.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.Products WITH(UPDLOCK,HOLDLOCK) WHERE Slug=N'royal-canin-mini-puppy')
INSERT dbo.Products(CategoryId,BrandId,PetType,Name,Slug,ShortDescription,Description,UsageInstructions,IsFeatured,CreatedByUserId)
SELECT c.Id,b.Id,N'DOG',N'Hạt Royal Canin Mini Puppy',N'royal-canin-mini-puppy',N'Thức ăn hạt cho chó con giống nhỏ từ 2 đến 10 tháng tuổi.',N'Dành cho chó con có cân nặng trưởng thành dưới 10 kg. Có quy cách 800 g và 2 kg.',N'Dùng theo hướng dẫn trên bao bì; luôn có nước sạch. Bảo quản nơi khô ráo, đóng kín sau khi mở.',1,@Admin FROM dbo.Categories c CROSS JOIN dbo.Brands b WHERE c.Code=N'THUC-AN-HAT' AND b.Name=N'Royal Canin';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'RC-MINI-PUPPY-800G' AND p.Slug<>N'royal-canin-mini-puppy') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'RC-MINI-PUPPY-800G')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'RC-MINI-PUPPY-800G',N'Túi 800 g',220000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'royal-canin-mini-puppy';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'RC-MINI-PUPPY-2KG' AND p.Slug<>N'royal-canin-mini-puppy') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'RC-MINI-PUPPY-2KG')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'RC-MINI-PUPPY-2KG',N'Túi 2 kg',495000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'royal-canin-mini-puppy';
IF NOT EXISTS(SELECT 1 FROM dbo.Brands WITH(UPDLOCK,HOLDLOCK) WHERE Name=N'Royal Canin') INSERT dbo.Brands(Name) VALUES(N'Royal Canin');
IF NOT EXISTS(SELECT 1 FROM dbo.Categories WHERE Code=N'THUC-AN-HAT' AND IsActive=1) THROW 50002,N'Thiếu danh mục hoạt động: THUC-AN-HAT',1;
IF EXISTS(SELECT 1 FROM dbo.Products WHERE Slug=N'royal-canin-kitten' AND Name COLLATE Latin1_General_100_BIN2<>N'Hạt Royal Canin Kitten' COLLATE Latin1_General_100_BIN2) THROW 50003,N'Slug trùng với sản phẩm khác, cần kiểm tra thủ công.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.Products WITH(UPDLOCK,HOLDLOCK) WHERE Slug=N'royal-canin-kitten')
INSERT dbo.Products(CategoryId,BrandId,PetType,Name,Slug,ShortDescription,Description,UsageInstructions,IsFeatured,CreatedByUserId)
SELECT c.Id,b.Id,N'CAT',N'Hạt Royal Canin Kitten',N'royal-canin-kitten',N'Thức ăn hạt cho mèo con trong giai đoạn tăng trưởng đến 12 tháng tuổi.',N'Royal Canin Kitten là thức ăn dạng hạt dành cho mèo con. Chọn túi 400 g hoặc 2 kg theo nhu cầu sử dụng.',N'Dùng theo hướng dẫn trên bao bì; luôn có nước sạch. Bảo quản nơi khô ráo, đóng kín sau khi mở.',1,@Admin FROM dbo.Categories c CROSS JOIN dbo.Brands b WHERE c.Code=N'THUC-AN-HAT' AND b.Name=N'Royal Canin';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'RC-KITTEN-400G' AND p.Slug<>N'royal-canin-kitten') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'RC-KITTEN-400G')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'RC-KITTEN-400G',N'Túi 400 g',145000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'royal-canin-kitten';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'RC-KITTEN-2KG' AND p.Slug<>N'royal-canin-kitten') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'RC-KITTEN-2KG')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'RC-KITTEN-2KG',N'Túi 2 kg',615000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'royal-canin-kitten';
IF NOT EXISTS(SELECT 1 FROM dbo.Brands WITH(UPDLOCK,HOLDLOCK) WHERE Name=N'Royal Canin') INSERT dbo.Brands(Name) VALUES(N'Royal Canin');
IF NOT EXISTS(SELECT 1 FROM dbo.Categories WHERE Code=N'THUC-AN-HAT' AND IsActive=1) THROW 50002,N'Thiếu danh mục hoạt động: THUC-AN-HAT',1;
IF EXISTS(SELECT 1 FROM dbo.Products WHERE Slug=N'royal-canin-indoor-27' AND Name COLLATE Latin1_General_100_BIN2<>N'Hạt Royal Canin Indoor 27' COLLATE Latin1_General_100_BIN2) THROW 50003,N'Slug trùng với sản phẩm khác, cần kiểm tra thủ công.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.Products WITH(UPDLOCK,HOLDLOCK) WHERE Slug=N'royal-canin-indoor-27')
INSERT dbo.Products(CategoryId,BrandId,PetType,Name,Slug,ShortDescription,Description,UsageInstructions,IsFeatured,CreatedByUserId)
SELECT c.Id,b.Id,N'CAT',N'Hạt Royal Canin Indoor 27',N'royal-canin-indoor-27',N'Thức ăn hạt cho mèo trưởng thành từ 1 đến 7 tuổi sống trong nhà.',N'Dành cho mèo nuôi trong nhà, ít vận động. Có quy cách túi 400 g và 2 kg.',N'Dùng theo hướng dẫn trên bao bì; luôn có nước sạch. Bảo quản nơi khô ráo, đóng kín sau khi mở.',1,@Admin FROM dbo.Categories c CROSS JOIN dbo.Brands b WHERE c.Code=N'THUC-AN-HAT' AND b.Name=N'Royal Canin';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'RC-INDOOR-400G' AND p.Slug<>N'royal-canin-indoor-27') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'RC-INDOOR-400G')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'RC-INDOOR-400G',N'Túi 400 g',135000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'royal-canin-indoor-27';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'RC-INDOOR-2KG' AND p.Slug<>N'royal-canin-indoor-27') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'RC-INDOOR-2KG')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'RC-INDOOR-2KG',N'Túi 2 kg',545000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'royal-canin-indoor-27';
IF NOT EXISTS(SELECT 1 FROM dbo.Brands WITH(UPDLOCK,HOLDLOCK) WHERE Name=N'Royal Canin') INSERT dbo.Brands(Name) VALUES(N'Royal Canin');
IF NOT EXISTS(SELECT 1 FROM dbo.Categories WHERE Code=N'THUC-AN-HAT' AND IsActive=1) THROW 50002,N'Thiếu danh mục hoạt động: THUC-AN-HAT',1;
IF EXISTS(SELECT 1 FROM dbo.Products WHERE Slug=N'royal-canin-fit-32' AND Name COLLATE Latin1_General_100_BIN2<>N'Hạt Royal Canin Fit 32' COLLATE Latin1_General_100_BIN2) THROW 50003,N'Slug trùng với sản phẩm khác, cần kiểm tra thủ công.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.Products WITH(UPDLOCK,HOLDLOCK) WHERE Slug=N'royal-canin-fit-32')
INSERT dbo.Products(CategoryId,BrandId,PetType,Name,Slug,ShortDescription,Description,UsageInstructions,IsFeatured,CreatedByUserId)
SELECT c.Id,b.Id,N'CAT',N'Hạt Royal Canin Fit 32',N'royal-canin-fit-32',N'Thức ăn hạt cho mèo trưởng thành có mức vận động vừa phải.',N'Dành cho mèo trưởng thành trên 1 tuổi, có vận động hoặc tiếp cận không gian ngoài trời. Quy cách túi 2 kg và 4 kg.',N'Dùng theo hướng dẫn trên bao bì; luôn có nước sạch. Bảo quản nơi khô ráo, đóng kín sau khi mở.',0,@Admin FROM dbo.Categories c CROSS JOIN dbo.Brands b WHERE c.Code=N'THUC-AN-HAT' AND b.Name=N'Royal Canin';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'RC-FIT-2KG' AND p.Slug<>N'royal-canin-fit-32') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'RC-FIT-2KG')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'RC-FIT-2KG',N'Túi 2 kg',445000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'royal-canin-fit-32';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'RC-FIT-4KG' AND p.Slug<>N'royal-canin-fit-32') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'RC-FIT-4KG')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'RC-FIT-4KG',N'Túi 4 kg',825000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'royal-canin-fit-32';
IF NOT EXISTS(SELECT 1 FROM dbo.Brands WITH(UPDLOCK,HOLDLOCK) WHERE Name=N'INABA') INSERT dbo.Brands(Name) VALUES(N'INABA');
IF NOT EXISTS(SELECT 1 FROM dbo.Categories WHERE Code=N'THUC-AN-UOT' AND IsActive=1) THROW 50002,N'Thiếu danh mục hoạt động: THUC-AN-UOT',1;
IF EXISTS(SELECT 1 FROM dbo.Products WHERE Slug=N'pate-inaba-ga-rau-cu-pho-mai' AND Name COLLATE Latin1_General_100_BIN2<>N'Pate INABA vị gà, rau củ và phô mai' COLLATE Latin1_General_100_BIN2) THROW 50003,N'Slug trùng với sản phẩm khác, cần kiểm tra thủ công.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.Products WITH(UPDLOCK,HOLDLOCK) WHERE Slug=N'pate-inaba-ga-rau-cu-pho-mai')
INSERT dbo.Products(CategoryId,BrandId,PetType,Name,Slug,ShortDescription,Description,UsageInstructions,IsFeatured,CreatedByUserId)
SELECT c.Id,b.Id,N'DOG',N'Pate INABA vị gà, rau củ và phô mai',N'pate-inaba-ga-rau-cu-pho-mai',N'Thức ăn ướt cho chó, hộp đôi 35 g × 2.',N'INABA Chicken, Vegetables & Cheese dạng hộp đôi, vị gà kết hợp rau củ và phô mai. Dùng theo hướng dẫn của nhà sản xuất.',N'Đọc hướng dẫn trên bao bì trước khi sử dụng.',0,@Admin FROM dbo.Categories c CROSS JOIN dbo.Brands b WHERE c.Code=N'THUC-AN-UOT' AND b.Name=N'INABA';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'INABA-CHICKEN-CHEESE-35GX2' AND p.Slug<>N'pate-inaba-ga-rau-cu-pho-mai') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'INABA-CHICKEN-CHEESE-35GX2')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'INABA-CHICKEN-CHEESE-35GX2',N'Hộp 35 g × 2',35000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'pate-inaba-ga-rau-cu-pho-mai';
IF NOT EXISTS(SELECT 1 FROM dbo.Brands WITH(UPDLOCK,HOLDLOCK) WHERE Name=N'CIAO') INSERT dbo.Brands(Name) VALUES(N'CIAO');
IF NOT EXISTS(SELECT 1 FROM dbo.Categories WHERE Code=N'THUC-AN-UOT' AND IsActive=1) THROW 50002,N'Thiếu danh mục hoạt động: THUC-AN-UOT',1;
IF EXISTS(SELECT 1 FROM dbo.Products WHERE Slug=N'ciao-ga-phi-le-ca-ngu-nuong' AND Name COLLATE Latin1_General_100_BIN2<>N'CIAO vị gà phi lê và cá ngừ nướng' COLLATE Latin1_General_100_BIN2) THROW 50003,N'Slug trùng với sản phẩm khác, cần kiểm tra thủ công.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.Products WITH(UPDLOCK,HOLDLOCK) WHERE Slug=N'ciao-ga-phi-le-ca-ngu-nuong')
INSERT dbo.Products(CategoryId,BrandId,PetType,Name,Slug,ShortDescription,Description,UsageInstructions,IsFeatured,CreatedByUserId)
SELECT c.Id,b.Id,N'CAT',N'CIAO vị gà phi lê và cá ngừ nướng',N'ciao-ga-phi-le-ca-ngu-nuong',N'Thức ăn bổ sung cho mèo, gói 10 g × 4.',N'CIAO Chicken Fillet & Grilled Bonito, vị gà phi lê và cá ngừ nướng. Quy cách đóng gói 4 túi nhỏ, mỗi túi 10 g.',N'Đọc hướng dẫn trên bao bì trước khi sử dụng.',0,@Admin FROM dbo.Categories c CROSS JOIN dbo.Brands b WHERE c.Code=N'THUC-AN-UOT' AND b.Name=N'CIAO';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'CIAO-CHICKEN-BONITO-10GX4' AND p.Slug<>N'ciao-ga-phi-le-ca-ngu-nuong') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'CIAO-CHICKEN-BONITO-10GX4')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'CIAO-CHICKEN-BONITO-10GX4',N'Gói 10 g × 4',55000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'ciao-ga-phi-le-ca-ngu-nuong';
IF NOT EXISTS(SELECT 1 FROM dbo.Brands WITH(UPDLOCK,HOLDLOCK) WHERE Name=N'MEOWOW') INSERT dbo.Brands(Name) VALUES(N'MEOWOW');
IF NOT EXISTS(SELECT 1 FROM dbo.Categories WHERE Code=N'THUC-AN-UOT' AND IsActive=1) THROW 50002,N'Thiếu danh mục hoạt động: THUC-AN-UOT',1;
IF EXISTS(SELECT 1 FROM dbo.Products WHERE Slug=N'pate-meowow-ca-ngu-thit-cua' AND Name COLLATE Latin1_General_100_BIN2<>N'Pate MEOWOW vị cá ngừ trắng và thịt cua' COLLATE Latin1_General_100_BIN2) THROW 50003,N'Slug trùng với sản phẩm khác, cần kiểm tra thủ công.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.Products WITH(UPDLOCK,HOLDLOCK) WHERE Slug=N'pate-meowow-ca-ngu-thit-cua')
INSERT dbo.Products(CategoryId,BrandId,PetType,Name,Slug,ShortDescription,Description,UsageInstructions,IsFeatured,CreatedByUserId)
SELECT c.Id,b.Id,N'CAT',N'Pate MEOWOW vị cá ngừ trắng và thịt cua',N'pate-meowow-ca-ngu-thit-cua',N'Pate lon cho mèo, vị cá ngừ trắng và thịt cua.',N'MEOWOW Tuna Kanikama dạng lon dành cho mèo. Sản phẩm kết hợp cá ngừ trắng và thịt cua, sử dụng theo thông tin trên bao bì.',N'Đọc hướng dẫn trên bao bì trước khi sử dụng.',0,@Admin FROM dbo.Categories c CROSS JOIN dbo.Brands b WHERE c.Code=N'THUC-AN-UOT' AND b.Name=N'MEOWOW';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'MEOWOW-TUNA-KANIKAMA-80G' AND p.Slug<>N'pate-meowow-ca-ngu-thit-cua') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'MEOWOW-TUNA-KANIKAMA-80G')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'MEOWOW-TUNA-KANIKAMA-80G',N'Lon 80 g',25000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'pate-meowow-ca-ngu-thit-cua';
IF NOT EXISTS(SELECT 1 FROM dbo.Brands WITH(UPDLOCK,HOLDLOCK) WHERE Name=N'BOBO') INSERT dbo.Brands(Name) VALUES(N'BOBO');
IF NOT EXISTS(SELECT 1 FROM dbo.Categories WHERE Code=N'DO-CHOI' AND IsActive=1) THROW 50002,N'Thiếu danh mục hoạt động: DO-CHOI',1;
IF EXISTS(SELECT 1 FROM dbo.Products WHERE Slug=N'do-choi-day-thung-bobo-hinh-gau' AND Name COLLATE Latin1_General_100_BIN2<>N'Đồ chơi dây thừng BOBO hình gấu' COLLATE Latin1_General_100_BIN2) THROW 50003,N'Slug trùng với sản phẩm khác, cần kiểm tra thủ công.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.Products WITH(UPDLOCK,HOLDLOCK) WHERE Slug=N'do-choi-day-thung-bobo-hinh-gau')
INSERT dbo.Products(CategoryId,BrandId,PetType,Name,Slug,ShortDescription,Description,UsageInstructions,IsFeatured,CreatedByUserId)
SELECT c.Id,b.Id,N'BOTH',N'Đồ chơi dây thừng BOBO hình gấu',N'do-choi-day-thung-bobo-hinh-gau',N'Đồ chơi dây thừng tạo hình gấu cho chó mèo.',N'Đồ chơi BOBO dạng dây thừng đan thành hình gấu. Kiểm tra tình trạng đồ chơi trước khi sử dụng và giám sát thú cưng khi chơi.',N'Đọc hướng dẫn trên bao bì trước khi sử dụng.',1,@Admin FROM dbo.Categories c CROSS JOIN dbo.Brands b WHERE c.Code=N'DO-CHOI' AND b.Name=N'BOBO';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'BOBO-ROPE-BEAR' AND p.Slug<>N'do-choi-day-thung-bobo-hinh-gau') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'BOBO-ROPE-BEAR')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'BOBO-ROPE-BEAR',N'1 chiếc',55000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'do-choi-day-thung-bobo-hinh-gau';
IF NOT EXISTS(SELECT 1 FROM dbo.Brands WITH(UPDLOCK,HOLDLOCK) WHERE Name=N'BOBO') INSERT dbo.Brands(Name) VALUES(N'BOBO');
IF NOT EXISTS(SELECT 1 FROM dbo.Categories WHERE Code=N'PHU-KIEN' AND IsActive=1) THROW 50002,N'Thiếu danh mục hoạt động: PHU-KIEN',1;
IF EXISTS(SELECT 1 FROM dbo.Products WHERE Slug=N'bat-an-bobo-plastic-bowl-3060' AND Name COLLATE Latin1_General_100_BIN2<>N'Bát ăn BOBO Plastic Bowl 3060' COLLATE Latin1_General_100_BIN2) THROW 50003,N'Slug trùng với sản phẩm khác, cần kiểm tra thủ công.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.Products WITH(UPDLOCK,HOLDLOCK) WHERE Slug=N'bat-an-bobo-plastic-bowl-3060')
INSERT dbo.Products(CategoryId,BrandId,PetType,Name,Slug,ShortDescription,Description,UsageInstructions,IsFeatured,CreatedByUserId)
SELECT c.Id,b.Id,N'BOTH',N'Bát ăn BOBO Plastic Bowl 3060',N'bat-an-bobo-plastic-bowl-3060',N'Bát ăn nhựa cho chó mèo, kích thước 15 × 15 × 6,5 cm.',N'Bát ăn BOBO mã 3060 bằng nhựa. Dùng đựng thức ăn hoặc nước; vệ sinh sạch sau mỗi lần sử dụng.',N'Đọc hướng dẫn trên bao bì trước khi sử dụng.',0,@Admin FROM dbo.Categories c CROSS JOIN dbo.Brands b WHERE c.Code=N'PHU-KIEN' AND b.Name=N'BOBO';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'BOBO-BOWL-3060' AND p.Slug<>N'bat-an-bobo-plastic-bowl-3060') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'BOBO-BOWL-3060')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'BOBO-BOWL-3060',N'15 × 15 × 6,5 cm',90000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'bat-an-bobo-plastic-bowl-3060';
IF NOT EXISTS(SELECT 1 FROM dbo.Brands WITH(UPDLOCK,HOLDLOCK) WHERE Name=N'BOBO') INSERT dbo.Brands(Name) VALUES(N'BOBO');
IF NOT EXISTS(SELECT 1 FROM dbo.Categories WHERE Code=N'CHAM-SOC' AND IsActive=1) THROW 50002,N'Thiếu danh mục hoạt động: CHAM-SOC',1;
IF EXISTS(SELECT 1 FROM dbo.Products WHERE Slug=N'luoc-chai-long-bobo-pet-comb' AND Name COLLATE Latin1_General_100_BIN2<>N'Lược chải lông BOBO Pet Comb' COLLATE Latin1_General_100_BIN2) THROW 50003,N'Slug trùng với sản phẩm khác, cần kiểm tra thủ công.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.Products WITH(UPDLOCK,HOLDLOCK) WHERE Slug=N'luoc-chai-long-bobo-pet-comb')
INSERT dbo.Products(CategoryId,BrandId,PetType,Name,Slug,ShortDescription,Description,UsageInstructions,IsFeatured,CreatedByUserId)
SELECT c.Id,b.Id,N'BOTH',N'Lược chải lông BOBO Pet Comb',N'luoc-chai-long-bobo-pet-comb',N'Lược chải lông cho chó mèo, lựa chọn cỡ S hoặc M.',N'BOBO Pet Comb có tay cầm và răng lược mềm. Chải nhẹ theo chiều lông, không kéo mạnh vùng lông rối.',N'Đọc hướng dẫn trên bao bì trước khi sử dụng.',0,@Admin FROM dbo.Categories c CROSS JOIN dbo.Brands b WHERE c.Code=N'CHAM-SOC' AND b.Name=N'BOBO';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'BOBO-COMB-S' AND p.Slug<>N'luoc-chai-long-bobo-pet-comb') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'BOBO-COMB-S')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'BOBO-COMB-S',N'Cỡ S',85000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'luoc-chai-long-bobo-pet-comb';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'BOBO-COMB-M' AND p.Slug<>N'luoc-chai-long-bobo-pet-comb') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'BOBO-COMB-M')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'BOBO-COMB-M',N'Cỡ M',95000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'luoc-chai-long-bobo-pet-comb';
IF NOT EXISTS(SELECT 1 FROM dbo.Brands WITH(UPDLOCK,HOLDLOCK) WHERE Name=N'BOBO') INSERT dbo.Brands(Name) VALUES(N'BOBO');
IF NOT EXISTS(SELECT 1 FROM dbo.Categories WHERE Code=N'CHAM-SOC' AND IsActive=1) THROW 50002,N'Thiếu danh mục hoạt động: CHAM-SOC',1;
IF EXISTS(SELECT 1 FROM dbo.Products WHERE Slug=N'ban-chai-tam-bobo-pet-washing-brush' AND Name COLLATE Latin1_General_100_BIN2<>N'Bàn chải tắm BOBO Pet Washing Brush' COLLATE Latin1_General_100_BIN2) THROW 50003,N'Slug trùng với sản phẩm khác, cần kiểm tra thủ công.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.Products WITH(UPDLOCK,HOLDLOCK) WHERE Slug=N'ban-chai-tam-bobo-pet-washing-brush')
INSERT dbo.Products(CategoryId,BrandId,PetType,Name,Slug,ShortDescription,Description,UsageInstructions,IsFeatured,CreatedByUserId)
SELECT c.Id,b.Id,N'BOTH',N'Bàn chải tắm BOBO Pet Washing Brush',N'ban-chai-tam-bobo-pet-washing-brush',N'Bàn chải hỗ trợ vệ sinh lông cho chó mèo khi tắm.',N'Bàn chải tắm BOBO Pet Washing Brush. Làm sạch và để khô dụng cụ sau khi dùng.',N'Đọc hướng dẫn trên bao bì trước khi sử dụng.',0,@Admin FROM dbo.Categories c CROSS JOIN dbo.Brands b WHERE c.Code=N'CHAM-SOC' AND b.Name=N'BOBO';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'BOBO-WASH-BRUSH' AND p.Slug<>N'ban-chai-tam-bobo-pet-washing-brush') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'BOBO-WASH-BRUSH')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'BOBO-WASH-BRUSH',N'1 chiếc',30000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'ban-chai-tam-bobo-pet-washing-brush';
IF NOT EXISTS(SELECT 1 FROM dbo.Brands WITH(UPDLOCK,HOLDLOCK) WHERE Name=N'BOBO') INSERT dbo.Brands(Name) VALUES(N'BOBO');
IF NOT EXISTS(SELECT 1 FROM dbo.Categories WHERE Code=N'CHAM-SOC' AND IsActive=1) THROW 50002,N'Thiếu danh mục hoạt động: CHAM-SOC',1;
IF EXISTS(SELECT 1 FROM dbo.Products WHERE Slug=N'gang-tay-bobo-grooming-brush-gloves' AND Name COLLATE Latin1_General_100_BIN2<>N'Găng tay BOBO Grooming Brush Gloves' COLLATE Latin1_General_100_BIN2) THROW 50003,N'Slug trùng với sản phẩm khác, cần kiểm tra thủ công.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.Products WITH(UPDLOCK,HOLDLOCK) WHERE Slug=N'gang-tay-bobo-grooming-brush-gloves')
INSERT dbo.Products(CategoryId,BrandId,PetType,Name,Slug,ShortDescription,Description,UsageInstructions,IsFeatured,CreatedByUserId)
SELECT c.Id,b.Id,N'BOTH',N'Găng tay BOBO Grooming Brush Gloves',N'gang-tay-bobo-grooming-brush-gloves',N'Găng tay chải lông và hỗ trợ tắm cho chó mèo.',N'BOBO Pet Grooming Brush Gloves là dụng cụ chăm sóc lông dạng găng tay. Sử dụng nhẹ nhàng và vệ sinh sau mỗi lần dùng.',N'Đọc hướng dẫn trên bao bì trước khi sử dụng.',0,@Admin FROM dbo.Categories c CROSS JOIN dbo.Brands b WHERE c.Code=N'CHAM-SOC' AND b.Name=N'BOBO';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'BOBO-GROOMING-GLOVE' AND p.Slug<>N'gang-tay-bobo-grooming-brush-gloves') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'BOBO-GROOMING-GLOVE')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'BOBO-GROOMING-GLOVE',N'12,3 × 11,3 cm',40000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'gang-tay-bobo-grooming-brush-gloves';
IF NOT EXISTS(SELECT 1 FROM dbo.Brands WITH(UPDLOCK,HOLDLOCK) WHERE Name=N'DELE') INSERT dbo.Brands(Name) VALUES(N'DELE');
IF NOT EXISTS(SELECT 1 FROM dbo.Categories WHERE Code=N'PK-VONG-CO' AND IsActive=1) THROW 50002,N'Thiếu danh mục hoạt động: PK-VONG-CO',1;
IF EXISTS(SELECT 1 FROM dbo.Products WHERE Slug=N'day-dat-tu-cuon-dele-002' AND Name COLLATE Latin1_General_100_BIN2<>N'Dây dắt tự cuốn DELE 002' COLLATE Latin1_General_100_BIN2) THROW 50003,N'Slug trùng với sản phẩm khác, cần kiểm tra thủ công.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.Products WITH(UPDLOCK,HOLDLOCK) WHERE Slug=N'day-dat-tu-cuon-dele-002')
INSERT dbo.Products(CategoryId,BrandId,PetType,Name,Slug,ShortDescription,Description,UsageInstructions,IsFeatured,CreatedByUserId)
SELECT c.Id,b.Id,N'BOTH',N'Dây dắt tự cuốn DELE 002',N'day-dat-tu-cuon-dele-002',N'Dây dắt tự cuốn DELE 002 cho chó mèo đi dạo.',N'DELE 002 Retractable Leash dạng tự cuốn, có móc gắn vòng cổ. Chọn theo giới hạn chịu lực trên bao bì và kiểm tra khóa trước khi dùng.',N'Đọc hướng dẫn trên bao bì trước khi sử dụng.',0,@Admin FROM dbo.Categories c CROSS JOIN dbo.Brands b WHERE c.Code=N'PK-VONG-CO' AND b.Name=N'DELE';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'DELE-LEASH-002' AND p.Slug<>N'day-dat-tu-cuon-dele-002') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'DELE-LEASH-002')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'DELE-LEASH-002',N'Mẫu 002',165000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'day-dat-tu-cuon-dele-002';
IF NOT EXISTS(SELECT 1 FROM dbo.Brands WITH(UPDLOCK,HOLDLOCK) WHERE Name=N'Me-O') INSERT dbo.Brands(Name) VALUES(N'Me-O');
IF NOT EXISTS(SELECT 1 FROM dbo.Categories WHERE Code=N'CAT-VE-SINH' AND IsActive=1) THROW 50002,N'Thiếu danh mục hoạt động: CAT-VE-SINH',1;
IF EXISTS(SELECT 1 FROM dbo.Products WHERE Slug=N'cat-meo-lemon-scent-5l' AND Name COLLATE Latin1_General_100_BIN2<>N'Cát vệ sinh Me-O Lemon Scent 5L' COLLATE Latin1_General_100_BIN2) THROW 50003,N'Slug trùng với sản phẩm khác, cần kiểm tra thủ công.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.Products WITH(UPDLOCK,HOLDLOCK) WHERE Slug=N'cat-meo-lemon-scent-5l')
INSERT dbo.Products(CategoryId,BrandId,PetType,Name,Slug,ShortDescription,Description,UsageInstructions,IsFeatured,CreatedByUserId)
SELECT c.Id,b.Id,N'CAT',N'Cát vệ sinh Me-O Lemon Scent 5L',N'cat-meo-lemon-scent-5l',N'Cát vệ sinh cho mèo Me-O hương chanh, túi 5 lít.',N'Me-O Cat Litter Lemon Scent dùng cho khay vệ sinh của mèo. Vệ sinh khay và thay cát theo hướng dẫn trên bao bì.',N'Đọc hướng dẫn trên bao bì trước khi sử dụng.',0,@Admin FROM dbo.Categories c CROSS JOIN dbo.Brands b WHERE c.Code=N'CAT-VE-SINH' AND b.Name=N'Me-O';
IF EXISTS(SELECT 1 FROM dbo.ProductVariants v JOIN dbo.Products p ON p.Id=v.ProductId WHERE v.SkuCode=N'MEO-LEMON-5L' AND p.Slug<>N'cat-meo-lemon-scent-5l') THROW 50004,N'SKU đã thuộc sản phẩm khác.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductVariants WITH(UPDLOCK,HOLDLOCK) WHERE SkuCode=N'MEO-LEMON-5L')
INSERT dbo.ProductVariants(ProductId,SkuCode,VariantName,Price,OriginalPrice,LowStockThreshold)
OUTPUT inserted.Id,inserted.SkuCode INTO @NewVariants(Id,SkuCode)
SELECT p.Id,N'MEO-LEMON-5L',N'Túi 5 lít',95000,NULL,5 FROM dbo.Products p WHERE p.Slug=N'cat-meo-lemon-scent-5l';
UPDATE dbo.Products SET ShortDescription=N'Thức ăn hạt cho chó Poodle trưởng thành trên 10 tháng tuổi.',Description=N'Royal Canin Poodle Adult dành cho chó Poodle trưởng thành. Sản phẩm dạng hạt, có lựa chọn túi 500 g và 1,5 kg.',UsageInstructions=N'Dùng theo hướng dẫn trên bao bì; luôn có nước sạch. Bảo quản nơi khô ráo, đóng kín sau khi mở.',UpdatedAt=SYSDATETIME() WHERE Slug=N'hat-royal-canin-poodle-adult' AND Name=N'Hạt Royal Canin Poodle Adult' AND Description IS NULL;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'hat-royal-canin-poodle-adult' AND pi.ImageUrl=N'/images/products/hat-royal-canin-poodle-adult.jpg')
INSERT dbo.ProductImages(ProductId,ImageUrl,AltText,SortOrder,IsPrimary) SELECT p.Id,N'/images/products/hat-royal-canin-poodle-adult.jpg',N'Hạt Royal Canin Poodle Adult',0,CASE WHEN EXISTS(SELECT 1 FROM dbo.ProductImages old WHERE old.ProductId=p.Id AND old.IsPrimary=1) THEN 0 ELSE 1 END FROM dbo.Products p WHERE p.Slug=N'hat-royal-canin-poodle-adult';
UPDATE pi SET AltText=N'Hạt Royal Canin Poodle Adult' FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'hat-royal-canin-poodle-adult' AND pi.ImageUrl=N'/images/products/hat-royal-canin-poodle-adult.jpg';
UPDATE dbo.Products SET ShortDescription=N'Thức ăn ướt Whiskas vị cá thu cho mèo trưởng thành, gói 85 g.',Description=N'Thức ăn ướt Whiskas Mackerel dạng túi 85 g. Có thể chọn gói đơn hoặc thùng 24 gói; bảo quản theo thông tin trên bao bì.',UsageInstructions=N'Đọc hướng dẫn trên bao bì trước khi sử dụng.',UpdatedAt=SYSDATETIME() WHERE Slug=N'pate-whiskas-ca-thu' AND Name=N'Pate Whiskas vị cá thu' AND Description IS NULL;
IF NOT EXISTS(SELECT 1 FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'pate-whiskas-ca-thu' AND pi.ImageUrl=N'/images/products/pate-whiskas-ca-thu.jpg')
INSERT dbo.ProductImages(ProductId,ImageUrl,AltText,SortOrder,IsPrimary) SELECT p.Id,N'/images/products/pate-whiskas-ca-thu.jpg',N'Pate Whiskas vị cá thu',0,CASE WHEN EXISTS(SELECT 1 FROM dbo.ProductImages old WHERE old.ProductId=p.Id AND old.IsPrimary=1) THEN 0 ELSE 1 END FROM dbo.Products p WHERE p.Slug=N'pate-whiskas-ca-thu';
UPDATE pi SET AltText=N'Pate Whiskas vị cá thu' FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'pate-whiskas-ca-thu' AND pi.ImageUrl=N'/images/products/pate-whiskas-ca-thu.jpg';
IF NOT EXISTS(SELECT 1 FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'royal-canin-mini-adult' AND pi.ImageUrl=N'/images/products/royal-canin-mini-adult.jpg')
INSERT dbo.ProductImages(ProductId,ImageUrl,AltText,SortOrder,IsPrimary) SELECT p.Id,N'/images/products/royal-canin-mini-adult.jpg',N'Hạt Royal Canin Mini Adult',0,CASE WHEN EXISTS(SELECT 1 FROM dbo.ProductImages old WHERE old.ProductId=p.Id AND old.IsPrimary=1) THEN 0 ELSE 1 END FROM dbo.Products p WHERE p.Slug=N'royal-canin-mini-adult';
UPDATE pi SET AltText=N'Hạt Royal Canin Mini Adult' FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'royal-canin-mini-adult' AND pi.ImageUrl=N'/images/products/royal-canin-mini-adult.jpg';
IF NOT EXISTS(SELECT 1 FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'royal-canin-mini-puppy' AND pi.ImageUrl=N'/images/products/royal-canin-mini-puppy.jpg')
INSERT dbo.ProductImages(ProductId,ImageUrl,AltText,SortOrder,IsPrimary) SELECT p.Id,N'/images/products/royal-canin-mini-puppy.jpg',N'Hạt Royal Canin Mini Puppy',0,CASE WHEN EXISTS(SELECT 1 FROM dbo.ProductImages old WHERE old.ProductId=p.Id AND old.IsPrimary=1) THEN 0 ELSE 1 END FROM dbo.Products p WHERE p.Slug=N'royal-canin-mini-puppy';
UPDATE pi SET AltText=N'Hạt Royal Canin Mini Puppy' FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'royal-canin-mini-puppy' AND pi.ImageUrl=N'/images/products/royal-canin-mini-puppy.jpg';
IF NOT EXISTS(SELECT 1 FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'royal-canin-kitten' AND pi.ImageUrl=N'/images/products/royal-canin-kitten.jpg')
INSERT dbo.ProductImages(ProductId,ImageUrl,AltText,SortOrder,IsPrimary) SELECT p.Id,N'/images/products/royal-canin-kitten.jpg',N'Hạt Royal Canin Kitten',0,CASE WHEN EXISTS(SELECT 1 FROM dbo.ProductImages old WHERE old.ProductId=p.Id AND old.IsPrimary=1) THEN 0 ELSE 1 END FROM dbo.Products p WHERE p.Slug=N'royal-canin-kitten';
UPDATE pi SET AltText=N'Hạt Royal Canin Kitten' FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'royal-canin-kitten' AND pi.ImageUrl=N'/images/products/royal-canin-kitten.jpg';
IF NOT EXISTS(SELECT 1 FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'royal-canin-indoor-27' AND pi.ImageUrl=N'/images/products/royal-canin-indoor-27.jpg')
INSERT dbo.ProductImages(ProductId,ImageUrl,AltText,SortOrder,IsPrimary) SELECT p.Id,N'/images/products/royal-canin-indoor-27.jpg',N'Hạt Royal Canin Indoor 27',0,CASE WHEN EXISTS(SELECT 1 FROM dbo.ProductImages old WHERE old.ProductId=p.Id AND old.IsPrimary=1) THEN 0 ELSE 1 END FROM dbo.Products p WHERE p.Slug=N'royal-canin-indoor-27';
UPDATE pi SET AltText=N'Hạt Royal Canin Indoor 27' FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'royal-canin-indoor-27' AND pi.ImageUrl=N'/images/products/royal-canin-indoor-27.jpg';
IF NOT EXISTS(SELECT 1 FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'royal-canin-fit-32' AND pi.ImageUrl=N'/images/products/royal-canin-fit-32.jpg')
INSERT dbo.ProductImages(ProductId,ImageUrl,AltText,SortOrder,IsPrimary) SELECT p.Id,N'/images/products/royal-canin-fit-32.jpg',N'Hạt Royal Canin Fit 32',0,CASE WHEN EXISTS(SELECT 1 FROM dbo.ProductImages old WHERE old.ProductId=p.Id AND old.IsPrimary=1) THEN 0 ELSE 1 END FROM dbo.Products p WHERE p.Slug=N'royal-canin-fit-32';
UPDATE pi SET AltText=N'Hạt Royal Canin Fit 32' FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'royal-canin-fit-32' AND pi.ImageUrl=N'/images/products/royal-canin-fit-32.jpg';
IF NOT EXISTS(SELECT 1 FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'pate-inaba-ga-rau-cu-pho-mai' AND pi.ImageUrl=N'/images/products/pate-inaba-ga-rau-cu-pho-mai.jpg')
INSERT dbo.ProductImages(ProductId,ImageUrl,AltText,SortOrder,IsPrimary) SELECT p.Id,N'/images/products/pate-inaba-ga-rau-cu-pho-mai.jpg',N'Pate INABA vị gà, rau củ và phô mai',0,CASE WHEN EXISTS(SELECT 1 FROM dbo.ProductImages old WHERE old.ProductId=p.Id AND old.IsPrimary=1) THEN 0 ELSE 1 END FROM dbo.Products p WHERE p.Slug=N'pate-inaba-ga-rau-cu-pho-mai';
UPDATE pi SET AltText=N'Pate INABA vị gà, rau củ và phô mai' FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'pate-inaba-ga-rau-cu-pho-mai' AND pi.ImageUrl=N'/images/products/pate-inaba-ga-rau-cu-pho-mai.jpg';
IF NOT EXISTS(SELECT 1 FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'ciao-ga-phi-le-ca-ngu-nuong' AND pi.ImageUrl=N'/images/products/ciao-ga-phi-le-ca-ngu-nuong.jpg')
INSERT dbo.ProductImages(ProductId,ImageUrl,AltText,SortOrder,IsPrimary) SELECT p.Id,N'/images/products/ciao-ga-phi-le-ca-ngu-nuong.jpg',N'CIAO vị gà phi lê và cá ngừ nướng',0,CASE WHEN EXISTS(SELECT 1 FROM dbo.ProductImages old WHERE old.ProductId=p.Id AND old.IsPrimary=1) THEN 0 ELSE 1 END FROM dbo.Products p WHERE p.Slug=N'ciao-ga-phi-le-ca-ngu-nuong';
UPDATE pi SET AltText=N'CIAO vị gà phi lê và cá ngừ nướng' FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'ciao-ga-phi-le-ca-ngu-nuong' AND pi.ImageUrl=N'/images/products/ciao-ga-phi-le-ca-ngu-nuong.jpg';
IF NOT EXISTS(SELECT 1 FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'pate-meowow-ca-ngu-thit-cua' AND pi.ImageUrl=N'/images/products/pate-meowow-ca-ngu-thit-cua.jpg')
INSERT dbo.ProductImages(ProductId,ImageUrl,AltText,SortOrder,IsPrimary) SELECT p.Id,N'/images/products/pate-meowow-ca-ngu-thit-cua.jpg',N'Pate MEOWOW vị cá ngừ trắng và thịt cua',0,CASE WHEN EXISTS(SELECT 1 FROM dbo.ProductImages old WHERE old.ProductId=p.Id AND old.IsPrimary=1) THEN 0 ELSE 1 END FROM dbo.Products p WHERE p.Slug=N'pate-meowow-ca-ngu-thit-cua';
UPDATE pi SET AltText=N'Pate MEOWOW vị cá ngừ trắng và thịt cua' FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'pate-meowow-ca-ngu-thit-cua' AND pi.ImageUrl=N'/images/products/pate-meowow-ca-ngu-thit-cua.jpg';
IF NOT EXISTS(SELECT 1 FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'do-choi-day-thung-bobo-hinh-gau' AND pi.ImageUrl=N'/images/products/do-choi-day-thung-bobo-hinh-gau.jpg')
INSERT dbo.ProductImages(ProductId,ImageUrl,AltText,SortOrder,IsPrimary) SELECT p.Id,N'/images/products/do-choi-day-thung-bobo-hinh-gau.jpg',N'Đồ chơi dây thừng BOBO hình gấu',0,CASE WHEN EXISTS(SELECT 1 FROM dbo.ProductImages old WHERE old.ProductId=p.Id AND old.IsPrimary=1) THEN 0 ELSE 1 END FROM dbo.Products p WHERE p.Slug=N'do-choi-day-thung-bobo-hinh-gau';
UPDATE pi SET AltText=N'Đồ chơi dây thừng BOBO hình gấu' FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'do-choi-day-thung-bobo-hinh-gau' AND pi.ImageUrl=N'/images/products/do-choi-day-thung-bobo-hinh-gau.jpg';
IF NOT EXISTS(SELECT 1 FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'bat-an-bobo-plastic-bowl-3060' AND pi.ImageUrl=N'/images/products/bat-an-bobo-plastic-bowl-3060.jpg')
INSERT dbo.ProductImages(ProductId,ImageUrl,AltText,SortOrder,IsPrimary) SELECT p.Id,N'/images/products/bat-an-bobo-plastic-bowl-3060.jpg',N'Bát ăn BOBO Plastic Bowl 3060',0,CASE WHEN EXISTS(SELECT 1 FROM dbo.ProductImages old WHERE old.ProductId=p.Id AND old.IsPrimary=1) THEN 0 ELSE 1 END FROM dbo.Products p WHERE p.Slug=N'bat-an-bobo-plastic-bowl-3060';
UPDATE pi SET AltText=N'Bát ăn BOBO Plastic Bowl 3060' FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'bat-an-bobo-plastic-bowl-3060' AND pi.ImageUrl=N'/images/products/bat-an-bobo-plastic-bowl-3060.jpg';
IF NOT EXISTS(SELECT 1 FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'luoc-chai-long-bobo-pet-comb' AND pi.ImageUrl=N'/images/products/luoc-chai-long-bobo-pet-comb.jpg')
INSERT dbo.ProductImages(ProductId,ImageUrl,AltText,SortOrder,IsPrimary) SELECT p.Id,N'/images/products/luoc-chai-long-bobo-pet-comb.jpg',N'Lược chải lông BOBO Pet Comb',0,CASE WHEN EXISTS(SELECT 1 FROM dbo.ProductImages old WHERE old.ProductId=p.Id AND old.IsPrimary=1) THEN 0 ELSE 1 END FROM dbo.Products p WHERE p.Slug=N'luoc-chai-long-bobo-pet-comb';
UPDATE pi SET AltText=N'Lược chải lông BOBO Pet Comb' FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'luoc-chai-long-bobo-pet-comb' AND pi.ImageUrl=N'/images/products/luoc-chai-long-bobo-pet-comb.jpg';
IF NOT EXISTS(SELECT 1 FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'ban-chai-tam-bobo-pet-washing-brush' AND pi.ImageUrl=N'/images/products/ban-chai-tam-bobo-pet-washing-brush.jpg')
INSERT dbo.ProductImages(ProductId,ImageUrl,AltText,SortOrder,IsPrimary) SELECT p.Id,N'/images/products/ban-chai-tam-bobo-pet-washing-brush.jpg',N'Bàn chải tắm BOBO Pet Washing Brush',0,CASE WHEN EXISTS(SELECT 1 FROM dbo.ProductImages old WHERE old.ProductId=p.Id AND old.IsPrimary=1) THEN 0 ELSE 1 END FROM dbo.Products p WHERE p.Slug=N'ban-chai-tam-bobo-pet-washing-brush';
UPDATE pi SET AltText=N'Bàn chải tắm BOBO Pet Washing Brush' FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'ban-chai-tam-bobo-pet-washing-brush' AND pi.ImageUrl=N'/images/products/ban-chai-tam-bobo-pet-washing-brush.jpg';
IF NOT EXISTS(SELECT 1 FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'gang-tay-bobo-grooming-brush-gloves' AND pi.ImageUrl=N'/images/products/gang-tay-bobo-grooming-brush-gloves.jpg')
INSERT dbo.ProductImages(ProductId,ImageUrl,AltText,SortOrder,IsPrimary) SELECT p.Id,N'/images/products/gang-tay-bobo-grooming-brush-gloves.jpg',N'Găng tay BOBO Grooming Brush Gloves',0,CASE WHEN EXISTS(SELECT 1 FROM dbo.ProductImages old WHERE old.ProductId=p.Id AND old.IsPrimary=1) THEN 0 ELSE 1 END FROM dbo.Products p WHERE p.Slug=N'gang-tay-bobo-grooming-brush-gloves';
UPDATE pi SET AltText=N'Găng tay BOBO Grooming Brush Gloves' FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'gang-tay-bobo-grooming-brush-gloves' AND pi.ImageUrl=N'/images/products/gang-tay-bobo-grooming-brush-gloves.jpg';
IF NOT EXISTS(SELECT 1 FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'day-dat-tu-cuon-dele-002' AND pi.ImageUrl=N'/images/products/day-dat-tu-cuon-dele-002.jpg')
INSERT dbo.ProductImages(ProductId,ImageUrl,AltText,SortOrder,IsPrimary) SELECT p.Id,N'/images/products/day-dat-tu-cuon-dele-002.jpg',N'Dây dắt tự cuốn DELE 002',0,CASE WHEN EXISTS(SELECT 1 FROM dbo.ProductImages old WHERE old.ProductId=p.Id AND old.IsPrimary=1) THEN 0 ELSE 1 END FROM dbo.Products p WHERE p.Slug=N'day-dat-tu-cuon-dele-002';
UPDATE pi SET AltText=N'Dây dắt tự cuốn DELE 002' FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'day-dat-tu-cuon-dele-002' AND pi.ImageUrl=N'/images/products/day-dat-tu-cuon-dele-002.jpg';
IF NOT EXISTS(SELECT 1 FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'cat-meo-lemon-scent-5l' AND pi.ImageUrl=N'/images/products/cat-meo-lemon-scent-5l.webp')
INSERT dbo.ProductImages(ProductId,ImageUrl,AltText,SortOrder,IsPrimary) SELECT p.Id,N'/images/products/cat-meo-lemon-scent-5l.webp',N'Cát vệ sinh Me-O Lemon Scent 5L',0,CASE WHEN EXISTS(SELECT 1 FROM dbo.ProductImages old WHERE old.ProductId=p.Id AND old.IsPrimary=1) THEN 0 ELSE 1 END FROM dbo.Products p WHERE p.Slug=N'cat-meo-lemon-scent-5l';
UPDATE pi SET AltText=N'Cát vệ sinh Me-O Lemon Scent 5L' FROM dbo.ProductImages pi JOIN dbo.Products p ON p.Id=pi.ProductId WHERE p.Slug=N'cat-meo-lemon-scent-5l' AND pi.ImageUrl=N'/images/products/cat-meo-lemon-scent-5l.webp';

IF EXISTS(SELECT 1 FROM @NewVariants)
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.StockImports WHERE ImportCode='IMP-CATALOG-DEMO') THROW 50005,N'Phiếu demo đã tồn tại nhưng thiếu SKU; kiểm tra trước khi nạp lại.',1;
    INSERT dbo.StockImports(ImportCode,SupplierName,Note,CreatedByUserId)
    VALUES('IMP-CATALOG-DEMO',N'Nhà cung cấp dữ liệu demo',N'Dữ liệu tồn đầu kỳ phục vụ kiểm thử/demo. Giá nhập và số lượng giả lập; không phải giao dịch thực tế.',@Staff);
    DECLARE @ImportId BIGINT=SCOPE_IDENTITY();
    DECLARE @Opening TABLE(SkuCode VARCHAR(80) PRIMARY KEY,Quantity INT,UnitCost DECIMAL(18,2));
    INSERT @Opening(SkuCode,Quantity,UnitCost) VALUES

(N'RC-MINI-ADULT-800G',20,136500),
(N'RC-MINI-ADULT-2KG',12,297500),
(N'RC-MINI-PUPPY-800G',18,154000),
(N'RC-MINI-PUPPY-2KG',10,346500),
(N'RC-KITTEN-400G',24,101500),
(N'RC-KITTEN-2KG',12,430500),
(N'RC-INDOOR-400G',20,94500),
(N'RC-INDOOR-2KG',10,381500),
(N'RC-FIT-2KG',15,311500),
(N'RC-FIT-4KG',8,577500),
(N'INABA-CHICKEN-CHEESE-35GX2',40,24500),
(N'CIAO-CHICKEN-BONITO-10GX4',35,38500),
(N'MEOWOW-TUNA-KANIKAMA-80G',40,17500),
(N'BOBO-ROPE-BEAR',20,38500),
(N'BOBO-BOWL-3060',15,62999),
(N'BOBO-COMB-S',15,59499),
(N'BOBO-COMB-M',12,66500),
(N'BOBO-WASH-BRUSH',20,21000),
(N'BOBO-GROOMING-GLOVE',20,28000),
(N'DELE-LEASH-002',4,115499),
(N'MEO-LEMON-5L',20,66500);

    INSERT dbo.StockImportItems(ImportId,VariantId,Quantity,UnitCost)
    SELECT @ImportId,n.Id,o.Quantity,o.UnitCost FROM @NewVariants n JOIN @Opening o ON o.SkuCode=n.SkuCode;
    UPDATE v SET StockQuantity=v.StockQuantity+o.Quantity,UpdatedAt=SYSDATETIME()
    FROM dbo.ProductVariants v JOIN @NewVariants n ON n.Id=v.Id JOIN @Opening o ON o.SkuCode=n.SkuCode;
    IF EXISTS(SELECT 1 FROM dbo.vw_StockReconciliation r JOIN @NewVariants n ON n.Id=r.VariantId WHERE r.Difference<>0)
        THROW 50006,N'Tồn kho mới không khớp phiếu nhập.',1;
END;
COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO
