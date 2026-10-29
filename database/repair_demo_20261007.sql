-- Applies only to the known demonstration orders/SKUs. Run inside a transaction.
SET XACT_ABORT ON;
IF (SELECT COUNT(*) FROM dbo.Orders WHERE OrderCode IN ('ORD-20261001-001','ORD-20261002-002','ORD-20261003-003','ORD-20261004-004','ORD-20261005-005','ORD-20261006-006','ORD-20261006-007','ORD-20261004-008')) <> 8
    THROW 50001, N'Dữ liệu demo không khớp; cần kiểm tra thủ công trước khi sửa.', 1;
UPDATE o SET SubtotalAmount = x.Subtotal, TotalAmount = x.Subtotal - o.DiscountAmount + o.ShippingFee, UpdatedAt = SYSDATETIME()
FROM dbo.Orders o
CROSS APPLY (SELECT SUM(oi.UnitPrice * oi.Quantity) Subtotal FROM dbo.OrderItems oi WHERE oi.OrderId=o.Id) x
WHERE o.OrderCode='ORD-20261004-004' AND o.SubtotalAmount<>x.Subtotal;
IF EXISTS (SELECT 1 FROM dbo.vw_StockReconciliation WHERE SkuCode IN ('RC-POODLE-500G','RC-POODLE-1500G','WHK-MACKEREL-85G','MEO-LITTER-8L','BALL-RUBBER-S','LEASH-SET-M') AND ExpectedStock<0)
    THROW 50002, N'Tồn kỳ vọng âm; không tự động sửa.', 1;
UPDATE v SET StockQuantity=r.ExpectedStock, UpdatedAt=SYSDATETIME()
FROM dbo.ProductVariants v JOIN dbo.vw_StockReconciliation r ON v.Id=r.VariantId
WHERE r.SkuCode IN ('RC-POODLE-500G','RC-POODLE-1500G','WHK-MACKEREL-85G','MEO-LITTER-8L','BALL-RUBBER-S','LEASH-SET-M') AND r.Difference<>0;
IF EXISTS (SELECT 1 FROM dbo.Vouchers v WHERE v.Code IN ('PETWELCOME10','FREESHIP30K') AND (SELECT COUNT(*) FROM dbo.Orders o WHERE o.VoucherId=v.Id AND o.OrderStatus<>'CANCELLED')>v.TotalUsageLimit)
    THROW 50003, N'Số đơn vượt quota; cần kiểm tra thủ công.', 1;
UPDATE v SET UsedCount=(SELECT COUNT(*) FROM dbo.Orders o WHERE o.VoucherId=v.Id AND o.OrderStatus<>'CANCELLED'), UpdatedAt=SYSDATETIME()
FROM dbo.Vouchers v WHERE v.Code IN ('PETWELCOME10','FREESHIP30K');
UPDATE cv SET IsUsed=CASE WHEN x.Uses>0 THEN 1 ELSE 0 END, UsedAt=x.FirstUsed
FROM dbo.CustomerVouchers cv JOIN dbo.Vouchers v ON v.Id=cv.VoucherId
CROSS APPLY (SELECT COUNT(*) Uses, MIN(o.CreatedAt) FirstUsed FROM dbo.Orders o WHERE o.VoucherId=cv.VoucherId AND o.CustomerId=cv.CustomerId AND o.OrderStatus<>'CANCELLED') x
WHERE v.Code IN ('PETWELCOME10','FREESHIP30K');
INSERT INTO dbo.OrderStatusHistories (OrderId, OldStatus, NewStatus, Reason, ChangedByUserId, ChangedAt)
SELECT o.Id,NULL,o.OrderStatus,N'Bổ sung snapshot trạng thái cho dữ liệu seed demo (không phải lịch sử chuyển trạng thái thực tế)',NULL,o.CreatedAt
FROM dbo.Orders o
WHERE o.OrderCode IN ('ORD-20261001-001','ORD-20261002-002','ORD-20261003-003','ORD-20261004-004','ORD-20261005-005','ORD-20261006-006','ORD-20261006-007','ORD-20261004-008')
AND NOT EXISTS (SELECT 1 FROM dbo.OrderStatusHistories h WHERE h.OrderId=o.Id);
-- VNPay rows are historical simulated demo payments; no provider confirmation is invented.
INSERT INTO dbo.PaymentTransactions (TransactionCode,OrderId,CustomerId,PaymentMethod,PaymentEnvironment,Amount,TransactionStatus,SignatureVerified,FailureReason,PaidAt,CreatedAt,UpdatedAt)
SELECT 'DEMO-'+o.OrderCode,o.Id,o.CustomerId,'VNPAY','SANDBOX',o.TotalAmount,'SUCCESS',NULL,
N'Dữ liệu thanh toán giả lập từ seed demo; không có callback hay xác minh chữ ký VNPay',o.CompletedAt,o.CreatedAt,o.UpdatedAt
FROM dbo.Orders o WHERE o.OrderCode IN ('ORD-20261002-002','ORD-20261004-004') AND o.PaymentStatus='PAID'
AND NOT EXISTS (SELECT 1 FROM dbo.PaymentTransactions p WHERE p.OrderId=o.Id);
