-- Migration: Add Color, WeightKg to ProductVariants and DeliveryAttempts to Orders
-- Date: 2026-11-19

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- 1. ProductVariants: Add Color and WeightKg
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.ProductVariants') AND name = 'Color')
BEGIN
    ALTER TABLE dbo.ProductVariants ADD Color NVARCHAR(50) NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.ProductVariants') AND name = 'WeightKg')
BEGIN
    ALTER TABLE dbo.ProductVariants ADD WeightKg DECIMAL(10,2) NULL;
END
GO

-- 2. Orders: Add DeliveryAttempts and FailedDeliveryReason
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Orders') AND name = 'DeliveryAttempts')
BEGIN
    ALTER TABLE dbo.Orders ADD DeliveryAttempts INT NOT NULL CONSTRAINT DF_Orders_DeliveryAttempts DEFAULT 0;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Orders') AND name = 'FailedDeliveryReason')
BEGIN
    ALTER TABLE dbo.Orders ADD FailedDeliveryReason NVARCHAR(1000) NULL;
END
GO

-- 3. Update Check Constraints for OrderStatus (support DELIVERY_FAILED and RETURNED)
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Orders_OrderStatus')
BEGIN
    ALTER TABLE dbo.Orders DROP CONSTRAINT CK_Orders_OrderStatus;
END
ALTER TABLE dbo.Orders ADD CONSTRAINT CK_Orders_OrderStatus CHECK (OrderStatus IN
    ('PENDING_CONFIRMATION','PENDING_PAYMENT','CONFIRMED','PREPARING','SHIPPING','DELIVERY_FAILED','RETURNED','COMPLETED','CANCELLED'));
GO

IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_OSH_NewStatus')
BEGIN
    ALTER TABLE dbo.OrderStatusHistories DROP CONSTRAINT CK_OSH_NewStatus;
END
ALTER TABLE dbo.OrderStatusHistories ADD CONSTRAINT CK_OSH_NewStatus CHECK (NewStatus IN
    ('PENDING_CONFIRMATION','PENDING_PAYMENT','CONFIRMED','PREPARING','SHIPPING','DELIVERY_FAILED','RETURNED','COMPLETED','CANCELLED'));
GO

IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_OSH_OldStatus')
BEGIN
    ALTER TABLE dbo.OrderStatusHistories DROP CONSTRAINT CK_OSH_OldStatus;
END
ALTER TABLE dbo.OrderStatusHistories ADD CONSTRAINT CK_OSH_OldStatus CHECK (OldStatus IS NULL OR OldStatus IN
    ('PENDING_CONFIRMATION','PENDING_PAYMENT','CONFIRMED','PREPARING','SHIPPING','DELIVERY_FAILED','RETURNED','COMPLETED','CANCELLED'));
GO
