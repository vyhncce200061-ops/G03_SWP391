package com.petshop.service;

import com.petshop.dto.catalog.ProductFormDto;
import com.petshop.entity.Product;
import com.petshop.entity.ProductVariant;
import com.petshop.entity.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service for Staff to manage Products and Product Variants (SKUs).
 */
public interface ProductManageService {

    Page<Product> getProducts(Pageable pageable);
    Page<Product> searchProducts(String keyword, Integer categoryId, Pageable pageable);
    void deleteProduct(Long id);
    Product findById(Long id);
    Product createProduct(ProductFormDto form, Long staffId);
    Product updateProduct(Long id, ProductFormDto form, Long staffId);
    void toggleProductStatus(Long id, ProductStatus status);
    ProductVariant findVariantById(Long variantId);
    ProductVariant addVariant(Long productId, ProductVariant variant);
    ProductVariant updateVariant(Long variantId, ProductVariant variant);
    List<ProductVariant> getLowStockVariants();
}
