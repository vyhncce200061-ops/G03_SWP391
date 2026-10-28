-- Approved scope 2026-10-29: Admin replies to product Feedback; keep hide/show.
-- Select your existing PetShop database. Run before starting the new code. Safe to rerun.
-- Adds only the shop reply; no new Feedback table, account deletion, or data rewrite.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF OBJECT_ID(N'dbo.Reviews', N'U') IS NULL OR OBJECT_ID(N'dbo.Users', N'U') IS NULL
        THROW 50001, 'Select the existing PetShop database before running this migration.', 1;
    IF COL_LENGTH('dbo.Reviews','ReplyText') IS NULL
        ALTER TABLE dbo.Reviews ADD ReplyText NVARCHAR(1000) NULL;
    IF COL_LENGTH('dbo.Reviews','RepliedByUserId') IS NULL
        ALTER TABLE dbo.Reviews ADD RepliedByUserId BIGINT NULL;
    IF COL_LENGTH('dbo.Reviews','RepliedAt') IS NULL
        ALTER TABLE dbo.Reviews ADD RepliedAt DATETIME2(6) NULL;
    IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('dbo.Reviews') AND
        ((name='ReplyText' AND (TYPE_NAME(system_type_id)<>'nvarchar' OR max_length<>2000 OR is_nullable<>1)) OR
         (name='RepliedByUserId' AND (TYPE_NAME(system_type_id)<>'bigint' OR is_nullable<>1)) OR
         (name='RepliedAt' AND (TYPE_NAME(system_type_id)<>'datetime2' OR scale<>6 OR is_nullable<>1))))
        THROW 50002, 'Existing reply columns have incompatible types; no changes committed.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name='FK_Reviews_RepliedByUser')
        EXEC(N'ALTER TABLE dbo.Reviews WITH CHECK ADD CONSTRAINT FK_Reviews_RepliedByUser FOREIGN KEY (RepliedByUserId) REFERENCES dbo.Users(Id)');
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='CK_Reviews_ReplyComplete')
        EXEC(N'ALTER TABLE dbo.Reviews WITH CHECK ADD CONSTRAINT CK_Reviews_ReplyComplete CHECK (
            (ReplyText IS NULL AND RepliedByUserId IS NULL AND RepliedAt IS NULL) OR
            (ReplyText IS NOT NULL AND LEN(LTRIM(RTRIM(ReplyText))) > 0 AND RepliedByUserId IS NOT NULL AND RepliedAt IS NOT NULL))');
    COMMIT;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK;
    THROW;
END CATCH;
