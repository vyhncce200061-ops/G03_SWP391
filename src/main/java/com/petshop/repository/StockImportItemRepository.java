package com.petshop.repository;

import com.petshop.entity.StockImportItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockImportItemRepository extends JpaRepository<StockImportItem, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT i FROM StockImportItem i WHERE i.id=:id")
    java.util.Optional<StockImportItem> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    List<StockImportItem> findByStockImportId(Long stockImportId);
}
