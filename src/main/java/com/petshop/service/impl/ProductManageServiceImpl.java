package com.petshop.service.impl;

import com.petshop.entity.Product;
import com.petshop.entity.ProductVariant;
import com.petshop.entity.User;
import com.petshop.entity.enums.ProductStatus;
import com.petshop.repository.ProductRepository;
import com.petshop.repository.ProductVariantRepository;
import com.petshop.repository.UserRepository;
import com.petshop.service.ProductManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductManageServiceImpl implements ProductManageService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<Product> getProducts(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + id));
    }

    @Override
    @Transactional
    public Product createProduct(Product product, Long staffId) {
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại: " + staffId));
        product.setCreatedBy(staff);
        product.setUpdatedBy(staff);
        return productRepository.save(product);
    }

    @Override
    @Transactional
    public Product updateProduct(Long id, Product updateData, Long staffId) {
        Product product = findById(id);
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại: " + staffId));

        product.setName(updateData.getName());
        product.setCategory(updateData.getCategory());
        product.setBrand(updateData.getBrand());
        product.setPetType(updateData.getPetType());
        product.setShortDescription(updateData.getShortDescription());
        product.setDescription(updateData.getDescription());
        product.setUsageInstructions(updateData.getUsageInstructions());
        product.setIsFeatured(updateData.getIsFeatured());
        product.setStatus(updateData.getStatus());
        product.setUpdatedBy(staff);

        return productRepository.save(product);
    }

    @Override
    @Transactional
    public void toggleProductStatus(Long id, ProductStatus status) {
        Product product = findById(id);
        product.setStatus(status);
        productRepository.save(product);
    }

    @Override
    @Transactional
    public ProductVariant addVariant(Long productId, ProductVariant variant) {
        Product product = findById(productId);
        if (variantRepository.existsBySkuCode(variant.getSkuCode())) {
            throw new IllegalArgumentException("Mã SKU đã tồn tại: " + variant.getSkuCode());
        }
        variant.setProduct(product);
        return variantRepository.save(variant);
    }

    @Override
    @Transactional
    public ProductVariant updateVariant(Long variantId, ProductVariant updateData) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy biến thể: " + variantId));

        variant.setVariantName(updateData.getVariantName());
        variant.setPrice(updateData.getPrice());
        variant.setOriginalPrice(updateData.getOriginalPrice());
        variant.setLowStockThreshold(updateData.getLowStockThreshold());
        variant.setStatus(updateData.getStatus());

        return variantRepository.save(variant);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariant> getLowStockVariants() {
        return variantRepository.findLowStockVariants();
    }
}
