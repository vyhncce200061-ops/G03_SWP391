package com.petshop.service;

import com.petshop.dto.catalog.CategoryFormDto;
import com.petshop.dto.catalog.CategoryViewDto;
import com.petshop.entity.Category;
import com.petshop.repository.CategoryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryManageService {
    private final CategoryRepository categories;

    public List<CategoryViewDto> listCategories() {
        return flatten(tree(false));
    }

    public List<CategoryViewDto> listTree() {
        return tree(false);
    }

    public CategoryViewDto parentForCreation(Integer parentId) {
        Category parent = requireCategory(parentId);
        if (parent.getParentCategory() != null) {
            throw new IllegalArgumentException("Chỉ được thêm danh mục con bên trong một danh mục cha.");
        }
        return view(parent, List.of());
    }

    public List<CategoryViewDto> activeTree() {
        return tree(true);
    }

    public List<Category> activeCategories() {
        List<Category> all = categories.findAllByOrderBySortOrderAscNameAscIdAsc();
        List<Category> result = new ArrayList<>();
        for (Category root : all) {
            if (root.getParentCategory() != null || !Boolean.TRUE.equals(root.getIsActive())) continue;
            result.add(root);
            all.stream().filter(c -> c.getParentCategory() != null
                    && root.getId().equals(c.getParentCategory().getId())
                    && Boolean.TRUE.equals(c.getIsActive())).forEach(result::add);
        }
        return result;
    }

    public List<CategoryViewDto> productOptions(Integer selectedId) {
        // Keep an inactive assignment selectable when editing other product information.
        return listCategories().stream().filter(c -> c.isVisible() || c.getId().equals(selectedId)).toList();
    }

    public List<CategoryViewDto> parentOptions(Integer id) {
        return tree(false).stream().filter(c -> !c.getId().equals(id)).toList();
    }

    public boolean hasChildren(Integer id) {
        return id != null && categories.existsByParentCategory_Id(id);
    }

    public CategoryFormDto editForm(Integer id) {
        Category category = requireCategory(id);
        CategoryFormDto form = new CategoryFormDto();
        form.setCode(category.getCode());
        form.setName(category.getName());
        form.setDescription(category.getDescription());
        form.setSortOrder(category.getSortOrder());
        form.setIsActive(category.getIsActive());
        form.setParentId(category.getParentCategory() == null ? null : category.getParentCategory().getId());
        return form;
    }

    @Transactional
    public Integer save(Integer id, CategoryFormDto form) {
        Category category = id == null ? new Category() : requireCategory(id);
        categories.findByCodeIgnoreCase(form.getCode()).filter(c -> !c.getId().equals(id))
                .ifPresent(c -> { throw new IllegalArgumentException("Mã danh mục đã tồn tại."); });
        Category parent = form.getParentId() == null ? null : requireCategory(form.getParentId());
        if (parent != null) {
            if (Objects.equals(parent.getId(), id)) {
                throw new IllegalArgumentException("Danh mục không thể là cha của chính nó.");
            }
            if (parent.getParentCategory() != null) {
                throw new IllegalArgumentException("Chỉ được chọn danh mục cấp cha; hệ thống hỗ trợ tối đa hai cấp.");
            }
            if (hasChildren(id)) {
                throw new IllegalArgumentException("Danh mục đang có danh mục con nên phải giữ ở cấp cha.");
            }
        }
        category.setCode(form.getCode());
        category.setName(form.getName());
        category.setDescription(form.getDescription());
        category.setSortOrder(form.getSortOrder());
        category.setIsActive(Boolean.TRUE.equals(form.getIsActive()));
        category.setParentCategory(parent);
        categories.saveAndFlush(category);
        return category.getId();
    }

    @Transactional
    public void toggleStatus(Integer id) {
        Category category = requireCategory(id);
        category.setIsActive(!Boolean.TRUE.equals(category.getIsActive()));
        categories.save(category);
    }

    @Transactional
    public void deactivate(Integer id) {
        Category category = requireCategory(id);
        if (Boolean.FALSE.equals(category.getIsActive())) {
            throw new IllegalArgumentException("Danh mục này đã ngừng hoạt động.");
        }
        category.setIsActive(false);
        categories.save(category);
    }

    private Category requireCategory(Integer id) {
        return categories.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Danh mục không tồn tại."));
    }

    private List<CategoryViewDto> tree(boolean activeOnly) {
        List<Category> all = categories.findAllByOrderBySortOrderAscNameAscIdAsc();
        return all.stream().filter(c -> c.getParentCategory() == null)
                .filter(c -> !activeOnly || Boolean.TRUE.equals(c.getIsActive()))
                .map(root -> view(root, all.stream()
                        .filter(child -> child.getParentCategory() != null
                                && root.getId().equals(child.getParentCategory().getId()))
                        .filter(child -> !activeOnly || Boolean.TRUE.equals(child.getIsActive()))
                        .map(child -> view(child, List.of())).toList())).toList();
    }

    private CategoryViewDto view(Category category, List<CategoryViewDto> children) {
        Category parent = category.getParentCategory();
        return new CategoryViewDto(category.getId(), category.getCode(), category.getName(),
                category.getDescription(), category.getSortOrder(), category.getIsActive(),
                parent == null ? null : parent.getId(), parent == null ? null : parent.getName(),
                parent == null || Boolean.TRUE.equals(parent.getIsActive()), children);
    }

    private List<CategoryViewDto> flatten(List<CategoryViewDto> roots) {
        List<CategoryViewDto> result = new ArrayList<>();
        roots.forEach(root -> { result.add(root); result.addAll(root.getChildren()); });
        return result;
    }
}
