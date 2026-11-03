package com.petshop.controller.admin;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.catalog.ProductFormDto;
import com.petshop.entity.enums.ProductStatus;
import com.petshop.service.CatalogService;
import com.petshop.service.ProductManageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class AdminProductController {
    private final ProductManageService products;
    private final CatalogService catalog;

    @ModelAttribute("productBasePath")
    public String productBasePath() { return "/admin/products"; }

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Integer categoryId,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size, Model model) {
        model.addAttribute("products", products.searchProducts(keyword, categoryId,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)))));
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("categories", catalog.getAllCategories());
        return "staff/product-list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("productForm", new ProductFormDto());
        return form(model);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("productForm", ProductFormDto.fromEntity(products.findById(id)));
        return form(model);
    }

    @PostMapping("/create")
    public String create(@AuthenticationPrincipal CustomUserDetails principal,
                         @Valid @ModelAttribute("productForm") ProductFormDto dto,
                         BindingResult errors, Model model, RedirectAttributes redirect) {
        dto.setId(null);
        if (errors.hasErrors()) return form(model);
        try {
            products.createProduct(dto, principal.getId());
            redirect.addFlashAttribute("successMessage", "Tạo sản phẩm thành công!");
            return "redirect:/admin/products";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return form(model);
        }
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                         @Valid @ModelAttribute("productForm") ProductFormDto dto,
                         BindingResult errors, Model model, RedirectAttributes redirect) {
        dto.setId(id);
        if (errors.hasErrors()) return form(model);
        try {
            products.updateProduct(id, dto, principal.getId());
            redirect.addFlashAttribute("successMessage", "Cập nhật sản phẩm thành công!");
            return "redirect:/admin/products";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return form(model);
        }
    }

    @PostMapping("/{id}/status")
    public String status(@PathVariable Long id, @RequestParam ProductStatus status, RedirectAttributes redirect) {
        products.toggleProductStatus(id, status);
        redirect.addFlashAttribute("successMessage", "Cập nhật trạng thái thành công!");
        return "redirect:/admin/products";
    }

    private String form(Model model) {
        model.addAttribute("categories", catalog.getAllCategories());
        model.addAttribute("brands", catalog.getAllBrands());
        return "staff/product-form";
    }
}
