package com.petshop.controller.staff;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.stock.StockImportEditFormDto;
import com.petshop.dto.stock.StockImportFormDto;
import com.petshop.entity.StockImport;
import com.petshop.entity.StockImportItem;
import com.petshop.repository.ProductVariantRepository;
import com.petshop.service.StockImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
        Pageable pageable = com.petshop.util.PageRequests.bounded(page, size);
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

    // ============ EDIT ITEM FORM ============
    @GetMapping("/items/{itemId}/edit")
    public String editImportItemForm(@PathVariable Long itemId, Model model) {
        StockImportItem item = stockImportService.getImportItemById(itemId);

        StockImportEditFormDto form = StockImportEditFormDto.builder()
                .itemId(item.getId())
                .importId(item.getStockImport().getId())
                .quantity(item.getQuantity())
                .unitCost(item.getUnitCost())
                .build();

        model.addAttribute("editForm", form);
        model.addAttribute("item", item);
        return "staff/stock-import-item-edit";
    }

    // ============ UPDATE IMPORT ITEM ============
    @PostMapping("/items/{itemId}/edit")
    public String updateImportItem(@PathVariable Long itemId,
                                   @AuthenticationPrincipal CustomUserDetails actor,
                                   @Valid @ModelAttribute("editForm") StockImportEditFormDto form,
                                   BindingResult errors, Model model,
                                   RedirectAttributes redirectAttributes) {
        StockImportItem item = stockImportService.getImportItemById(itemId);
        form.setItemId(itemId);
        if (errors.hasErrors()) {
            model.addAttribute("item", item);
            model.addAttribute("errorMessage", errors.getAllErrors().get(0).getDefaultMessage());
            return "staff/stock-import-item-edit";
        }
        try {
            stockImportService.updateImportItem(form, actor.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật mặt hàng thành công!");
        } catch (IllegalArgumentException e) {
            form.setImportId(item.getStockImport().getId());
            model.addAttribute("item", item);
            model.addAttribute("errorMessage", e.getMessage());
            return "staff/stock-import-item-edit";
        }
        return "redirect:/staff/stock-imports/" + item.getStockImport().getId();
    }

    // ============ DETAIL ============
    @GetMapping("/{id}")
    public String viewImportDetail(@PathVariable Long id, Model model) {
        StockImport stockImport = stockImportService.getStockImportDetail(id);
        model.addAttribute("stockImport", stockImport);
        return "staff/stock-import-detail";
    }
}
