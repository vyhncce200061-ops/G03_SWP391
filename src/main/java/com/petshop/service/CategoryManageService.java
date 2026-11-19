package com.petshop.service;

import com.petshop.dto.catalog.CategoryFormDto;
import com.petshop.dto.catalog.CategoryViewDto;
import com.petshop.entity.Category;
import com.petshop.entity.enums.ProductStatus;
import com.petshop.repository.CategoryRepository;
import com.petshop.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryManageService {
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final EntityManager entityManager;

    public List<CategoryViewDto> listCategories() {
        return flatten(listTree());
    }

    public List<CategoryViewDto> listTree() {
        return tree(categories.findAllByOrderBySortOrderAscNameAscIdAsc());
    }

    public List<CategoryViewDto> activeManagementTree() {
        List<Category> active = categories.findAllByOrderBySortOrderAscNameAscIdAsc().stream()
                .filter(CategoryManageService::isActiveInHierarchy).toList();
        return tree(active);
    }

    public CategoryViewDto parentForCreation(Integer parentId) {
        Category parent = requireCategory(parentId);
        if (parent.getParentCategory() != null) {
            throw new IllegalArgumentException("Chỉ được thêm danh mục con bên trong một danh mục cha.");
        }
        return view(parent, List.of());
    }

    public List<CategoryViewDto> activeTree() {
        return tree(activeCategories());
    }

    public List<Category> activeCategories() {
        return categories.findStorefrontCategories();
    }

    public static boolean isActiveInHierarchy(Category category) {
        return category != null && Boolean.TRUE.equals(category.getIsActive()) && allowsActiveChild(category.getParentCategory());
    }

    public static Predicate activeHierarchyPredicate(CriteriaBuilder cb, Path<Category> category, Path<Category> parent) {
        return cb.and(cb.isTrue(category.get("isActive")), cb.or(cb.isNull(parent.get("id")),
                cb.and(cb.isTrue(parent.get("isActive")), cb.isNull(parent.get("parentCategory")))));
    }

    private static boolean allowsActiveChild(Category parent) {
        return parent == null || (parent.getParentCategory() == null && Boolean.TRUE.equals(parent.getIsActive()));
    }

    public List<CategoryViewDto> productOptions(Integer selectedId) {
        // A root with children serves as a browse group, even when its children are inactive.
        return listCategories().stream().filter(c -> c.getChildren().isEmpty())
                .filter(c -> c.isVisible() || c.getId().equals(selectedId)).toList();
    }

    public long directProductsBeforeFirstChild(Integer parentId) {
        return hasChildren(parentId) ? 0 : products.countByCategory_Id(parentId);
    }

    public void requireLeafForProduct(Category category) {
        if (hasChildren(category.getId())) {
            throw new IllegalArgumentException("Danh mục này đã có nhóm con. Vui lòng chọn danh mục cuối nhánh cho sản phẩm.");
        }
    }

    public List<CategoryViewDto> parentOptions(Integer id) {
        return listTree().stream().filter(c -> !c.getId().equals(id)).toList();
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
        form.setIsActive(isActiveInHierarchy(category));
        form.setParentId(parentId(category));
        return form;
    }

    @Transactional
    public Integer save(Integer id, CategoryFormDto form) {
        Integer destination = form.getParentId();
        if (destination != null && Objects.equals(destination, id)) {
            throw new IllegalArgumentException("Danh mục không thể là cha của chính nó.");
        }
        Category category = id == null ? new Category() : lockForChange(id, destination);
        Category parent = destination == null ? null : (id == null ? lockCategory(destination) : requireCategory(destination));
        categories.findByCodeIgnoreCase(form.getCode()).filter(c -> !c.getId().equals(id))
                .ifPresent(c -> { throw new IllegalArgumentException("Mã danh mục đã tồn tại."); });
        if (parent != null) {
            if (parent.getParentCategory() != null) {
                throw new IllegalArgumentException("Chỉ được chọn danh mục cấp cha; hệ thống hỗ trợ tối đa hai cấp.");
            }
            if (id != null && category.getParentCategory() == null) {
                throw new IllegalArgumentException("Không thể chuyển danh mục cha thành danh mục con.");
            }
            if (hasChildren(id)) {
                throw new IllegalArgumentException("Danh mục đang có danh mục con nên phải giữ ở cấp cha.");
            }
        }
        boolean active = id == null ? allowsActiveChild(parent) : isActiveInHierarchy(category);
        if (form.getIsActive() != null) {
            active = form.getIsActive();
        }
        requireActiveParent(parent, active);
        boolean moveDirectProducts = prepareFirstChild(category, parent, form, active);
        // Clean up old data before reopening a branch. An inactive root may still
        // have children marked active by earlier versions of the application.
        if (id != null && parent == null && (!active || !Boolean.TRUE.equals(category.getIsActive()))) {
            deactivateChildren(id);
        }
        category.setCode(form.getCode());
        category.setName(form.getName());
        category.setDescription(form.getDescription());
        if (form.getSortOrder() != null) {
            category.setSortOrder(form.getSortOrder());
        } else if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }
        category.setIsActive(active);
        category.setParentCategory(parent);
        categories.saveAndFlush(category);
        if (moveDirectProducts) {
            products.moveDirectProducts(parent.getId(), category);
        }
        return category.getId();
    }

    private boolean prepareFirstChild(Category category, Category parent, CategoryFormDto form, boolean active) {
        if (parent == null || Objects.equals(parentId(category), parent.getId()) || hasChildren(parent.getId())
                || !products.existsByCategory_Id(parent.getId())) {
            return false;
        }
        if (category.getId() != null || !form.isMoveDirectProducts()) {
            throw new IllegalArgumentException("Danh mục đang có sản phẩm. Khi thêm danh mục con đầu tiên, hãy tạo nhóm con phù hợp và xác nhận chuyển sản phẩm vào nhóm đó.");
        }
        if (!active && parent.getIsActive() && products.existsByCategory_IdAndStatus(parent.getId(), ProductStatus.ACTIVE)) {
            throw new IllegalArgumentException("Hãy bật danh mục con mới để các sản phẩm đang bán vẫn hiển thị sau khi chuyển.");
        }
        return true;
    }

    @Transactional
    public Integer toggleStatus(Integer id) {
        Category category = lockForChange(id, null);
        return saveStatus(category, !isActiveInHierarchy(category));
    }

    @Transactional
    public Integer setActive(Integer id, boolean active) {
        return saveStatus(lockForChange(id, null), active);
    }

    private Integer saveStatus(Category category, boolean active) {
        Category parent = category.getParentCategory();
        requireActiveParent(parent, active);
        if (parent == null && (!active || !Boolean.TRUE.equals(category.getIsActive()))) {
            deactivateChildren(category.getId());
        }
        category.setIsActive(active);
        return parent == null ? category.getId() : parent.getId();
    }

    private void requireActiveParent(Category parent, boolean active) {
        if (active && !allowsActiveChild(parent)) {
            throw new IllegalArgumentException("Hãy kích hoạt danh mục cha trước khi kích hoạt danh mục con.");
        }
    }

    private void deactivateChildren(Integer rootId) {
        for (Category child : categories.findByParentCategory_IdAndIsActiveTrue(rootId)) {
            entityManager.refresh(child, LockModeType.PESSIMISTIC_WRITE);
            child.setIsActive(false);
        }
    }

    @Transactional
    public Category lockProductCategory(Integer id) {
        try {
            return lockForChange(id, null);
        } catch (TransientDataAccessException ex) {
            throw new IllegalArgumentException("Danh mục vừa thay đổi. Vui lòng tải lại trang rồi chọn danh mục.", ex);
        }
    }

    @Transactional
    public Integer delete(Integer id) {
        Category category = lockForChange(id, null);
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
        entityManager.refresh(category, LockModeType.PESSIMISTIC_WRITE);
        return category;
    }

    private Category lockForChange(Integer id, Integer destination) {
        // A fresh scalar read avoids using an old parent from the persistence context.
        Integer currentParent = categories.findParentId(id);
        if (destination != null && categories.findParentId(destination) != null) {
            throw new IllegalArgumentException("Chỉ được chọn danh mục cấp cha; hệ thống hỗ trợ tối đa hai cấp.");
        }
        // Lock both roots in ID order, then the child. A parent shutdown uses the same order.
        Integer currentRoot = currentParent == null ? id : currentParent;
        Stream.of(currentRoot, destination).filter(Objects::nonNull)
                .distinct().sorted().forEach(this::lockCategory);
        Category category = currentParent == null ? requireCategory(id) : lockCategory(id);
        if (!Objects.equals(currentParent, parentId(category))) {
            throw new OptimisticLockingFailureException("Danh mục vừa được chuyển sang nhánh khác.");
        }
        return category;
    }

    private Integer parentId(Category category) {
        return category.getParentCategory() == null ? null : category.getParentCategory().getId();
    }

    private List<CategoryViewDto> tree(List<Category> all) {
        Map<Integer, List<CategoryViewDto>> children = new LinkedHashMap<>();
        for (Category category : all) {
            Integer parentId = parentId(category);
            if (parentId != null) {
                children.computeIfAbsent(parentId, key -> new ArrayList<>()).add(view(category, List.of()));
            }
        }
        return all.stream().filter(c -> c.getParentCategory() == null)
                .map(root -> view(root, children.getOrDefault(root.getId(), List.of()))).toList();
    }

    private CategoryViewDto view(Category category, List<CategoryViewDto> children) {
        Category parent = category.getParentCategory();
        return new CategoryViewDto(category.getId(), category.getCode(), category.getName(),
                category.getDescription(), category.getSortOrder(), category.getIsActive(),
                parent == null ? null : parent.getId(), parent == null ? null : parent.getName(),
                allowsActiveChild(parent), List.copyOf(children));
    }

    private List<CategoryViewDto> flatten(List<CategoryViewDto> roots) {
        List<CategoryViewDto> result = new ArrayList<>();
        roots.forEach(root -> { result.add(root); result.addAll(root.getChildren()); });
        return result;
    }
}
