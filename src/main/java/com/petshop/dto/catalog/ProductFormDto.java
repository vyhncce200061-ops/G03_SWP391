package com.petshop.dto.catalog;

import com.petshop.entity.Product;
import com.petshop.entity.ProductImage;
import com.petshop.entity.enums.PetType;
import com.petshop.entity.enums.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Form DTO for creating/editing a Product from Staff side.
 * Handles validation before mapping to Product entity.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductFormDto {

    /** Null when creating new, has value when editing */
    private Long id;

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(max = 250, message = "Tên sản phẩm tối đa 250 ký tự")
    private String name;

    @NotBlank(message = "Slug không được để trống")
    @Size(max = 300, message = "Slug tối đa 300 ký tự")
    private String slug;

    @NotNull(message = "Vui lòng chọn danh mục")
    private Integer categoryId;

    /** Optional */
    private Integer brandId;

    @NotNull(message = "Vui lòng chọn loại thú cưng")
    private PetType petType;

    @Size(max = 1000, message = "Mô tả ngắn tối đa 1000 ký tự")
    private String shortDescription;

    private String description;

    private String usageInstructions;

    @NotNull(message = "Vui lòng chọn trạng thái")
    private ProductStatus status;

    @Builder.Default
    private Boolean isFeatured = false;

    // FIELDS FOR IMAGE UPLOAD

    /** List of uploaded image files from the form */
    private List<MultipartFile> imageFiles;

    /** List of existing image URLs (used when loading edit form) */
    @Builder.Default
    private List<String> existingImageUrls = new ArrayList<>();

    /**
     * Map Entity to DTO (when loading edit form).
     */
    public static ProductFormDto fromEntity(Product product) {
        ProductFormDto dto = ProductFormDto.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                .petType(product.getPetType())
                .shortDescription(product.getShortDescription())
                .description(product.getDescription())
                .usageInstructions(product.getUsageInstructions())
                .status(product.getStatus())
                .isFeatured(product.getIsFeatured())
                .build();

        // Map existing image URLs to DTO for displaying on the edit form
        if (product.getImages() != null) {
            dto.setExistingImageUrls(product.getImages().stream()
                    .map(ProductImage::getImageUrl)
                    .toList());
        }

        return dto;
    }
}
