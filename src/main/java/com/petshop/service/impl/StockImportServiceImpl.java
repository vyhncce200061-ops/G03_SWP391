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
        if (form == null) throw new IllegalArgumentException("Phiếu nhập không hợp lệ.");
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

        if (!"ACTIVE".equals(staff.getStatus()) || !java.util.Set.of("ADMIN", "STAFF").contains(staff.getRole().getCode())) {
            throw new IllegalArgumentException("Tài khoản không có quyền nhập kho.");
        }
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
    @Override
    @Transactional(readOnly = true)
    public StockImportItem getImportItemById(Long itemId) {
        var item = stockImportItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mặt hàng trong phiếu nhập."));
        item.getVariant().getProduct().getName();
        item.getStockImport().getImportCode();
        return item;
    }

    @Override
    @Transactional
    public void updateImportItem(com.petshop.dto.stock.StockImportEditFormDto form, Long staffId) {
        if (form == null || form.getItemId() == null || !validator.validate(form).isEmpty()) {
            throw new IllegalArgumentException("Số lượng (1–10.000), đơn giá và phiếu nhập phải hợp lệ.");
        }
        var staff = userRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên."));
        if (!"ACTIVE".equals(staff.getStatus()) || !java.util.Set.of("STAFF", "ADMIN").contains(staff.getRole().getCode())) {
            throw new IllegalArgumentException("Tài khoản không có quyền nhập kho.");
        }
        var item = stockImportItemRepository.findByIdForUpdate(form.getItemId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mặt hàng trong phiếu nhập."));
        entityManager.refresh(item, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!item.getStockImport().getId().equals(form.getImportId())) {
            throw new IllegalArgumentException("Mặt hàng không thuộc phiếu nhập đã chọn.");
        }
        var variant = variantRepository.findByIdForUpdate(item.getVariant().getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy SKU nhập kho."));
        entityManager.refresh(variant, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        int delta = form.getQuantity() - item.getQuantity();
        long remaining = (long) variant.getStockQuantity() + delta;
        if (remaining < 0 || remaining > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Không thể sửa số lượng: tồn kho không đủ để giảm hoặc vượt giới hạn.");
        }
        if (delta != 0) {
            if (variantRepository.restoreStock(variant.getId(), delta) != 1) throw new IllegalStateException("Chưa cập nhật được tồn kho.");
            entityManager.refresh(variant);
        }
        item.setQuantity(form.getQuantity());
        item.setUnitCost(form.getUnitCost());
        stockImportItemRepository.saveAndFlush(item);
    }
}
