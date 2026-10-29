package com.petshop.controller.guest;

import com.petshop.dto.catalog.ProductFilterDto;
import com.petshop.dto.catalog.ProductSummaryDto;
import com.petshop.entity.enums.PetType;
import com.petshop.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.petshop.entity.Product;

@Controller
@RequiredArgsConstructor
public class ProductCatalogController {

    private final CatalogService catalogService;
    private final com.petshop.service.ReviewService reviews;

    @GetMapping("/products")
    public String listProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer brandId,
            @RequestParam(required = false) String petType,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) java.math.BigDecimal minPrice,
            @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Model model) {

        PetType petTypeEnum = null;
        if (petType != null && !petType.trim().isEmpty()) {
            try {
                petTypeEnum = PetType.valueOf(petType.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        ProductFilterDto filter = ProductFilterDto.builder()
                .keyword(keyword)
                .categoryCode(categoryCode)
                .categoryId(categoryId)
                .brandId(brandId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .petType(petTypeEnum)
                .sortBy(sortBy)
                .build();

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)));
        Page<ProductSummaryDto> productPage = catalogService.searchAndFilterProducts(filter, pageable);

        model.addAttribute("productPage", productPage);
        model.addAttribute("filter", filter);
        model.addAttribute("categories", catalogService.getAllCategories());
        model.addAttribute("brands", catalogService.getAllBrands());
        model.addAttribute("keyword", keyword != null ? keyword : "");
        model.addAttribute("selectedCategory", categoryCode != null ? categoryCode : "");
        Integer selectedId = categoryId;
        if (selectedId == null && categoryCode != null) {
            selectedId = catalogService.getAllCategories().stream().filter(c -> categoryCode.equals(c.getCode()))
                    .map(com.petshop.entity.Category::getId).findFirst().orElse(null);
        }
        model.addAttribute("selectedCategoryId", selectedId);
        model.addAttribute("selectedPetType", petType != null ? petType : "");
        model.addAttribute("selectedSort", sortBy != null ? sortBy : "newest");

        return "guest/product-list";
    }


    @GetMapping("/products/{slug}")
    public String productDetail(@PathVariable String slug, @RequestParam(defaultValue="0") int reviewPage, Model model) {
        Product product;
        try {
            product = catalogService.getProductBySlug(slug);
        } catch (IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm.");
        }

        model.addAttribute("product", product);
        model.addAttribute("reviews", reviews.publicReviews(product.getId(), PageRequest.of(Math.max(0, reviewPage),10)));
        model.addAttribute("availableVariants", product.getVariants().stream()
                .filter(v -> v.getStatus() == com.petshop.entity.enums.ProductVariantStatus.ACTIVE && v.getStockQuantity() > 0)
                .toList());
        model.addAttribute("relatedProducts", catalogService.getFeaturedProducts(4));

        return "guest/product-detail";
    }


    @GetMapping("/categories/{code}")
    public String categoryShortcut(@PathVariable String code) {
        return "redirect:/products?categoryCode=" + code;
    }
}
