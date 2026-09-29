package com.petshop.repository;

import com.petshop.entity.StockImport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockImportRepository extends JpaRepository<StockImport, Long> {
    Optional<StockImport> findByImportCode(String importCode);
    Page<StockImport> findAllByOrderByImportedAtDesc(Pageable pageable);

    @Query("SELECT si FROM StockImport si " +
           "LEFT JOIN FETCH si.items i " +
           "LEFT JOIN FETCH i.variant v " +
           "LEFT JOIN FETCH v.product " +
           "WHERE si.id = :id")
    Optional<StockImport> findByIdWithDetails(@Param("id") Long id);
}
