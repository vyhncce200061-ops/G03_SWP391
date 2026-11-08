-- Required by feature/wishlist-my_vourcher: allocate COD stock on confirmation,
-- and VNPay stock after verified payment. Select the project DB and stop the app first.
-- Back up the database before running. This script never changes stock quantities,
-- order totals, payment records, statuses, or existing StockDeducted values.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
IF DB_NAME() IN ('master', 'model', 'msdb', 'tempdb')
    THROW 50001, 'Select the existing PetShop project database.', 1;
IF OBJECT_ID(N'dbo.Orders', N'U') IS NULL
    THROW 50002, 'The selected database does not contain dbo.Orders.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    IF COL_LENGTH(N'dbo.Orders', N'StockDeducted') IS NULL
    BEGIN
        EXEC(N'ALTER TABLE dbo.Orders ADD StockDeducted BIT NOT NULL
            CONSTRAINT DF_Orders_StockDeducted DEFAULT 0 WITH VALUES;');
        -- Earlier checkout code deducted inventory for every non-cancelled order.
        -- Mark those allocations once, so confirming or cancelling a legacy order
        -- cannot deduct twice or lose its original restoration. No inventory is moved.
        EXEC(N'UPDATE dbo.Orders SET StockDeducted = 1 WHERE OrderStatus <> ''CANCELLED'';');
    END;
    IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.Orders')
        AND name = N'StockDeducted' AND (system_type_id <> 104 OR is_nullable <> 0))
        THROW 50003, 'Orders.StockDeducted exists with an incompatible type or nullability; changes rolled back.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.default_constraints dc JOIN sys.columns c
        ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
        WHERE c.object_id = OBJECT_ID(N'dbo.Orders') AND c.name = N'StockDeducted')
        EXEC(N'ALTER TABLE dbo.Orders ADD CONSTRAINT DF_Orders_StockDeducted DEFAULT 0 FOR StockDeducted;');

    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.Orders')
        AND name = N'CK_Orders_PendingConfirm')
        EXEC(N'ALTER TABLE dbo.Orders DROP CONSTRAINT CK_Orders_PendingConfirm;');
    EXEC(N'ALTER TABLE dbo.Orders WITH CHECK ADD CONSTRAINT CK_Orders_PendingConfirm CHECK (
        OrderStatus <> ''PENDING_CONFIRMATION''
        OR PaymentMethod = ''COD''
        OR (PaymentMethod = ''VNPAY'' AND PaymentStatus = ''PAID'')
    );');
    -- Keep the existing SQL stock report consistent with the application's report.
    IF OBJECT_ID(N'dbo.vw_StockReconciliation', N'V') IS NOT NULL
        EXEC(N'ALTER VIEW dbo.vw_StockReconciliation AS
            SELECT v.Id AS VariantId, v.SkuCode, v.StockQuantity,
                   ISNULL(i.Imported, 0) AS TotalImported,
                   ISNULL(s.Sold, 0) AS TotalSold,
                   ISNULL(i.Imported, 0) - ISNULL(s.Sold, 0) AS ExpectedStock,
                   v.StockQuantity - (ISNULL(i.Imported, 0) - ISNULL(s.Sold, 0)) AS Difference
            FROM dbo.ProductVariants v
            OUTER APPLY (SELECT SUM(Quantity) AS Imported FROM dbo.StockImportItems WHERE VariantId = v.Id) i
            OUTER APPLY (SELECT SUM(oi.Quantity) AS Sold FROM dbo.OrderItems oi
                JOIN dbo.Orders o ON o.Id = oi.OrderId
                WHERE oi.VariantId = v.Id AND o.StockDeducted = 1 AND o.OrderStatus <> ''CANCELLED'') s;');
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
