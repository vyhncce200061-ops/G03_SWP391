package com.petshop.service;

import com.petshop.entity.Category;
import com.petshop.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryManageService {
    private final CategoryRepository categories;

    @Transactional
    public void create(Category form, Integer parentId) {
        if (form.getName() == null || form.getName().isBlank() || form.getCode() == null || form.getCode().isBlank())
            throw new IllegalArgumentException("Tên và mã danh mục không được để trống.");
        if (categories.existsByCode(form.getCode().trim())) throw new IllegalArgumentException("Mã danh mục đã tồn tại.");
        Category parent = parentId == null ? null : categories.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Danh mục cha không tồn tại."));
        if (parent != null && parent.getParentCategory() != null)
            throw new IllegalArgumentException("Danh mục chỉ hỗ trợ tối đa hai cấp.");
        Category category = Category.builder().name(form.getName().trim()).code(form.getCode().trim())
                .description(form.getDescription()).sortOrder(form.getSortOrder() == null ? 0 : form.getSortOrder())
                .parentCategory(parent).isActive(true).build();
        categories.save(category);
    }
}
