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
    private final jakarta.validation.Validator validator;
    private final jakarta.persistence.EntityManager entityManager;

    @Override
    @Transactional
    public StockImport createStockImport(com.petshop.dto.stock.StockImportFormDto form, Long staffId) {
        var violations = validator.validate(form);
        if (!violations.isEmpty()) throw new IllegalArgumentException(violations.iterator().next().getMessage());
        var items = form.getItems().stream().map(line -> StockImportItem.builder()
                .variant(ProductVariant.builder().id(line.getVariantId()).build())
                .quantity(line.getQuantity()).unitCost(line.getUnitCost()).build()).toList();
        var receipt = StockImport.builder().supplierName(form.getSupplierName().trim()).note(form.getNote()).build();
        return createStockImport(receipt, items, staffId);
    }

    @Override
    @Transactional
    public StockImport createStockImport(StockImport stockImport, List<StockImportItem> items, Long staffId) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Phiếu nhập phải có sản phẩm.");
        if (stockImport == null || stockImport.getId() != null) throw new IllegalArgumentException("Phiếu nhập mới không được ghi đè phiếu đã có.");
        var ids = new java.util.HashSet<Long>();
        for (StockImportItem item : items) {
            if (item.getVariant() == null || item.getVariant().getId() == null || item.getQuantity() == null || item.getQuantity() < 1
                    || item.getUnitCost() == null || item.getUnitCost().signum() < 0) {
                throw new IllegalArgumentException("Sản phẩm, số lượng và giá nhập không hợp lệ.");
            }
            if (!ids.add(item.getVariant().getId())) throw new IllegalArgumentException("Không được nhập trùng SKU trong cùng một phiếu.");
        }
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại: " + staffId));

        if (stockImport.getImportCode() == null || stockImport.getImportCode().isBlank()) {
            stockImport.setImportCode("IMP-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        }

        stockImport.setCreatedBy(staff);
        stockImport.setImportedAt(LocalDateTime.now());
        StockImport savedImport = stockImportRepository.save(stockImport);

        for (StockImportItem item : items.stream().sorted(java.util.Comparator.comparing(i -> i.getVariant().getId())).toList()) {
            ProductVariant variant = variantRepository.findById(item.getVariant().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy SKU nhập kho."));
            item.setVariant(variant);
            item.setStockImport(savedImport);
            stockImportItemRepository.save(item);

            // Automatically increase variant stock
            variantRepository.restoreStock(item.getVariant().getId(), item.getQuantity());
            entityManager.refresh(variant);
            savedImport.getItems().add(item);
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
