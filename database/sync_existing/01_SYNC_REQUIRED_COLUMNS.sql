-- Chay tren database du an da chon trong SSMS. Khong dung bootstrap/reset.
-- Chi them 3 cot dang duoc code su dung; khong xoa hoac thay noi dung dong du lieu.
SET XACT_ABORT ON;
SET NOCOUNT ON;
IF DB_NAME() IN ('master','model','msdb','tempdb')
    THROW 50001, N'Hay chon database PetShop cua du an, khong chay tren database he thong.', 1;
IF OBJECT_ID(N'dbo.Users', N'U') IS NULL OR OBJECT_ID(N'dbo.Vouchers', N'U') IS NULL
    THROW 50002, N'Database khong co bang Users/Vouchers cua du an. Dung cap nhat.', 1;
BEGIN TRY
    BEGIN TRANSACTION;
    IF COL_LENGTH(N'dbo.Users', N'MustChangePassword') IS NULL
        EXEC(N'ALTER TABLE dbo.Users ADD MustChangePassword BIT NOT NULL CONSTRAINT DF_Users_MustChangePassword DEFAULT 0 WITH VALUES;');
    IF COL_LENGTH(N'dbo.Vouchers', N'IsDeleted') IS NULL
        EXEC(N'ALTER TABLE dbo.Vouchers ADD IsDeleted BIT NOT NULL CONSTRAINT DF_Vouchers_IsDeleted DEFAULT 0 WITH VALUES;');
    IF COL_LENGTH(N'dbo.Vouchers', N'DeletedAt') IS NULL
        EXEC(N'ALTER TABLE dbo.Vouchers ADD DeletedAt DATETIME2(0) NULL;');
    IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.Users') AND name=N'MustChangePassword' AND (system_type_id<>104 OR is_nullable<>0))
        THROW 50003, N'Users.MustChangePassword da ton tai nhung sai kieu/nullability. Can doi chieu truoc khi sua.', 1;
    IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.Vouchers') AND name=N'IsDeleted' AND (system_type_id<>104 OR is_nullable<>0))
        THROW 50004, N'Vouchers.IsDeleted da ton tai nhung sai kieu/nullability. Can doi chieu truoc khi sua.', 1;
    IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.Vouchers') AND name=N'DeletedAt' AND (system_type_id<>42 OR is_nullable<>1))
        THROW 50005, N'Vouchers.DeletedAt da ton tai nhung sai kieu/nullability. Can doi chieu truoc khi sua.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.Vouchers') AND name=N'CK_Vouchers_SoftDelete')
        EXEC(N'ALTER TABLE dbo.Vouchers WITH CHECK ADD CONSTRAINT CK_Vouchers_SoftDelete CHECK ((IsDeleted=0 AND DeletedAt IS NULL) OR (IsDeleted=1 AND DeletedAt IS NOT NULL AND Status=''INACTIVE''));');
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
SELECT OBJECT_NAME(object_id) AS TableName, name AS ColumnName, TYPE_NAME(user_type_id) AS DataType, is_nullable
FROM sys.columns
WHERE (object_id=OBJECT_ID(N'dbo.Users') AND name=N'MustChangePassword')
   OR (object_id=OBJECT_ID(N'dbo.Vouchers') AND name IN (N'IsDeleted',N'DeletedAt'))
ORDER BY TableName, ColumnName;
