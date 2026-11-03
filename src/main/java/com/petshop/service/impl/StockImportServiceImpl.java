package com.petshop.service.impl;

import com.petshop.dto.stock.StockImportEditFormDto;
import com.petshop.dto.stock.StockImportFormDto;
import com.petshop.dto.stock.StockImportItemDto;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    public StockImport createStockImport(StockImportFormDto form, Long staffId) {
        // Validate items exist
        if (form.getItems() == null || form.getItems().isEmpty()) {
            throw new IllegalArgumentException("Phiếu nhập kho phải có ít nhất một sản phẩm.");
        }

        // Check for duplicate variants in the same import
        Set<Long> variantIds = new HashSet<>();
        for (StockImportItemDto itemDto : form.getItems()) {
            if (!variantIds.add(itemDto.getVariantId())) {
                throw new IllegalArgumentException("Không được nhập trùng sản phẩm trong cùng một phiếu.");
            }
        }

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại: " + staffId));

        // Create import header
        StockImport stockImport = new StockImport();
        stockImport.setImportCode("IMP-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        stockImport.setSupplierName(form.getSupplierName());
        stockImport.setNote(form.getNote());
        stockImport.setImportedAt(LocalDateTime.now());
        stockImport.setCreatedBy(staff);

        StockImport savedImport = stockImportRepository.save(stockImport);

        // Process each item
        List<StockImportItem> savedItems = new ArrayList<>();
        for (StockImportItemDto itemDto : form.getItems()) {
            ProductVariant variant = variantRepository.findById(itemDto.getVariantId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy SKU với ID: " + itemDto.getVariantId()));

            StockImportItem item = StockImportItem.builder()
                    .stockImport(savedImport)
                    .variant(variant)
                    .quantity(itemDto.getQuantity())
                    .unitCost(itemDto.getUnitCost())
                    .build();
            savedItems.add(stockImportItemRepository.save(item));

            // Increase stock quantity
            variantRepository.restoreStock(variant.getId(), itemDto.getQuantity());
        }

        savedImport.setItems(savedItems);
        log.info("Stock import {} created by staff {} with {} items",
                savedImport.getImportCode(), staffId, savedItems.size());
        return savedImport;
    }

    @Override
    @Transactional
    public void updateImportItem(StockImportEditFormDto form) {
        StockImportItem item = stockImportItemRepository.findById(form.getItemId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mặt hàng trong phiếu nhập"));

        ProductVariant variant = item.getVariant();
        int oldQuantity = item.getQuantity();
        int newQuantity = form.getQuantity();

        // Calculate difference (Delta)
        int delta = newQuantity - oldQuantity;

        // If decreasing quantity, check if current stock is enough
        if (delta < 0) {
            int currentStock = variant.getStockQuantity();
            if (currentStock + delta < 0) {
                throw new IllegalArgumentException("Không thể giảm số lượng! Tồn kho hiện tại của SKU '"
                        + variant.getSkuCode() + "' chỉ còn " + currentStock + ", không đủ để trừ đi " + Math.abs(delta) + ".");
            }
        }

        // Update stock quantity if there is a change
        if (delta != 0) {
            variantRepository.restoreStock(variant.getId(), delta);
        }

        // Update item quantity and unit cost
        item.setQuantity(newQuantity);
        item.setUnitCost(form.getUnitCost());

        stockImportItemRepository.save(item);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockImport> getStockImports(Pageable pageable) {
        return stockImportRepository.findAllByOrderByImportedAtDesc(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public StockImport getStockImportDetail(Long id) {
        StockImport stockImport = stockImportRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu nhập kho: " + id));

        // Force initialization of lazy collections while the session is open
        if (stockImport.getItems() != null) {
            stockImport.getItems().size();

            // Also force initialization of nested properties used in Thymeleaf
            stockImport.getItems().forEach(item -> {
                if (item.getVariant() != null) {
                    item.getVariant().getSkuCode();
                    if (item.getVariant().getProduct() != null) {
                        item.getVariant().getProduct().getName();
                    }
                }
            });
        }

        return stockImport;
    }
    @Override
    @Transactional(readOnly = true)
    public StockImportItem getImportItemById(Long itemId) {
        StockImportItem item = stockImportItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mặt hàng"));

        // Force initialization of lazy-loaded properties while the session is open
        if (item.getVariant() != null) {
            item.getVariant().getSkuCode();
            if (item.getVariant().getProduct() != null) {
                item.getVariant().getProduct().getName();
            }
        }
        if (item.getStockImport() != null) {
            item.getStockImport().getId();
        }

        return item;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariant> getLowStockVariants() {
        return variantRepository.findLowStockVariants();
    }
}