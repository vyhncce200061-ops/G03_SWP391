package com.petshop.service;

import com.petshop.dto.catalog.CategoryFormDto;
import com.petshop.dto.catalog.CategoryViewDto;
import com.petshop.entity.Category;
import com.petshop.repository.CategoryRepository;
import com.petshop.repository.ProductRepository;
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
    private final ProductRepository products;
    private final jakarta.persistence.EntityManager entityManager;

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

    public boolean isParent(Integer id) {
        if (id == null) return false;
        return categories.findById(id)
                .map(c -> c.getParentCategory() == null)
                .orElse(false);
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
        // Lock the edited category and the proposed parent in one stable order.
        // Re-read their levels after a concurrent move before checking the tree.
        java.util.stream.Stream.of(id, form.getParentId()).filter(Objects::nonNull)
                .distinct().sorted().forEach(this::lockCategory);
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
            if (id != null && category.getParentCategory() == null) {
                throw new IllegalArgumentException("Danh mục cha là cấp cao nhất, không thể thêm danh mục cha mà chỉ có thể thêm danh mục con.");
            }
            if (hasChildren(id)) {
                throw new IllegalArgumentException("Danh mục đang có danh mục con nên phải giữ ở cấp cha.");
            }
        }
        category.setCode(form.getCode());
        category.setName(form.getName());
        category.setDescription(form.getDescription());
        if (form.getSortOrder() != null) {
            category.setSortOrder(form.getSortOrder());
        } else if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }
        category.setIsActive(Boolean.TRUE.equals(form.getIsActive()));
        category.setParentCategory(parent);
        categories.saveAndFlush(category);
        return category.getId();
    }

    @Transactional
    public Integer toggleStatus(Integer id) {
        Category category = lockCategory(id);
        return saveStatus(category, !Boolean.TRUE.equals(category.getIsActive()));
    }

    @Transactional
    public Integer setActive(Integer id, boolean active) {
        return saveStatus(lockCategory(id), active);
    }

    private Integer saveStatus(Category category, boolean active) {
        // Explicit targets keep repeated submissions from reversing the requested action.
        category.setIsActive(active);
        categories.save(category);
        return category.getParentCategory() == null ? category.getId() : category.getParentCategory().getId();
    }

    @Transactional
    public Integer delete(Integer id) {
        Category category = lockCategory(id);
        // Include inactive children and hidden products: their references must be preserved.
        if (categories.existsByParentCategory_Id(id)) {
            throw new IllegalArgumentException("Danh mục còn danh mục con nên không thể xóa. Bạn có thể ngừng hoạt động thay vì xóa.");
        }
        if (products.existsByCategory_Id(id)) {
            throw new IllegalArgumentException("Danh mục còn sản phẩm sử dụng, kể cả sản phẩm đã ẩn hoặc ngừng kinh doanh, nên không thể xóa. Bạn có thể ngừng hoạt động thay vì xóa.");
        }
        Integer parentId = category.getParentCategory() == null ? null : category.getParentCategory().getId();
        categories.delete(category);
        // Surface a concurrent reference conflict before returning success to the controller.
        categories.flush();
        return parentId;
    }

    private Category requireCategory(Integer id) {
        return categories.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Danh mục không tồn tại."));
    }

    private Category lockCategory(Integer id) {
        Category category = categories.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Danh mục không tồn tại."));
        entityManager.refresh(category, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return category;
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
