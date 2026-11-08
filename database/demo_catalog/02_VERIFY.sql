USE [PetShopDB];
GO
-- Mọi dòng phải khớp tồn; Difference phải bằng 0.
SELECT * FROM dbo.vw_StockReconciliation WHERE Difference<>0;
-- Mỗi sản phẩm hoạt động phải có đúng một ảnh chính và SKU hoạt động.
SELECT p.Id,p.Name,COUNT(i.Id) PrimaryImages
FROM dbo.Products p LEFT JOIN dbo.ProductImages i ON i.ProductId=p.Id AND i.IsPrimary=1
WHERE p.Status='ACTIVE' GROUP BY p.Id,p.Name HAVING COUNT(i.Id)<>1;
SELECT p.Id,p.Name FROM dbo.Products p WHERE p.Status='ACTIVE'
AND NOT EXISTS(SELECT 1 FROM dbo.ProductVariants v WHERE v.ProductId=p.Id AND v.Status='ACTIVE');
-- Các bản ghi demo phải có đúng một phiếu nhập, không cộng tồn lặp.
SELECT s.ImportCode,s.SupplierName,s.Note,COUNT(i.Id) SkuLines,SUM(i.Quantity) TotalQuantity
FROM dbo.StockImports s JOIN dbo.StockImportItems i ON i.ImportId=s.Id
WHERE s.ImportCode='IMP-CATALOG-DEMO' GROUP BY s.ImportCode,s.SupplierName,s.Note;
SELECT p.Status,COUNT(*) Products FROM dbo.Products p GROUP BY p.Status;
GO
