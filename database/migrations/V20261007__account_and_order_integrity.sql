-- Incremental migration. Does not reset or delete tables/data.
SET XACT_ABORT ON;
IF COL_LENGTH('dbo.Users', 'MustChangePassword') IS NULL
    ALTER TABLE dbo.Users ADD MustChangePassword BIT NOT NULL CONSTRAINT DF_Users_MustChangePassword DEFAULT 0;
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Orders_CompletedPaid')
    ALTER TABLE dbo.Orders WITH CHECK ADD CONSTRAINT CK_Orders_CompletedPaid CHECK (OrderStatus <> 'COMPLETED' OR PaymentStatus = 'PAID');
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.Orders') AND name = 'IX_Orders_CompletedAt')
    CREATE INDEX IX_Orders_CompletedAt ON dbo.Orders(CompletedAt) INCLUDE (SubtotalAmount, DiscountAmount, ShippingFee, TotalAmount) WHERE OrderStatus = 'COMPLETED';
