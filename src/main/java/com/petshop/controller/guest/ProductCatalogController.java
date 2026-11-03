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
import com.petshop.entity.Category;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

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
            @RequestParam(name="sort", required = false) String sort,
            @RequestParam(required = false) String priceRange,
            @RequestParam(required = false) java.math.BigDecimal minPrice,
            @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Model model) {

        // Support both current catalog parameters and existing links from Vy's branch.
        if (sortBy == null || sortBy.isBlank()) sortBy = sort;
        if ("priceAsc".equals(sortBy)) sortBy = "price_asc";
        if ("priceDesc".equals(sortBy)) sortBy = "price_desc";
        if (priceRange != null && minPrice == null && maxPrice == null) {
            switch (priceRange) {
                case "under100" -> maxPrice = new java.math.BigDecimal("99999.99");
                case "100to300" -> { minPrice = new java.math.BigDecimal("100000"); maxPrice = new java.math.BigDecimal("300000"); }
                case "300to500" -> { minPrice = new java.math.BigDecimal("300000"); maxPrice = new java.math.BigDecimal("500000"); }
                case "over500" -> minPrice = new java.math.BigDecimal("500000.01");
                default -> { }
            }
        }
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


    /**
     * Suggest matching categories and products while searching.
     *
     * @param keyword the search keyword
     * @return matching categories and products
     */
    @GetMapping("/products/suggestions")
    @ResponseBody
    public Map<String, Object> suggestProducts(
            @RequestParam(required = false) String keyword) {

        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> categorySuggestions =
                new ArrayList<>();
        List<ProductSummaryDto> productSuggestions =
                new ArrayList<>();

        if (keyword == null || keyword.trim().length() < 2) {
            result.put("categories", categorySuggestions);
            result.put("products", productSuggestions);
            return result;
        }

        String search = normalizeText(keyword);

        // Find matching categories from the database.
        List<Category> allCategories =
                catalogService.getAllCategories();

        for (Category category : allCategories) {
            if (category.getName() == null
                    || !normalizeText(category.getName())
                    .contains(search)) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", category.getId());
            item.put("name", category.getName());
            item.put("code", category.getCode());
            categorySuggestions.add(item);

            // Retrieve products in the matching category.
            ProductFilterDto filter = ProductFilterDto.builder()
                    .categoryId(category.getId())
                    .build();

            Page<ProductSummaryDto> page =
                    catalogService.searchAndFilterProducts(
                            filter, PageRequest.of(0, 5)
                    );

            productSuggestions.addAll(page.getContent());

            if (productSuggestions.size() >= 5) {
                break;
            }
        }

        // If no category matches, search by product keyword.
        if (categorySuggestions.isEmpty()) {
            ProductFilterDto filter = ProductFilterDto.builder()
                    .keyword(keyword.trim())
                    .build();

            productSuggestions.addAll(
                    catalogService.searchAndFilterProducts(
                            filter, PageRequest.of(0, 5)
                    ).getContent()
            );
        }

        // Return no more than five products.
        if (productSuggestions.size() > 5) {
            productSuggestions = new ArrayList<>(
                    productSuggestions.subList(0, 5)
            );
        }

        result.put("categories", categorySuggestions);
        result.put("products", productSuggestions);

        return result;
    }

    /**
     * Normalize Vietnamese text for case- and accent-insensitive matching.
     *
     * @param value the input text
     * @return normalized text
     */
    private String normalizeText(String value) {
        String normalized = Normalizer.normalize(
                value.toLowerCase(Locale.ROOT),
                Normalizer.Form.NFD
        );

        return Pattern.compile("\\p{M}+")
                .matcher(normalized)
                .replaceAll("")
                .replace('đ', 'd')
                .trim();
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
