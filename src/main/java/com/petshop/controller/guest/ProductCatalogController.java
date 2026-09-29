package com.petshop.controller.guest;

import com.petshop.dto.catalog.ProductFilterDto;
import com.petshop.dto.catalog.ProductSummaryDto;
import com.petshop.entity.Product;
import com.petshop.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class ProductCatalogController {

    private final CatalogService catalogService;

    @GetMapping("/products")
    public String listProducts(@ModelAttribute ProductFilterDto filter,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "12") int size,
                               Model model) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductSummaryDto> productPage = catalogService.searchAndFilterProducts(filter, pageable);

        model.addAttribute("products", productPage);
        model.addAttribute("filter", filter);
        model.addAttribute("categories", catalogService.getAllCategories());
        model.addAttribute("brands", catalogService.getAllBrands());
        return "guest/product-list";
    }

    @GetMapping("/products/{slug}")
    public String productDetail(@PathVariable String slug, Model model) {
        Product product = catalogService.getProductBySlug(slug);
        model.addAttribute("product", product);
        model.addAttribute("relatedProducts", catalogService.getFeaturedProducts(4));
        return "guest/product-detail";
    }

    @GetMapping("/categories/{code}")
    public String categoryShortcut(@PathVariable String code) {
        return "redirect:/products?categoryCode=" + code;
    }
}
