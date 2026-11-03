-- Script nạp dữ liệu mẫu Đơn Hàng & Đánh Giá phục vụ Demo môn SWP391
USE [PetShopDB];
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

SET XACT_ABORT ON;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @NewOrders TABLE (Id BIGINT PRIMARY KEY);
DECLARE @CustomerId BIGINT = (SELECT TOP 1 Id FROM dbo.Users WITH (UPDLOCK, HOLDLOCK) WHERE Email = N'customer@petshop.vn');
DECLARE @VoucherWelcomeId BIGINT = (SELECT TOP 1 Id FROM dbo.Vouchers WHERE Code = 'PETWELCOME10');
DECLARE @VoucherFreeshipId BIGINT = (SELECT TOP 1 Id FROM dbo.Vouchers WHERE Code = 'FREESHIP30K');

-- Lấy các VariantId mẫu
DECLARE @VarPoodle500g BIGINT = (SELECT TOP 1 Id FROM dbo.ProductVariants WHERE SkuCode = 'RC-POODLE-500G');
DECLARE @VarPoodle1500g BIGINT = (SELECT TOP 1 Id FROM dbo.ProductVariants WHERE SkuCode = 'RC-POODLE-1500G');
DECLARE @VarPate85g BIGINT = (SELECT TOP 1 Id FROM dbo.ProductVariants WHERE SkuCode = 'WHK-MACKEREL-85G');
DECLARE @VarCatLitter8L BIGINT = (SELECT TOP 1 Id FROM dbo.ProductVariants WHERE SkuCode = 'MEO-LITTER-8L');
DECLARE @VarBallS BIGINT = (SELECT TOP 1 Id FROM dbo.ProductVariants WHERE SkuCode = 'BALL-RUBBER-S');
DECLARE @VarLeashM BIGINT = (SELECT TOP 1 Id FROM dbo.ProductVariants WHERE SkuCode = 'LEASH-SET-M');

IF @CustomerId IS NULL OR @VoucherWelcomeId IS NULL OR @VoucherFreeshipId IS NULL
OR @VarPoodle500g IS NULL OR @VarPoodle1500g IS NULL OR @VarPate85g IS NULL OR @VarCatLitter8L IS NULL OR @VarBallS IS NULL OR @VarLeashM IS NULL
    THROW 50001, N'Chưa có dữ liệu nền; seed demo không được thực hiện.', 1;

-- 1. ĐƠN HÀNG 1: Hoàn tất 6 ngày trước
IF NOT EXISTS (SELECT 1 FROM dbo.Orders WHERE OrderCode = 'ORD-20261001-001')
BEGIN
    INSERT INTO dbo.Orders (
        OrderCode, CustomerId, VoucherId, VoucherCodeSnapshot,
        RecipientName, RecipientPhone, ShippingProvince, ShippingDistrict, ShippingWard, ShippingAddressLine,
        CustomerNote, SubtotalAmount, DiscountAmount, ShippingFee, TotalAmount,
        OrderStatus, PaymentMethod, PaymentStatus, CompletedAt, CreatedAt, UpdatedAt
    ) VALUES (
        'ORD-20261001-001', @CustomerId, NULL, NULL,
        N'Nguyễn Văn Khách', '0912345678', N'Hà Nội', N'Cầu Giấy', N'Dịch Vọng Hậu', N'123 Đường Cầu Giấy',
        N'Giao giờ hành chính', 420000, 0, 30000, 450000,
        'COMPLETED', 'COD', 'PAID',
        DATEADD(DAY, -6, SYSDATETIME()), DATEADD(DAY, -7, SYSDATETIME()), DATEADD(DAY, -6, SYSDATETIME())
    );

    DECLARE @Ord1Id BIGINT = SCOPE_IDENTITY();
    INSERT INTO @NewOrders VALUES (@Ord1Id);
    INSERT INTO dbo.OrderItems (OrderId, VariantId, ProductNameSnapshot, SkuCodeSnapshot, VariantNameSnapshot, UnitPrice, Quantity)
    VALUES (@Ord1Id, @VarPoodle1500g, N'Hạt Royal Canin Poodle Adult', 'RC-POODLE-1500G', N'Túi 1.5kg', 420000, 1);
END;

-- 2. ĐƠN HÀNG 2: Hoàn tất 5 ngày trước (Áp dụng voucher PETWELCOME10 giảm 50k)
IF NOT EXISTS (SELECT 1 FROM dbo.Orders WHERE OrderCode = 'ORD-20261002-002')
BEGIN
    INSERT INTO dbo.Orders (
        OrderCode, CustomerId, VoucherId, VoucherCodeSnapshot,
        RecipientName, RecipientPhone, ShippingProvince, ShippingDistrict, ShippingWard, ShippingAddressLine,
        CustomerNote, SubtotalAmount, DiscountAmount, ShippingFee, TotalAmount,
        OrderStatus, PaymentMethod, PaymentStatus, CompletedAt, CreatedAt, UpdatedAt
    ) VALUES (
        'ORD-20261002-002', @CustomerId, @VoucherWelcomeId, 'PETWELCOME10',
        N'Nguyễn Văn Khách', '0912345678', N'Hà Nội', N'Cầu Giấy', N'Dịch Vọng Hậu', N'123 Đường Cầu Giấy',
        N'Để ở bàn lễ tân', 585000, 50000, 30000, 565000,
        'COMPLETED', 'VNPAY', 'PAID',
        DATEADD(DAY, -5, SYSDATETIME()), DATEADD(DAY, -5, SYSDATETIME()), DATEADD(DAY, -5, SYSDATETIME())
    );

    DECLARE @Ord2Id BIGINT = SCOPE_IDENTITY();
    INSERT INTO @NewOrders VALUES (@Ord2Id);
    INSERT INTO dbo.OrderItems (OrderId, VariantId, ProductNameSnapshot, SkuCodeSnapshot, VariantNameSnapshot, UnitPrice, Quantity)
    VALUES 
        (@Ord2Id, @VarPoodle500g, N'Hạt Royal Canin Poodle Adult', 'RC-POODLE-500G', N'Túi 500g', 165000, 1),
        (@Ord2Id, @VarPoodle1500g, N'Hạt Royal Canin Poodle Adult', 'RC-POODLE-1500G', N'Túi 1.5kg', 420000, 1);
END;

-- 3. ĐƠN HÀNG 3: Hoàn tất 3 ngày trước (Cát vệ sinh + Đồ chơi gặm)
IF NOT EXISTS (SELECT 1 FROM dbo.Orders WHERE OrderCode = 'ORD-20261003-003')
BEGIN
    INSERT INTO dbo.Orders (
        OrderCode, CustomerId, VoucherId, VoucherCodeSnapshot,
        RecipientName, RecipientPhone, ShippingProvince, ShippingDistrict, ShippingWard, ShippingAddressLine,
        CustomerNote, SubtotalAmount, DiscountAmount, ShippingFee, TotalAmount,
        OrderStatus, PaymentMethod, PaymentStatus, CompletedAt, CreatedAt, UpdatedAt
    ) VALUES (
        'ORD-20261003-003', @CustomerId, NULL, NULL,
        N'Nguyễn Văn Khách', '0912345678', N'Hà Nội', N'Cầu Giấy', N'Dịch Vọng Hậu', N'123 Đường Cầu Giấy',
        NULL, 130000, 0, 30000, 160000,
        'COMPLETED', 'COD', 'PAID',
        DATEADD(DAY, -3, SYSDATETIME()), DATEADD(DAY, -4, SYSDATETIME()), DATEADD(DAY, -3, SYSDATETIME())
    );

    DECLARE @Ord3Id BIGINT = SCOPE_IDENTITY();
    INSERT INTO @NewOrders VALUES (@Ord3Id);
    INSERT INTO dbo.OrderItems (OrderId, VariantId, ProductNameSnapshot, SkuCodeSnapshot, VariantNameSnapshot, UnitPrice, Quantity)
    VALUES 
        (@Ord3Id, @VarCatLitter8L, N'Cát Vệ Sinh Me-O Hương Chanh', 'MEO-LITTER-8L', N'Túi 8 lít', 95000, 1),
        (@Ord3Id, @VarBallS, N'Bóng Cao Su Gặm Cho Chó', 'BALL-RUBBER-S', N'Size S', 35000, 1);
END;

-- 4. ĐƠN HÀNG 4: Hoàn tất 2 ngày trước (Pate cá thu + Vòng cổ)
IF NOT EXISTS (SELECT 1 FROM dbo.Orders WHERE OrderCode = 'ORD-20261004-004')
BEGIN
    INSERT INTO dbo.Orders (
        OrderCode, CustomerId, VoucherId, VoucherCodeSnapshot,
        RecipientName, RecipientPhone, ShippingProvince, ShippingDistrict, ShippingWard, ShippingAddressLine,
        CustomerNote, SubtotalAmount, DiscountAmount, ShippingFee, TotalAmount,
        OrderStatus, PaymentMethod, PaymentStatus, CompletedAt, CreatedAt, UpdatedAt
    ) VALUES (
        'ORD-20261004-004', @CustomerId, @VoucherFreeshipId, 'FREESHIP30K',
        N'Nguyễn Văn Khách', '0912345678', N'Hà Nội', N'Cầu Giấy', N'Dịch Vọng Hậu', N'123 Đường Cầu Giấy',
        N'Giao trước 5h chiều', 537000, 30000, 30000, 537000,
        'COMPLETED', 'VNPAY', 'PAID',
        DATEADD(DAY, -2, SYSDATETIME()), DATEADD(DAY, -2, SYSDATETIME()), DATEADD(DAY, -2, SYSDATETIME())
    );

    DECLARE @Ord4Id BIGINT = SCOPE_IDENTITY();
    INSERT INTO @NewOrders VALUES (@Ord4Id);
    INSERT INTO dbo.OrderItems (OrderId, VariantId, ProductNameSnapshot, SkuCodeSnapshot, VariantNameSnapshot, UnitPrice, Quantity)
    VALUES 
        (@Ord4Id, @VarPate85g, N'Pate Whiskas Vị Cá Thu', 'WHK-MACKEREL-85G', N'Túi 85g', 18000, 1),
        (@Ord4Id, @VarLeashM, N'Bộ Vòng Cổ Dây Dắt', 'LEASH-SET-M', N'Size M', 99000, 1),
        (@Ord4Id, @VarPoodle1500g, N'Hạt Royal Canin Poodle Adult', 'RC-POODLE-1500G', N'Túi 1.5kg', 420000, 1);
END;

-- 5. ĐƠN HÀNG 5: Hoàn tất hôm qua
IF NOT EXISTS (SELECT 1 FROM dbo.Orders WHERE OrderCode = 'ORD-20261005-005')
BEGIN
    INSERT INTO dbo.Orders (
        OrderCode, CustomerId, VoucherId, VoucherCodeSnapshot,
        RecipientName, RecipientPhone, ShippingProvince, ShippingDistrict, ShippingWard, ShippingAddressLine,
        CustomerNote, SubtotalAmount, DiscountAmount, ShippingFee, TotalAmount,
        OrderStatus, PaymentMethod, PaymentStatus, CompletedAt, CreatedAt, UpdatedAt
    ) VALUES (
        'ORD-20261005-005', @CustomerId, NULL, NULL,
        N'Nguyễn Văn Khách', '0912345678', N'Hà Nội', N'Cầu Giấy', N'Dịch Vọng Hậu', N'123 Đường Cầu Giấy',
        NULL, 190000, 0, 30000, 220000,
        'COMPLETED', 'COD', 'PAID',
        DATEADD(DAY, -1, SYSDATETIME()), DATEADD(DAY, -1, SYSDATETIME()), DATEADD(DAY, -1, SYSDATETIME())
    );

    DECLARE @Ord5Id BIGINT = SCOPE_IDENTITY();
    INSERT INTO @NewOrders VALUES (@Ord5Id);
    INSERT INTO dbo.OrderItems (OrderId, VariantId, ProductNameSnapshot, SkuCodeSnapshot, VariantNameSnapshot, UnitPrice, Quantity)
    VALUES 
        (@Ord5Id, @VarCatLitter8L, N'Cát Vệ Sinh Me-O Hương Chanh', 'MEO-LITTER-8L', N'Túi 8 lít', 95000, 2);
END;

-- 6. ĐƠN HÀNG 6: Hoàn tất hôm nay
IF NOT EXISTS (SELECT 1 FROM dbo.Orders WHERE OrderCode = 'ORD-20261006-006')
BEGIN
    INSERT INTO dbo.Orders (
        OrderCode, CustomerId, VoucherId, VoucherCodeSnapshot,
        RecipientName, RecipientPhone, ShippingProvince, ShippingDistrict, ShippingWard, ShippingAddressLine,
        CustomerNote, SubtotalAmount, DiscountAmount, ShippingFee, TotalAmount,
        OrderStatus, PaymentMethod, PaymentStatus, CompletedAt, CreatedAt, UpdatedAt
    ) VALUES (
        'ORD-20261006-006', @CustomerId, NULL, NULL,
        N'Nguyễn Văn Khách', '0912345678', N'Hà Nội', N'Cầu Giấy', N'Dịch Vọng Hậu', N'123 Đường Cầu Giấy',
        N'Giao gấp giúp em', 330000, 0, 30000, 360000,
        'COMPLETED', 'COD', 'PAID',
        SYSDATETIME(), SYSDATETIME(), SYSDATETIME()
    );

    DECLARE @Ord6Id BIGINT = SCOPE_IDENTITY();
    INSERT INTO @NewOrders VALUES (@Ord6Id);
    INSERT INTO dbo.OrderItems (OrderId, VariantId, ProductNameSnapshot, SkuCodeSnapshot, VariantNameSnapshot, UnitPrice, Quantity)
    VALUES 
        (@Ord6Id, @VarPoodle500g, N'Hạt Royal Canin Poodle Adult', 'RC-POODLE-500G', N'Túi 500g', 165000, 2);
END;

-- 7. ĐƠN HÀNG 7: Đơn chờ duyệt (PENDING_CONFIRMATION) để Admin/Staff duyệt thử
IF NOT EXISTS (SELECT 1 FROM dbo.Orders WHERE OrderCode = 'ORD-20261006-007')
BEGIN
    INSERT INTO dbo.Orders (
        OrderCode, CustomerId, VoucherId, VoucherCodeSnapshot,
        RecipientName, RecipientPhone, ShippingProvince, ShippingDistrict, ShippingWard, ShippingAddressLine,
        CustomerNote, SubtotalAmount, DiscountAmount, ShippingFee, TotalAmount,
        OrderStatus, PaymentMethod, PaymentStatus, CompletedAt, CreatedAt, UpdatedAt
    ) VALUES (
        'ORD-20261006-007', @CustomerId, NULL, NULL,
        N'Nguyễn Văn Khách', '0912345678', N'Hà Nội', N'Cầu Giấy', N'Dịch Vọng Hậu', N'123 Đường Cầu Giấy',
        N'Gọi trước khi giao', 165000, 0, 30000, 195000,
        'PENDING_CONFIRMATION', 'COD', 'UNPAID',
        NULL, SYSDATETIME(), SYSDATETIME()
    );

    DECLARE @Ord7Id BIGINT = SCOPE_IDENTITY();
    INSERT INTO @NewOrders VALUES (@Ord7Id);
    INSERT INTO dbo.OrderItems (OrderId, VariantId, ProductNameSnapshot, SkuCodeSnapshot, VariantNameSnapshot, UnitPrice, Quantity)
    VALUES 
        (@Ord7Id, @VarPoodle500g, N'Hạt Royal Canin Poodle Adult', 'RC-POODLE-500G', N'Túi 500g', 165000, 1);
END;

-- 8. ĐƠN HÀNG 8: Đơn đã hủy (CANCELLED) để test lọc danh sách
IF NOT EXISTS (SELECT 1 FROM dbo.Orders WHERE OrderCode = 'ORD-20261004-008')
BEGIN
    INSERT INTO dbo.Orders (
        OrderCode, CustomerId, VoucherId, VoucherCodeSnapshot,
        RecipientName, RecipientPhone, ShippingProvince, ShippingDistrict, ShippingWard, ShippingAddressLine,
        CustomerNote, SubtotalAmount, DiscountAmount, ShippingFee, TotalAmount,
        OrderStatus, PaymentMethod, PaymentStatus, CancelType, CancelReason, CancelledAt, CreatedAt, UpdatedAt
    ) VALUES (
        'ORD-20261004-008', @CustomerId, NULL, NULL,
        N'Nguyễn Văn Khách', '0912345678', N'Hà Nội', N'Cầu Giấy', N'Dịch Vọng Hậu', N'123 Đường Cầu Giấy',
        NULL, 95000, 0, 30000, 125000,
        'CANCELLED', 'COD', 'CANCELLED', 'CUSTOMER_CANCEL', N'Tôi đổi ý không mua nữa',
        DATEADD(DAY, -2, SYSDATETIME()), DATEADD(DAY, -2, SYSDATETIME()), DATEADD(DAY, -2, SYSDATETIME())
    );

    DECLARE @Ord8Id BIGINT = SCOPE_IDENTITY();
    INSERT INTO @NewOrders VALUES (@Ord8Id);
    INSERT INTO dbo.OrderItems (OrderId, VariantId, ProductNameSnapshot, SkuCodeSnapshot, VariantNameSnapshot, UnitPrice, Quantity)
    VALUES 
        (@Ord8Id, @VarCatLitter8L, N'Cát Vệ Sinh Me-O Hương Chanh', 'MEO-LITTER-8L', N'Túi 8 lít', 95000, 1);
END;

-- 2. NẠP ĐÁNH GIÁ MẪU (REVIEWS)
-- Lấy các OrderItem từ đơn hàng hoàn tất để tạo review
DECLARE @ItemOrd1 BIGINT = (SELECT TOP 1 Id FROM dbo.OrderItems WHERE OrderId = (SELECT Id FROM dbo.Orders WHERE OrderCode = 'ORD-20261001-001'));
DECLARE @ItemOrd2_1 BIGINT = (SELECT TOP 1 Id FROM dbo.OrderItems WHERE OrderId = (SELECT Id FROM dbo.Orders WHERE OrderCode = 'ORD-20261002-002') ORDER BY Id ASC);
DECLARE @ItemOrd3_1 BIGINT = (SELECT TOP 1 Id FROM dbo.OrderItems WHERE OrderId = (SELECT Id FROM dbo.Orders WHERE OrderCode = 'ORD-20261003-003') ORDER BY Id ASC);
DECLARE @ItemOrd4_1 BIGINT = (SELECT TOP 1 Id FROM dbo.OrderItems WHERE OrderId = (SELECT Id FROM dbo.Orders WHERE OrderCode = 'ORD-20261004-004') ORDER BY Id ASC);
DECLARE @ItemOrd4_2 BIGINT = (SELECT TOP 1 Id FROM dbo.OrderItems WHERE OrderId = (SELECT Id FROM dbo.Orders WHERE OrderCode = 'ORD-20261004-004') ORDER BY Id DESC);

IF @ItemOrd1 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.Reviews WHERE OrderItemId = @ItemOrd1)
BEGIN
    INSERT INTO dbo.Reviews (OrderItemId, CustomerId, Rating, Comment, Status, CreatedAt, UpdatedAt)
    VALUES (@ItemOrd1, @CustomerId, 5, N'Hạt thơm ngon, hạt giòn vừa miệng, cún Poodle nhà mình ăn rất hào hứng. Đóng gói cẩn thận 10 điểm!', 'VISIBLE', DATEADD(DAY, -5, SYSDATETIME()), DATEADD(DAY, -5, SYSDATETIME()));
END;

IF @ItemOrd2_1 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.Reviews WHERE OrderItemId = @ItemOrd2_1)
BEGIN
    INSERT INTO dbo.Reviews (OrderItemId, CustomerId, Rating, Comment, Status, CreatedAt, UpdatedAt)
    VALUES (@ItemOrd2_1, @CustomerId, 5, N'Hạt Royal Canin chuẩn chính hãng, bao bì có tem mác rõ ràng. Cún ăn tiêu hóa rất tốt, lông mượt!', 'VISIBLE', DATEADD(DAY, -4, SYSDATETIME()), DATEADD(DAY, -4, SYSDATETIME()));
END;

IF @ItemOrd3_1 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.Reviews WHERE OrderItemId = @ItemOrd3_1)
BEGIN
    INSERT INTO dbo.Reviews (OrderItemId, CustomerId, Rating, Comment, Status, CreatedAt, UpdatedAt)
    VALUES (@ItemOrd3_1, @CustomerId, 4, N'Cát vệ sinh vón cục khá tốt, mùi chanh nhẹ nhàng khử mùi hiệu quả. Sẽ tiếp tục ủng hộ shop lâu dài.', 'VISIBLE', DATEADD(DAY, -2, SYSDATETIME()), DATEADD(DAY, -2, SYSDATETIME()));
END;

IF @ItemOrd4_1 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.Reviews WHERE OrderItemId = @ItemOrd4_1)
BEGIN
    INSERT INTO dbo.Reviews (OrderItemId, CustomerId, Rating, Comment, Status, CreatedAt, UpdatedAt)
    VALUES (@ItemOrd4_1, @CustomerId, 5, N'Pate cá thu thơm lừng, mèo nhà mình kén ăn mà cũng vét sạch đĩa. Giao hàng siêu nhanh trong ngày!', 'VISIBLE', DATEADD(DAY, -1, SYSDATETIME()), DATEADD(DAY, -1, SYSDATETIME()));
END;

-- Review có điểm 1 sao và đang bị ẨN (HIDDEN) để thử nghiệm chức năng bật/tắt duyệt review của Admin
IF @ItemOrd4_2 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.Reviews WHERE OrderItemId = @ItemOrd4_2)
BEGIN
    INSERT INTO dbo.Reviews (OrderItemId, CustomerId, Rating, Comment, Status, CreatedAt, UpdatedAt)
    VALUES (@ItemOrd4_2, @CustomerId, 1, N'Giao hàng hơi chậm so với dự kiến, shipper thái độ không thân thiện. Mong shop đổi đơn vị vận chuyển khác.', 'HIDDEN', DATEADD(HOUR, -12, SYSDATETIME()), DATEADD(HOUR, -12, SYSDATETIME()));
END;

-- Only newly inserted non-cancelled orders consume stock/quota. Re-running is a no-op.
IF EXISTS (SELECT 1 FROM dbo.ProductVariants v JOIN
    (SELECT oi.VariantId,SUM(oi.Quantity) Qty FROM dbo.OrderItems oi JOIN @NewOrders n ON n.Id=oi.OrderId JOIN dbo.Orders o ON o.Id=oi.OrderId WHERE o.OrderStatus<>'CANCELLED' GROUP BY oi.VariantId) x
    ON x.VariantId=v.Id WHERE v.StockQuantity<x.Qty)
    THROW 50002,N'Không đủ tồn kho để nạp đơn demo.',1;
UPDATE v SET StockQuantity=StockQuantity-x.Qty,UpdatedAt=SYSDATETIME()
FROM dbo.ProductVariants v JOIN
    (SELECT oi.VariantId,SUM(oi.Quantity) Qty FROM dbo.OrderItems oi JOIN @NewOrders n ON n.Id=oi.OrderId JOIN dbo.Orders o ON o.Id=oi.OrderId WHERE o.OrderStatus<>'CANCELLED' GROUP BY oi.VariantId) x ON x.VariantId=v.Id;
IF EXISTS (SELECT 1 FROM dbo.Vouchers v JOIN
    (SELECT o.VoucherId,COUNT(*) Uses FROM dbo.Orders o JOIN @NewOrders n ON n.Id=o.Id WHERE o.VoucherId IS NOT NULL AND o.OrderStatus<>'CANCELLED' GROUP BY o.VoucherId) x ON x.VoucherId=v.Id
    WHERE v.UsedCount+x.Uses>v.TotalUsageLimit)
    THROW 50003,N'Không đủ quota voucher để nạp đơn demo.',1;
IF EXISTS (SELECT 1 FROM dbo.Orders o JOIN dbo.Vouchers v ON v.Id=o.VoucherId
    WHERE o.OrderStatus<>'CANCELLED' AND o.VoucherId IN (SELECT VoucherId FROM dbo.Orders WHERE Id IN (SELECT Id FROM @NewOrders))
    GROUP BY o.CustomerId,o.VoucherId,v.PerCustomerLimit HAVING COUNT(*)>v.PerCustomerLimit)
    THROW 50004,N'Đơn demo vượt giới hạn voucher theo khách hàng.',1;
UPDATE v SET UsedCount=UsedCount+x.Uses,UpdatedAt=SYSDATETIME()
FROM dbo.Vouchers v JOIN (SELECT o.VoucherId,COUNT(*) Uses FROM dbo.Orders o JOIN @NewOrders n ON n.Id=o.Id WHERE o.VoucherId IS NOT NULL AND o.OrderStatus<>'CANCELLED' GROUP BY o.VoucherId) x ON x.VoucherId=v.Id;
UPDATE cv SET IsUsed=CASE WHEN x.Uses>0 THEN 1 ELSE 0 END,UsedAt=x.FirstUsed
FROM dbo.CustomerVouchers cv CROSS APPLY
    (SELECT COUNT(*) Uses,MIN(o.CreatedAt) FirstUsed FROM dbo.Orders o WHERE o.CustomerId=cv.CustomerId AND o.VoucherId=cv.VoucherId AND o.OrderStatus<>'CANCELLED') x
WHERE cv.CustomerId=@CustomerId AND cv.VoucherId IN (SELECT VoucherId FROM dbo.Orders WHERE Id IN (SELECT Id FROM @NewOrders));
INSERT INTO dbo.OrderStatusHistories(OrderId,OldStatus,NewStatus,Reason,ChangedByUserId,ChangedAt)
SELECT o.Id,NULL,o.OrderStatus,N'Snapshot trạng thái từ seed demo',NULL,o.CreatedAt FROM dbo.Orders o JOIN @NewOrders n ON n.Id=o.Id;
INSERT INTO dbo.PaymentTransactions(TransactionCode,OrderId,CustomerId,PaymentMethod,PaymentEnvironment,Amount,TransactionStatus,SignatureVerified,FailureReason,PaidAt,CreatedAt,UpdatedAt)
SELECT 'DEMO-'+o.OrderCode,o.Id,o.CustomerId,'VNPAY','SANDBOX',o.TotalAmount,'SUCCESS',NULL,
N'Thanh toán giả lập từ seed demo; không có xác minh hay callback VNPay',o.CompletedAt,o.CreatedAt,o.UpdatedAt
FROM dbo.Orders o JOIN @NewOrders n ON n.Id=o.Id WHERE o.PaymentMethod='VNPAY' AND o.PaymentStatus='PAID';
COMMIT TRANSACTION;
PRINT N'Seed demo hoàn tất; tồn kho, voucher, tổng đơn và audit đã đồng bộ. Chạy lại không trừ tồn lần nữa.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO
