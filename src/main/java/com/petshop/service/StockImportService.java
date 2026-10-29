package com.petshop.service;

import com.petshop.dto.stock.StockImportFormDto;
import com.petshop.entity.ProductVariant;
import com.petshop.entity.StockImport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface StockImportService {

    StockImport createStockImport(StockImportFormDto form, Long staffId);  // ← ĐỔI signature
    StockImport createStockImport(StockImport receipt, List<com.petshop.entity.StockImportItem> items, Long staffId);
    Page<StockImport> getStockImports(Pageable pageable);
    StockImport getStockImportDetail(Long id);
    List<ProductVariant> getLowStockVariants();

}
