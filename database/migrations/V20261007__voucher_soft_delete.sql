-- Incremental migration: preserves existing vouchers, wallets and orders.
-- Run each GO-separated batch in order. Existing voucher codes remain unique.
SET XACT_ABORT ON;
IF COL_LENGTH('dbo.Vouchers', 'IsDeleted') IS NULL
    ALTER TABLE dbo.Vouchers ADD IsDeleted BIT NOT NULL CONSTRAINT DF_Vouchers_IsDeleted DEFAULT 0;
GO
IF COL_LENGTH('dbo.Vouchers', 'DeletedAt') IS NULL
    ALTER TABLE dbo.Vouchers ADD DeletedAt DATETIME2(0) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('dbo.Vouchers') AND name = 'CK_Vouchers_SoftDelete')
    ALTER TABLE dbo.Vouchers WITH CHECK ADD CONSTRAINT CK_Vouchers_SoftDelete CHECK
        ((IsDeleted = 0 AND DeletedAt IS NULL) OR (IsDeleted = 1 AND DeletedAt IS NOT NULL AND Status = 'INACTIVE'));
GO
