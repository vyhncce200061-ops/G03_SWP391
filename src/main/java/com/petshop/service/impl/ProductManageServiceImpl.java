package com.petshop.service.impl;

import com.petshop.dto.catalog.ProductFormDto;
import com.petshop.entity.*;
import com.petshop.entity.enums.ProductStatus;
import com.petshop.repository.*;
import com.petshop.service.ProductManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductManageServiceImpl implements ProductManageService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductImageRepository productImageRepository;
    private final jakarta.validation.Validator validator;
    private final jakarta.persistence.EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public Page<Product> getProducts(Pageable pageable) {
        return searchProducts(null, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> searchProducts(String keyword, Integer categoryId, Pageable pageable) {
        Page<Product> products = productRepository.findAll((root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%"));
            }
            if (categoryId != null) {
                var category = root.join("category", jakarta.persistence.criteria.JoinType.LEFT);
                var parent = category.join("parentCategory", jakarta.persistence.criteria.JoinType.LEFT);
                predicates.add(cb.or(cb.equal(category.get("id"), categoryId), cb.equal(parent.get("id"), categoryId)));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, pageable);

        // Force initialization of lazy collections to prevent LazyInitializationException in Thymeleaf
        products.getContent().forEach(product -> {
            if (product.getImages() != null) {
                product.getImages().size();
            }
            if (product.getCategory() != null) {
                product.getCategory().getName();
                if (product.getCategory().getParentCategory() != null) {
                    product.getCategory().getParentCategory().getName();
                }
            }
            if (product.getBrand() != null) {
                product.getBrand().getName();
            }
        });

        return products;
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        // Preserve order, review and stock history; deletion only hides the product.
        toggleProductStatus(id, ProductStatus.HIDDEN);
    }

    @Override
    @Transactional(readOnly = true)
    public Product findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + id));
        // Trigger lazy loading for variants and images
        product.getVariants().size();
        product.getImages().size();
        return product;
    }

    @Override
    @Transactional
    public Product createProduct(ProductFormDto form, Long staffId) {
        validateForm(form);
        if (productRepository.existsBySlug(form.getSlug())) {
            throw new IllegalArgumentException("Slug \"" + form.getSlug() + "\" đã tồn tại.");
        }

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên"));
        Category category = categoryRepository.findById(form.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục"));
        Brand brand = form.getBrandId() != null ? brandRepository.findById(form.getBrandId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thương hiệu đã chọn.")) : null;

        Product product = new Product();
        mapFormToProduct(form, product, category, brand, staff);
        Product savedProduct = productRepository.save(product);

        saveImages(form.getImageFiles(), savedProduct);

        return savedProduct;
    }

    @Override
    @Transactional
    public Product updateProduct(Long id, ProductFormDto form, Long staffId) {
        validateForm(form);
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));
        entityManager.refresh(product, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        if (!product.getSlug().equals(form.getSlug()) && productRepository.existsBySlug(form.getSlug())) {
            throw new IllegalArgumentException("Slug \"" + form.getSlug() + "\" đã tồn tại.");
        }

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên"));
        Category category = categoryRepository.findById(form.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục"));
        Brand brand = form.getBrandId() != null ? brandRepository.findById(form.getBrandId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thương hiệu đã chọn.")) : null;

        if (form.getPrimaryImageId() != null && product.getImages().stream().noneMatch(i -> i.getId().equals(form.getPrimaryImageId()))) {
            throw new IllegalArgumentException("Ảnh chính phải thuộc sản phẩm đang sửa.");
        }
        mapFormToProduct(form, product, category, brand, staff);
        if (form.getPrimaryImageId() != null) {
            // Clear old primary first to respect SQL Server's filtered unique index.
            product.getImages().forEach(i -> i.setIsPrimary(false));
            productImageRepository.flush();
            product.getImages().stream().filter(i -> i.getId().equals(form.getPrimaryImageId())).findFirst().orElseThrow().setIsPrimary(true);
        }
        Product updatedProduct = productRepository.save(product);

        saveImages(form.getImageFiles(), updatedProduct);

        return updatedProduct;
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
            throw new IllegalArgumentException("Mã SKU đã tồn tại.");
        }
        variant.setStockQuantity(0);
        variant.setProduct(product);
        return variantRepository.save(variant);
    }

    @Override
    @Transactional
    public ProductVariant updateVariant(Long variantId, ProductVariant updateData) {
        ProductVariant variant = variantRepository.findByIdForUpdate(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy biến thể"));
        variant.setVariantName(updateData.getVariantName());
        variant.setPrice(updateData.getPrice());
        variant.setLowStockThreshold(updateData.getLowStockThreshold());
        variant.setStatus(updateData.getStatus());
        return variantRepository.save(variant);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductVariant findVariantById(Long variantId) {
        return variantRepository.findById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy biến thể: " + variantId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariant> getLowStockVariants() {
        return variantRepository.findLowStockVariants();
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    private void mapFormToProduct(ProductFormDto form, Product product, Category category, Brand brand, User staff) {
        product.setName(form.getName());
        product.setSlug(form.getSlug());
        product.setCategory(category);
        product.setBrand(brand);
        product.setPetType(form.getPetType());
        product.setShortDescription(form.getShortDescription());
        product.setDescription(form.getDescription());
        product.setUsageInstructions(form.getUsageInstructions());
        product.setIsFeatured(Boolean.TRUE.equals(form.getIsFeatured()));
        product.setStatus(form.getStatus());
        product.setUpdatedBy(staff);
        if (product.getId() == null) {
            product.setCreatedBy(staff);
        }
    }

    private void saveImages(List<MultipartFile> files, Product product) {
        if (files == null || files.isEmpty()) {
            return;
        }

        try {
            String uploadDir = "uploads/products/";
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            boolean isFirstImage = product.getImages().isEmpty();

            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;

                List<String> allowedTypes = java.util.Arrays.asList("image/jpeg", "image/png", "image/gif");
                if (file.getSize() > 5L * 1024 * 1024 || file.getContentType() == null
                        || !allowedTypes.contains(file.getContentType())) {
                    throw new IllegalArgumentException("Ảnh sản phẩm cần là JPG, PNG hoặc GIF, tối đa 5 MB.");
                }

                // Keep the original image bytes (including animated GIFs), but derive
                // the stored extension from validated content, never the client filename.
                String fileExtension;
                try (var input = file.getInputStream();
                     var imageInput = javax.imageio.ImageIO.createImageInputStream(input)) {
                    if (imageInput == null) throw new IllegalArgumentException("Nội dung tệp không phải hình ảnh hợp lệ.");
                    var readers = javax.imageio.ImageIO.getImageReaders(imageInput);
                    if (!readers.hasNext()) throw new IllegalArgumentException("Nội dung tệp không phải hình ảnh hợp lệ.");
                    var reader = readers.next();
                    try {
                        String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                        String actualType = switch (format) {
                            case "jpeg", "jpg" -> "image/jpeg";
                            case "png" -> "image/png";
                            case "gif" -> "image/gif";
                            default -> "";
                        };
                        if (!actualType.equals(file.getContentType())) {
                            throw new IllegalArgumentException("Nội dung ảnh không khớp định dạng JPG, PNG hoặc GIF đã khai báo.");
                        }
                        reader.setInput(imageInput);
                        if (reader.read(0) == null) throw new IllegalArgumentException("Nội dung tệp không phải hình ảnh hợp lệ.");
                        fileExtension = format.equals("gif") ? ".gif" : format.equals("png") ? ".png" : ".jpg";
                    } finally {
                        reader.dispose();
                    }
                } catch (IOException e) {
                    throw new IllegalArgumentException("Nội dung ảnh bị hỏng hoặc không đọc được.");
                }

                String newFileName = UUID.randomUUID() + fileExtension;
                Path path = Paths.get(uploadDir + newFileName);

                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                if (status != STATUS_COMMITTED) {
                                    try { Files.deleteIfExists(path); }
                                    catch (IOException error) { log.warn("Could not remove uncommitted product image {}", path, error); }
                                }
                            }
                        });

                try (var input = file.getInputStream()) {
                    Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
                }

                ProductImage image = new ProductImage();
                image.setProduct(product);
                image.setImageUrl("/uploads/products/" + newFileName);
                image.setAltText(product.getName());
                image.setIsPrimary(isFirstImage);

                productImageRepository.save(image);
                product.getImages().add(image);

                isFirstImage = false;
            }
        } catch (IOException e) {
            log.error("Error saving product images: ", e);
            throw new RuntimeException("Không thể lưu ảnh sản phẩm.");
        }
    }

    private void validateForm(ProductFormDto form) {
        var violations = validator.validate(form);
        if (!violations.isEmpty()) throw new IllegalArgumentException(violations.iterator().next().getMessage());
    }
}
