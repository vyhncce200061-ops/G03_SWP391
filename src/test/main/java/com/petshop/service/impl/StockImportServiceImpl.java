package com.petshop.service.impl;

import com.petshop.entity.ProductVariant;
import com.petshop.entity.StockImport;
import com.petshop.entity.StockImportItem;
import com.petshop.entity.User;
import com.petshop.repository.ProductVariantRepository;
import com.petshop.repository.StockImportItemRepository;
import com.petshop.repository.StockImportRepository;
import com.petshop.repository.UserRepository;
import com.petshop.service.StockImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockImportServiceImpl implements StockImportService {

    private final StockImportRepository stockImportRepository;
    private final StockImportItemRepository stockImportItemRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public StockImport createStockImport(StockImport stockImport, List<StockImportItem> items, Long staffId) {
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại: " + staffId));

        if (stockImport.getImportCode() == null || stockImport.getImportCode().isBlank()) {
            stockImport.setImportCode("IMP-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        }

        stockImport.setCreatedBy(staff);
        stockImport.setImportedAt(LocalDateTime.now());
        StockImport savedImport = stockImportRepository.save(stockImport);

        for (StockImportItem item : items) {
            item.setStockImport(savedImport);
            stockImportItemRepository.save(item);

            // Automatically increase variant stock
            variantRepository.restoreStock(item.getVariant().getId(), item.getQuantity());
        }

        log.info("Stock import {} created by staff {}", savedImport.getImportCode(), staffId);
        return savedImport;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockImport> getStockImports(Pageable pageable) {
        return stockImportRepository.findAllByOrderByImportedAtDesc(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public StockImport getStockImportDetail(Long id) {
        return stockImportRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu nhập kho: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariant> getLowStockVariants() {
        return variantRepository.findLowStockVariants();
    }
}
