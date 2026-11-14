SET XACT_ABORT ON;
BEGIN TRANSACTION;
UPDATE b SET LogoUrl = s.LogoUrl, UpdatedAt = SYSDATETIME()
FROM dbo.Brands b
JOIN (VALUES
    (N'Royal Canin', N'/images/brands/royal-canin.png'),
    (N'Whiskas', N'/images/brands/whiskas.png'),
    (N'Me-O', N'/images/brands/me-o.png'),
    (N'Pedigree', N'/images/brands/pedigree.jpg'),
    (N'SmartHeart', N'/images/brands/smartheart.png'),
    (N'INABA', N'/images/brands/inaba.jpg'),
    (N'CIAO', N'/images/brands/ciao.jpg'),
    (N'MEOWOW', N'/images/brands/meowow.jpg'),
    (N'BOBO', N'/images/brands/bobo.png'),
    (N'DELE', N'/images/brands/dele.jpg')
) s(Name, LogoUrl) ON b.Name = s.Name
WHERE NULLIF(LTRIM(RTRIM(b.LogoUrl)), '') IS NULL;
COMMIT;
