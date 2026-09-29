package com.petshop.repository;

import com.petshop.entity.StockImportItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockImportItemRepository extends JpaRepository<StockImportItem, Long> {
    List<StockImportItem> findByStockImportId(Long stockImportId);
}
