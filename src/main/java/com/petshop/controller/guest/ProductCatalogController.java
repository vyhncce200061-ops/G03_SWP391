package com.petshop.controller.guest;

import com.petshop.dto.catalog.ProductFilterDto;
import com.petshop.dto.catalog.ProductSummaryDto;
import com.petshop.entity.Category;
import com.petshop.entity.Product;
import com.petshop.entity.enums.PetType;
import com.petshop.service.CatalogService;
import com.petshop.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
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
    private final ReviewService reviews;
    private final com.petshop.service.WishlistService wishlistService;

    // Hiển thị danh sách sản phẩm và xử lý các bộ lọc
    @GetMapping("/products")
    public String listProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer brandId,
            @RequestParam(required = false) String petType,
            @RequestParam(required = false) String sortBy,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(required = false) String priceRange,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Model model) {

        // Hỗ trợ cả tên tham số sắp xếp cũ và mới
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = sort;
        }

        if ("priceAsc".equals(sortBy)) {
            sortBy = "price_asc";
        }

        if ("priceDesc".equals(sortBy)) {
            sortBy = "price_desc";
        }

        // Xử lý khoảng giá theo đơn vị tiền Việt Nam (VNĐ)
        if (priceRange != null && !priceRange.isBlank()
                && minPrice == null && maxPrice == null) {

            switch (priceRange) {

                case "under100" -> {
                    minPrice = null;
                    maxPrice = new BigDecimal("99999.99");
                }

                case "100to300" -> {
                    minPrice = new BigDecimal("100000");
                    maxPrice = new BigDecimal("300000");
                }

                case "300to500" -> {
                    minPrice = new BigDecimal("300000");
                    maxPrice = new BigDecimal("500000");
                }

                case "over500" -> {
                    minPrice = new BigDecimal("500000.01");
                    maxPrice = null;
                }

                default -> {
                    minPrice = null;
                    maxPrice = null;
                }
            }
        }

        // Chuyển loại thú cưng thành kiểu dữ liệu PetType
        PetType petTypeEnum = null;

        if (petType != null && !petType.trim().isEmpty()) {
            try {
                petTypeEnum = PetType.valueOf(
                        petType.trim().toUpperCase(Locale.ROOT)
                );
            } catch (IllegalArgumentException ignored) {
                petTypeEnum = null;
            }
        }

        // Tạo điều kiện lọc sản phẩm
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

        // Thiết lập phân trang
        Pageable pageable = com.petshop.util.PageRequests.bounded(page, size);

        // Lấy danh sách sản phẩm theo điều kiện lọc
        Page<ProductSummaryDto> productPage =
                catalogService.searchAndFilterProducts(filter, pageable);

        // Gửi dữ liệu sang giao diện
        model.addAttribute("productPage", productPage);
        model.addAttribute("filter", filter);
        model.addAttribute("categories", catalogService.getAllCategories());
        model.addAttribute("brands", catalogService.getAllBrands());

        model.addAttribute("keyword", keyword != null ? keyword : "");
        model.addAttribute("selectedCategory",
                categoryCode != null ? categoryCode : "");

        // Xác định danh mục đang được chọn
        Integer selectedId = categoryId;

        if (selectedId == null && categoryCode != null) {
            selectedId = catalogService.getAllCategories()
                    .stream()
                    .filter(c -> categoryCode.equals(c.getCode()))
                    .map(Category::getId)
                    .findFirst()
                    .orElse(null);
        }

        model.addAttribute("selectedCategoryId", selectedId);

        // Giữ lại các lựa chọn bộ lọc
        model.addAttribute("selectedPetType",
                petTypeEnum != null ? petTypeEnum.name() : "");

        model.addAttribute("selectedSort",
                sortBy != null ? sortBy : "newest");

        model.addAttribute("selectedPriceRange",
                priceRange != null ? priceRange : "");

        return "guest/product-list";
    }

    // Gợi ý danh mục và sản phẩm khi người dùng tìm kiếm
    @GetMapping("/products/suggestions")
    @ResponseBody
    public Map<String, Object> suggestProducts(
            @RequestParam(required = false) String keyword) {

        Map<String, Object> result = new LinkedHashMap<>();

        List<Map<String, Object>> categorySuggestions =
                new ArrayList<>();

        List<ProductSummaryDto> productSuggestions =
                new ArrayList<>();

        // Không tìm kiếm khi từ khóa có ít hơn hai ký tự
        if (keyword == null || keyword.trim().length() < 2) {
            result.put("categories", categorySuggestions);
            result.put("products", productSuggestions);
            return result;
        }

        String search = normalizeText(keyword);

        // Lấy danh sách danh mục
        List<Category> allCategories =
                catalogService.getAllCategories();

        for (Category category : allCategories) {

            if (category.getName() == null
                    || !normalizeText(category.getName()).contains(search)) {
                continue;
            }

            // Tạo thông tin danh mục gợi ý
            Map<String, Object> item = new LinkedHashMap<>();

            item.put("id", category.getId());
            item.put("name", category.getName());
            item.put("code", category.getCode());

            categorySuggestions.add(item);

            // Lấy các sản phẩm thuộc danh mục phù hợp
            ProductFilterDto filter = ProductFilterDto.builder()
                    .categoryId(category.getId())
                    .build();

            Page<ProductSummaryDto> productPage =
                    catalogService.searchAndFilterProducts(
                            filter,
                            PageRequest.of(0, 5)
                    );

            productSuggestions.addAll(productPage.getContent());

            if (productSuggestions.size() >= 5) {
                break;
            }
        }

        // Nếu không có danh mục phù hợp thì tìm theo tên sản phẩm
        if (categorySuggestions.isEmpty()) {

            ProductFilterDto filter = ProductFilterDto.builder()
                    .keyword(keyword.trim())
                    .build();

            productSuggestions.addAll(
                    catalogService.searchAndFilterProducts(
                            filter,
                            PageRequest.of(0, 5)
                    ).getContent()
            );
        }

        // Giới hạn tối đa năm sản phẩm gợi ý
        if (productSuggestions.size() > 5) {
            productSuggestions = new ArrayList<>(
                    productSuggestions.subList(0, 5)
            );
        }

        result.put("categories", categorySuggestions);
        result.put("products", productSuggestions);

        return result;
    }

    // Chuẩn hóa tiếng Việt để tìm kiếm không phân biệt dấu và chữ hoa
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

    // Hiển thị chi tiết sản phẩm và danh sách đánh giá
    @GetMapping("/products/{slug}")
    public String productDetail(@PathVariable String slug, @RequestParam(defaultValue="0") int reviewPage, Model model,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            com.petshop.config.security.CustomUserDetails principal) {
        Product product;

        try {
            product = catalogService.getProductBySlug(slug);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy sản phẩm."
            );
        }

        model.addAttribute("product", product);
        model.addAttribute("reviews", reviews.publicReviews(product.getId(), com.petshop.util.PageRequests.bounded(reviewPage,10)));
        model.addAttribute("wishlisted", principal != null && "CUSTOMER".equals(principal.getRoleCode())
                && wishlistService.contains(principal.getId(), product.getId()));
        model.addAttribute("availableVariants", product.getVariants().stream()
                .filter(v -> v.getStatus() == com.petshop.entity.enums.ProductVariantStatus.ACTIVE && v.getStockQuantity() > 0)
                .toList());
        model.addAttribute("relatedProducts", catalogService.getFeaturedProducts(4));

        return "guest/product-detail";
    }

    // Chuyển nhanh đến danh sách sản phẩm theo danh mục
    @GetMapping("/categories/{code}")
    public String categoryShortcut(@PathVariable String code) {
        return "redirect:/products?categoryCode=" + code;
    }
}
