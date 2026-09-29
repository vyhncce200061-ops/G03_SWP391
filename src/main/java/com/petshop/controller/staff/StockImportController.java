package com.petshop.controller.staff;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.entity.ProductVariant;
import com.petshop.entity.StockImport;
import com.petshop.entity.StockImportItem;
import com.petshop.repository.ProductVariantRepository;
import com.petshop.service.StockImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/staff/stock-imports")
@RequiredArgsConstructor
public class StockImportController {

    private final StockImportService stockImportService;
    private final ProductVariantRepository variantRepository;

    @GetMapping
    public String listImports(@RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "10") int size,
                              Model model) {
        Pageable pageable = PageRequest.of(page, size);
        Page<StockImport> imports = stockImportService.getStockImports(pageable);
        model.addAttribute("imports", imports);
        return "staff/stock-import-list";
    }

    @GetMapping("/create")
    public String createImportForm(Model model) {
        model.addAttribute("stockImport", new StockImport());
        model.addAttribute("variants", variantRepository.findAll());
        return "staff/stock-import-form";
    }

    @PostMapping("/create")
    public String saveImport(@AuthenticationPrincipal CustomUserDetails userDetails,
                             @ModelAttribute StockImport stockImport,
                             @RequestParam Long variantId,
                             @RequestParam Integer quantity,
                             @RequestParam BigDecimal unitCost,
                             RedirectAttributes redirectAttributes) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy biến thể"));

        StockImportItem item = StockImportItem.builder()
                .variant(variant)
                .quantity(quantity)
                .unitCost(unitCost)
                .build();

        List<StockImportItem> items = new ArrayList<>();
        items.add(item);

        stockImportService.createStockImport(stockImport, items, userDetails.getId());
        redirectAttributes.addFlashAttribute("successMessage", "Tạo phiếu nhập kho thành công và đã cập nhật tồn kho!");
        return "redirect:/staff/stock-imports";
    }

    @GetMapping("/{id}")
    public String viewImportDetail(@PathVariable Long id, Model model) {
        StockImport stockImport = stockImportService.getStockImportDetail(id);
        model.addAttribute("stockImport", stockImport);
        return "staff/stock-import-detail";
    }

    @GetMapping("/low-stock")
    public String lowStockAlerts(Model model) {
        model.addAttribute("lowStockVariants", stockImportService.getLowStockVariants());
        return "staff/low-stock";
    }
}
