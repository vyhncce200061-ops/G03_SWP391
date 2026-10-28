
package com.petshop.controller.admin;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.entity.Product;
import com.petshop.entity.enums.ProductStatus;
import com.petshop.service.CatalogService;
import com.petshop.service.ProductManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductManageService productManageService;
    private final CatalogService catalogService;

    // Display product list
    @GetMapping
    public String listProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        // Always display 10 products per page
        Pageable pageable = PageRequest.of(Math.max(page, 0), 10);

        Integer selectedCategoryId =
                categoryId == null ? null : categoryId.intValue();

        Page<Product> products = productManageService.searchProducts(
                keyword,
                selectedCategoryId,
                pageable
        );

        model.addAttribute("products", products);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("categories", catalogService.getAllCategories());

        return "staff/product-list";
    }

    // Display create form
    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("product", new Product());
        model.addAttribute("categories", catalogService.getAllCategories());
        model.addAttribute("brands", catalogService.getAllBrands());
        model.addAttribute("statuses", ProductStatus.values());

        return "staff/product-form";
    }

    // Create product
    @PostMapping("/create")
    public String createProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ModelAttribute Product product,
            RedirectAttributes redirectAttributes) {

        try {
            if (userDetails == null) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage", "Vui lòng đăng nhập lại."
                );
                return "redirect:/login";
            }

            productManageService.createProduct(product, userDetails.getId());

            redirectAttributes.addFlashAttribute(
                    "successMessage", "Thêm sản phẩm thành công!"
            );

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", e.getMessage()
            );
            return "redirect:/admin/products/create";
        }

        return "redirect:/admin/products";
    }

    // Display edit form
    @GetMapping("/{id}/edit")
    public String editForm(
            @PathVariable Long id,
            Model model) {

        model.addAttribute("product", productManageService.findById(id));
        model.addAttribute("categories", catalogService.getAllCategories());
        model.addAttribute("brands", catalogService.getAllBrands());
        model.addAttribute("statuses", ProductStatus.values());

        return "staff/product-form";
    }

    // Update product
    @PostMapping("/{id}/edit")
    public String updateProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ModelAttribute Product product,
            RedirectAttributes redirectAttributes) {

        try {
            if (userDetails == null) {
                return "redirect:/login";
            }

            productManageService.updateProduct(
                    id, product, userDetails.getId()
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage", "Cập nhật sản phẩm thành công!"
            );

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", e.getMessage()
            );
        }

        return "redirect:/admin/products";
    }

    // Update product status
    @PostMapping("/{id}/status")
    public String updateStatus(
            @PathVariable Long id,
            @RequestParam ProductStatus status,
            RedirectAttributes redirectAttributes) {

        try {
            productManageService.toggleProductStatus(id, status);

            redirectAttributes.addFlashAttribute(
                    "successMessage", "Cập nhật trạng thái sản phẩm thành công!"
            );

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", e.getMessage()
            );
        }

        return "redirect:/admin/products";
    }
}
