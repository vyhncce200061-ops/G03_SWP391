-- Run on the existing PetShop database after the voucher soft-delete migration.
-- Archive the legacy campaign while preserving orders, wallet links, and usage.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
DECLARE @RetiredAt datetime2 = SYSDATETIME();

UPDATE dbo.Vouchers
SET IsDeleted = 1, DeletedAt = @RetiredAt, Status = N'INACTIVE', UpdatedAt = @RetiredAt
WHERE UPPER(LTRIM(RTRIM(Code))) = N'FREESHIP30K'
  AND IsDeleted = 0;

COMMIT TRANSACTION;
