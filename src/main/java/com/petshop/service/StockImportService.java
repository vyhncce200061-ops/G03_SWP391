package com.petshop.service;

import com.petshop.dto.stock.StockImportEditFormDto;
import com.petshop.dto.stock.StockImportFormDto;
import com.petshop.entity.ProductVariant;
import com.petshop.entity.StockImport;
import com.petshop.entity.StockImportItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface StockImportService {

    StockImport createStockImport(StockImportFormDto form, Long staffId);
    // Compatibility overload retained for existing integration callers.
    StockImport createStockImport(StockImport receipt, List<StockImportItem> items, Long staffId);
    void updateImportItem(StockImportEditFormDto form, Long staffId);
    Page<StockImport> getStockImports(Pageable pageable);
    StockImport getStockImportDetail(Long id);
    StockImportItem getImportItemById(Long itemId);
    List<ProductVariant> getLowStockVariants();

}
