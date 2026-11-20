-- Migration: Create Banners table for homepage slider management
-- Date: 2026-11-20

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID('dbo.Banners', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Banners (
        Id          INT IDENTITY(1,1) NOT NULL,
        Title       NVARCHAR(150)     NOT NULL,
        Subtitle    NVARCHAR(300)     NULL,
        ImageUrl    NVARCHAR(1000)    NOT NULL,
        LinkUrl     NVARCHAR(500)     NULL,
        ButtonText  NVARCHAR(50)      NULL,
        SortOrder   INT               NOT NULL CONSTRAINT DF_Banners_SortOrder DEFAULT 0,
        IsActive    BIT               NOT NULL CONSTRAINT DF_Banners_IsActive DEFAULT 1,
        StartAt     DATETIME2(0)      NULL,
        EndAt       DATETIME2(0)      NULL,
        CreatedAt   DATETIME2(0)      NOT NULL CONSTRAINT DF_Banners_CreatedAt DEFAULT SYSDATETIME(),
        UpdatedAt   DATETIME2(0)      NOT NULL CONSTRAINT DF_Banners_UpdatedAt DEFAULT SYSDATETIME(),
        CONSTRAINT PK_Banners PRIMARY KEY (Id),
        CONSTRAINT CK_Banners_SortOrder CHECK (SortOrder >= 0 AND SortOrder <= 9999),
        CONSTRAINT CK_Banners_Schedule CHECK (StartAt IS NULL OR EndAt IS NULL OR EndAt > StartAt)
    );

    CREATE INDEX IX_Banners_Active_Sort ON dbo.Banners(IsActive, SortOrder);

    -- Sample initial banners for homepage
    INSERT INTO dbo.Banners (Title, Subtitle, ImageUrl, LinkUrl, ButtonText, SortOrder, IsActive)
    VALUES 
    (N'Siêu Ưu Đãi Thức Ăn Thú Cưng', N'Bộ sưu tập hạt và pate cao cấp cho chó mèo - Giảm giá đến 30%', N'/images/hero-pets.svg', N'/products', N'Mua ngay', 1, 1),
    (N'Phụ Kiện & Đồ Chơi Thông Minh', N'Đồ chơi gặm, vòng cổ và đệm êm ái giúp bé cưng luôn vui vẻ', N'/images/hero-pets.svg', N'/products', N'Khám phá ngay', 2, 1);
END
GO
