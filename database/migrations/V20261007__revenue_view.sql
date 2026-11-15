CREATE OR ALTER VIEW dbo.vw_RevenueByDay AS
SELECT CAST(CompletedAt AS DATE) AS RevenueDate,
       COUNT(*) AS OrderCount,
       SUM(SubtotalAmount) AS GoodsRevenue,
       SUM(DiscountAmount) AS TotalDiscount,
       SUM(SubtotalAmount - DiscountAmount) AS TotalRevenue,
       SUM(ShippingFee) AS TotalShipping,
       SUM(TotalAmount) AS TotalCollected
FROM dbo.Orders WHERE OrderStatus='COMPLETED' GROUP BY CAST(CompletedAt AS DATE);
