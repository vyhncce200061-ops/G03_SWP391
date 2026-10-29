package com.petshop.controller.staff;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.catalog.ProductFormDto;
import com.petshop.dto.catalog.ProductVariantFormDto;
import com.petshop.entity.Product;
import com.petshop.entity.ProductVariant;
import com.petshop.entity.enums.ProductStatus;
import com.petshop.service.CatalogService;
import com.petshop.service.ProductManageService;
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

/**
 * Staff controller for Product & Variant (SKU) management.
 * Handles CRUD operations with server-side validation.
 */
@Controller
@RequestMapping("/staff/products")
@RequiredArgsConstructor
public class ProductManageController {

    private final ProductManageService productManageService;
    private final CatalogService catalogService;

    @ModelAttribute("productBasePath")
    public String productBasePath() { return "/staff/products"; }

    // ============ PRODUCT LIST ============
    @GetMapping
    public String listProducts(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(required = false) Integer categoryId,
                               Model model) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)));
        Page<Product> products = productManageService.searchProducts(keyword, categoryId, pageable);
        model.addAttribute("products", products);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("categories", catalogService.getAllCategories());
        return "staff/product-list";
    }

    // ============ CREATE PRODUCT FORM ============
    @GetMapping("/create")
    public String createForm(Model model) {
        if (!model.containsAttribute("productForm")) {
            model.addAttribute("productForm", new ProductFormDto());
        }
        model.addAttribute("categories", catalogService.getAllCategories());
        model.addAttribute("brands", catalogService.getAllBrands());
        return "staff/product-form";
    }

    // ============ SAVE NEW PRODUCT ============
    @PostMapping("/create")
    public String saveProduct(@AuthenticationPrincipal CustomUserDetails userDetails,
                              @Valid @ModelAttribute("productForm") ProductFormDto form,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", catalogService.getAllCategories());
            model.addAttribute("brands", catalogService.getAllBrands());
            return "staff/product-form";
        }

        try {
            productManageService.createProduct(form, userDetails.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Tạo sản phẩm thành công!");
            return "redirect:/staff/products";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("categories", catalogService.getAllCategories());
            model.addAttribute("brands", catalogService.getAllBrands());
            return "staff/product-form";
        }
    }

    // ============ EDIT PRODUCT FORM ============
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Product product = productManageService.findById(id);
        model.addAttribute("productForm", ProductFormDto.fromEntity(product));
        model.addAttribute("categories", catalogService.getAllCategories());
        model.addAttribute("brands", catalogService.getAllBrands());
        return "staff/product-form";
    }

    // ============ UPDATE PRODUCT ============
    @PostMapping("/{id}/edit")
    public String updateProduct(@AuthenticationPrincipal CustomUserDetails userDetails,
                                @PathVariable Long id,
                                @Valid @ModelAttribute("productForm") ProductFormDto form,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        form.setId(id);
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", catalogService.getAllCategories());
            model.addAttribute("brands", catalogService.getAllBrands());
            return "staff/product-form";
        }

        try {
            productManageService.updateProduct(id, form, userDetails.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật sản phẩm thành công!");
            return "redirect:/staff/products";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("categories", catalogService.getAllCategories());
            model.addAttribute("brands", catalogService.getAllBrands());
            return "staff/product-form";
        }
    }

    // ============ TOGGLE PRODUCT STATUS ============
    @PostMapping("/{id}/status")
    public String toggleStatus(@PathVariable Long id,
                               @RequestParam ProductStatus status,
                               RedirectAttributes redirectAttributes) {
        productManageService.toggleProductStatus(id, status);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái thành công!");
        return "redirect:/staff/products";
    }

    // ============ MANAGE VARIANTS ============
    @PostMapping("/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        productManageService.deleteProduct(id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã ẩn sản phẩm. Lịch sử đơn hàng và tồn kho được giữ lại.");
        return "redirect:/staff/products";
    }

    @GetMapping("/{id}/variants")
    public String manageVariants(@PathVariable Long id, Model model) {
        Product product = productManageService.findById(id);
        model.addAttribute("product", product);
        if (!model.containsAttribute("variantForm")) {
            model.addAttribute("variantForm", new ProductVariantFormDto());
        }
        return "staff/variant-list";
    }

    // ============ CREATE VARIANT FORM ============
    @GetMapping("/{id}/variants/create")
    public String createVariantForm(@PathVariable Long id, Model model) {
        Product product = productManageService.findById(id);
        model.addAttribute("product", product);
        model.addAttribute("variantForm", new ProductVariantFormDto());
        return "staff/variant-form";
    }

    // ============ EDIT VARIANT FORM ============
    @GetMapping("/{id}/variants/{vid}/edit")
    public String editVariantForm(@PathVariable Long id, @PathVariable Long vid, Model model) {
        Product product = productManageService.findById(id);
        ProductVariant variant = productManageService.findVariantById(vid);
        if (!variant.getProduct().getId().equals(id)) throw new IllegalArgumentException("SKU không thuộc sản phẩm này.");

        ProductVariantFormDto form = ProductVariantFormDto.builder()
                .id(variant.getId())
                .skuCode(variant.getSkuCode())
                .variantName(variant.getVariantName())
                .price(variant.getPrice())
                .lowStockThreshold(variant.getLowStockThreshold())
                .status(variant.getStatus())
                .build();

        model.addAttribute("product", product);
        model.addAttribute("variantForm", form);
        return "staff/variant-form";
    }

    // ============ ADD VARIANT ============
    @PostMapping("/{id}/variants")
    public String addVariant(@PathVariable Long id,
                             @Valid @ModelAttribute("variantForm") ProductVariantFormDto form,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            Product product = productManageService.findById(id);
            model.addAttribute("product", product);
            return "staff/variant-form";
        }

        try {
            ProductVariant variant = form.toEntity();
            productManageService.addVariant(id, variant);
            redirectAttributes.addFlashAttribute("successMessage", "Thêm biến thể SKU thành công!");
            return "redirect:/staff/products/" + id + "/variants";
        } catch (IllegalArgumentException e) {
            Product product = productManageService.findById(id);
            model.addAttribute("product", product);
            model.addAttribute("errorMessage", e.getMessage());
            return "staff/variant-form";
        }
    }

    // ============ UPDATE VARIANT ============
    @PostMapping("/variants/{vid}/edit")
    public String updateVariant(@PathVariable Long vid,
                                @RequestParam Long productId,
                                @Valid @ModelAttribute("variantForm") ProductVariantFormDto form,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            Product product = productManageService.findById(productId);
            model.addAttribute("product", product);
            return "staff/variant-form";
        }

        try {
            if (!productManageService.findVariantById(vid).getProduct().getId().equals(productId)) {
                throw new IllegalArgumentException("SKU không thuộc sản phẩm này.");
            }
            ProductVariant variant = form.toEntity();
            productManageService.updateVariant(vid, variant);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật biến thể thành công!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/staff/products/" + productId + "/variants";
    }

    // ============ LOW STOCK ALERT ============
    @GetMapping("/low-stock")
    public String lowStock(Model model) {
        model.addAttribute("lowStockVariants", productManageService.getLowStockVariants());
        return "staff/low-stock";
    }
}
