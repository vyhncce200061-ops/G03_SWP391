package com.petshop.service.impl;

import com.petshop.dto.catalog.ProductFilterDto;
import com.petshop.dto.catalog.ProductSummaryDto;
import com.petshop.entity.Brand;
import com.petshop.entity.Category;
import com.petshop.entity.Product;
import com.petshop.entity.ProductImage;
import com.petshop.entity.ProductVariant;
import com.petshop.entity.enums.ProductStatus;
import com.petshop.entity.enums.ProductVariantStatus;
import com.petshop.repository.*;
import com.petshop.service.CatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogServiceImpl implements CatalogService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductImageRepository imageRepository;
    private final ProductVariantRepository variantRepository;
    private final ReviewRepository reviewRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryDto> searchAndFilterProducts(ProductFilterDto filter, Pageable pageable) {
        Page<Product> products = productRepository.findAll((root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));
            if (filter.getKeyword() != null && !filter.getKeyword().isBlank())
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), "%" + filter.getKeyword().trim().toLowerCase(java.util.Locale.ROOT) + "%"),
                        cb.like(cb.lower(root.get("shortDescription")), "%" + filter.getKeyword().trim().toLowerCase(java.util.Locale.ROOT) + "%")));
            if (filter.getPetType() != null) predicates.add(cb.equal(root.get("petType"), filter.getPetType()));
            if (filter.getBrandId() != null) predicates.add(cb.equal(root.get("brand").get("id"), filter.getBrandId()));
            if (filter.getCategoryId() != null || (filter.getCategoryCode() != null && !filter.getCategoryCode().isBlank())) {
                var category = root.join("category", jakarta.persistence.criteria.JoinType.LEFT);
                var parent = category.join("parentCategory", jakarta.persistence.criteria.JoinType.LEFT);
                if (filter.getCategoryId() != null) predicates.add(cb.or(cb.equal(category.get("id"), filter.getCategoryId()), cb.equal(parent.get("id"), filter.getCategoryId())));
                if (filter.getCategoryCode() != null && !filter.getCategoryCode().isBlank())
                    predicates.add(cb.or(cb.equal(category.get("code"), filter.getCategoryCode()), cb.equal(parent.get("code"), filter.getCategoryCode())));
            }
            var priceQuery = query.subquery(BigDecimal.class);
            var variant = priceQuery.from(ProductVariant.class);
            priceQuery.select(cb.min(variant.get("price"))).where(cb.equal(variant.get("product"), root), cb.equal(variant.get("status"), ProductVariantStatus.ACTIVE));
            if (filter.getMinPrice() != null) predicates.add(cb.ge(priceQuery, filter.getMinPrice()));
            if (filter.getMaxPrice() != null) predicates.add(cb.le(priceQuery, filter.getMaxPrice()));
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                if ("price_asc".equals(filter.getSortBy())) query.orderBy(cb.asc(priceQuery), cb.asc(root.get("id")));
                else if ("price_desc".equals(filter.getSortBy())) query.orderBy(cb.desc(priceQuery), cb.asc(root.get("id")));
                else if ("asc".equals(filter.getSortBy())) query.orderBy(cb.asc(root.get("name")), cb.asc(root.get("id")));
                else if ("desc".equals(filter.getSortBy())) query.orderBy(cb.desc(root.get("name")), cb.asc(root.get("id")));
                else query.orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, pageable);

        return products.map(this::mapToSummaryDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Product getProductBySlug(String slug) {
        return productRepository.findDetailedBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với slug: " + slug));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepository.findByIsActiveTrueOrderBySortOrderAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> getActiveCategoryTree() {
        return categoryRepository.findAllActiveHierarchy();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Brand> getAllBrands() {
        return brandRepository.findByIsActiveTrueOrderByNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductSummaryDto> getFeaturedProducts(int limit) {
        if (limit <= 0) return List.of();
        Pageable pageable = PageRequest.of(0, limit);
        Page<Product> featured = productRepository.findByStatusAndIsFeaturedTrue(ProductStatus.ACTIVE, pageable);
        return featured.getContent().stream()
                .map(this::mapToSummaryDto)
                .collect(Collectors.toList());
    }

    private ProductSummaryDto mapToSummaryDto(Product product) {
        List<ProductVariant> variants = variantRepository.findByProductIdAndStatus(product.getId(), ProductVariantStatus.ACTIVE);
        
        BigDecimal minPrice = variants.stream()
                .map(ProductVariant::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal maxPrice = variants.stream()
                .map(ProductVariant::getPrice)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        int totalStock = variants.stream()
                .mapToInt(ProductVariant::getStockQuantity)
                .sum();

        String primaryImageUrl = imageRepository.findByProductIdAndIsPrimaryTrue(product.getId())
                .map(ProductImage::getImageUrl)
                .orElseGet(() -> imageRepository.findByProductIdOrderBySortOrderAsc(product.getId()).stream()
                        .findFirst()
                        .map(ProductImage::getImageUrl)
                        .orElse(null));

        Double avgRating = reviewRepository.calculateAverageRating(product.getId());
        Long reviewCount = reviewRepository.countVisibleReviews(product.getId());

        return ProductSummaryDto.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : "")
                .brandName(product.getBrand() != null ? product.getBrand().getName() : "")
                .petType(product.getPetType())
                .primaryImageUrl(primaryImageUrl)
                .imageUrl(primaryImageUrl)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .totalStock(totalStock)
                .avgRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0)
                .reviewCount(reviewCount != null ? reviewCount : 0L)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> filterProducts(String keyword, Long categoryId, Pageable pageable) {
        return productRepository.findAll((root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));
            if (keyword != null && !keyword.isBlank()) predicates.add(cb.like(cb.lower(root.get("name")), "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%"));
            if (categoryId != null) predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, pageable);
    }
}
