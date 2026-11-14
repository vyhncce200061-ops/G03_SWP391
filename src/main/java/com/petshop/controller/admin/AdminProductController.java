package com.petshop.controller.admin;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.catalog.ProductFormDto;
import com.petshop.entity.enums.ProductStatus;
import com.petshop.service.CatalogService;
import com.petshop.service.CategoryManageService;
import com.petshop.service.ProductManageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    private final CategoryManageService categories;

    @ModelAttribute("productBasePath")
    public String productBasePath() { return "/admin/products"; }

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Integer categoryId,
                       @RequestParam(required = false) ProductStatus status,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size, Model model) {
        model.addAttribute("products", products.searchProducts(keyword, categoryId, status,
                com.petshop.util.PageRequests.bounded(page, size)));
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedStatus", status);
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

    @PostMapping("/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirect) {
        products.deleteProduct(id);
        redirect.addFlashAttribute("successMessage", "Đã xóa mềm sản phẩm thành công! Sản phẩm đã chuyển sang trạng thái tạm ẩn.");
        return "redirect:/admin/products";
    }

    @PostMapping("/{id}/restore")
    public String restoreProduct(@PathVariable Long id, RedirectAttributes redirect) {
        products.toggleProductStatus(id, ProductStatus.ACTIVE);
        redirect.addFlashAttribute("successMessage", "Đã khôi phục sản phẩm thành công! Sản phẩm đang được mở bán.");
        return "redirect:/admin/products";
    }

    private String form(Model model) {
        ProductFormDto dto = (ProductFormDto) model.getAttribute("productForm");
        if (dto.getId() != null) {
            ProductFormDto stored = ProductFormDto.fromEntity(products.findById(dto.getId()));
            dto.setExistingImageIds(stored.getExistingImageIds());
            dto.setExistingImageUrls(stored.getExistingImageUrls());
        }
        model.addAttribute("categories", categories.productOptions(dto.getCategoryId()));
        model.addAttribute("brands", catalog.getBrandOptions(dto.getBrandId()));
        return "staff/product-form";
    }
}
