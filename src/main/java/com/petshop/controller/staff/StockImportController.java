package com.petshop.controller.staff;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.stock.StockImportFormDto;
import com.petshop.entity.StockImport;
import com.petshop.repository.ProductVariantRepository;
import com.petshop.service.StockImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/staff/stock-imports")
@RequiredArgsConstructor
public class StockImportController {

    private final StockImportService stockImportService;
    private final ProductVariantRepository variantRepository;

    // ============ LIST IMPORTS ============
    @GetMapping
    public String listImports(@RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "10") int size,
                              Model model) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)));
        Page<StockImport> imports = stockImportService.getStockImports(pageable);
        model.addAttribute("imports", imports);
        return "staff/stock-import-list";
    }

    // ============ CREATE FORM ============
    @GetMapping("/create")
    public String createImportForm(Model model) {
        if (!model.containsAttribute("stockImportForm")) {
            StockImportFormDto form = new StockImportFormDto();
            form.setSupplierName("");
            model.addAttribute("stockImportForm", form);
        }
        model.addAttribute("variants", variantRepository.findAllActiveWithProduct());
        model.addAttribute("importCode",
                "IMP-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        return "staff/stock-import-form";
    }

    // ============ SAVE IMPORT ============
    @PostMapping("/create")
    public String saveImport(@AuthenticationPrincipal CustomUserDetails userDetails,
                             @Valid @ModelAttribute("stockImportForm") StockImportFormDto form,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes) {

        // If validation fails → return to view
        if (bindingResult.hasErrors()) {
            model.addAttribute("variants", variantRepository.findAllActiveWithProduct());
            model.addAttribute("importCode",
                    "IMP-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
            return "staff/stock-import-form";
        }

        try {
            stockImportService.createStockImport(form, userDetails.getId());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Tạo phiếu nhập kho thành công và đã cập nhật tồn kho!");
            return "redirect:/staff/stock-imports";
        } catch (IllegalArgumentException e) {
            model.addAttribute("variants", variantRepository.findAllActiveWithProduct());
            model.addAttribute("importCode",
                    "IMP-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
            model.addAttribute("errorMessage", e.getMessage());
            return "staff/stock-import-form";
        }
    }

    // ============ DETAIL ============
    @GetMapping("/{id}")
    public String viewImportDetail(@PathVariable Long id, Model model) {
        StockImport stockImport = stockImportService.getStockImportDetail(id);
        model.addAttribute("stockImport", stockImport);
        return "staff/stock-import-detail";
    }
}