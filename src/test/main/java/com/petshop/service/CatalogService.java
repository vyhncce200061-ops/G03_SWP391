package com.petshop.service;

import com.petshop.dto.catalog.ProductFilterDto;
import com.petshop.dto.catalog.ProductSummaryDto;
import com.petshop.entity.Brand;
import com.petshop.entity.Category;
import com.petshop.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CatalogService {
    Page<ProductSummaryDto> searchAndFilterProducts(ProductFilterDto filter, Pageable pageable);
    Product getProductBySlug(String slug);
    List<Category> getAllCategories();
    List<Category> getActiveCategoryTree();
    List<Brand> getAllBrands();
    List<ProductSummaryDto> getFeaturedProducts(int limit);
}
