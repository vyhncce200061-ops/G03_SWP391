-- Read-only verification. Select the project database in SSMS.
SELECT DB_NAME() AS SelectedDatabase;
SELECT name, TYPE_NAME(system_type_id) AS DataType, is_nullable
FROM sys.columns WHERE object_id=OBJECT_ID('dbo.Orders') AND name='StockDeducted';
-- One BIT NOT NULL column.
SELECT name,definition,is_disabled,is_not_trusted FROM sys.check_constraints
WHERE parent_object_id=OBJECT_ID('dbo.Orders') AND name='CK_Orders_PendingConfirm';
-- Enabled/trusted, permits COD or paid VNPay at PENDING_CONFIRMATION.
SELECT OBJECT_DEFINITION(OBJECT_ID('dbo.vw_StockReconciliation')) AS StockViewDefinition;
-- The existing view must filter Orders.StockDeducted=1.
SELECT OrderStatus,PaymentMethod,PaymentStatus,StockDeducted,COUNT(*) AS NumberOfOrders
FROM dbo.Orders GROUP BY OrderStatus,PaymentMethod,PaymentStatus,StockDeducted;
-- Do not force all pending orders to one flag: legacy and new orders differ.
SELECT * FROM dbo.vw_StockReconciliation WHERE Difference<>0;
-- Inspect any pre-existing stock/import discrepancy; never auto-adjust stock here.
