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
        Page<Product> products;
        if (filter.getPetType() != null) {
            products = productRepository.findByPetTypeAndStatus(filter.getPetType(), ProductStatus.ACTIVE, pageable);
        } else if (filter.getCategoryId() != null) {
            products = productRepository.findByCategoryIdAndStatus(filter.getCategoryId(), ProductStatus.ACTIVE, pageable);
        } else if (filter.getBrandId() != null) {
            products = productRepository.findByBrandIdAndStatus(filter.getBrandId(), ProductStatus.ACTIVE, pageable);
        } else {
            products = productRepository.findByStatus(ProductStatus.ACTIVE, pageable);
        }

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
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .totalStock(totalStock)
                .avgRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 5.0)
                .reviewCount(reviewCount != null ? reviewCount : 0L)
                .build();
    }
}
