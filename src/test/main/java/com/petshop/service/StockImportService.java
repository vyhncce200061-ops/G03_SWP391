package com.petshop.service;

import com.petshop.entity.ProductVariant;
import com.petshop.entity.StockImport;
import com.petshop.entity.StockImportItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface StockImportService {
    StockImport createStockImport(StockImport stockImport, List<StockImportItem> items, Long staffId);
    Page<StockImport> getStockImports(Pageable pageable);
    StockImport getStockImportDetail(Long id);
    List<ProductVariant> getLowStockVariants();
}
