
package com.petshop.repository;

import com.petshop.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Integer> {

    // Check whether a category code already exists
    boolean existsByCode(String code);
    boolean existsByParentCategory_Id(Integer parentId);

    // Find a category by its code
    Optional<Category> findByCode(String code);

    // Get active root categories ordered by sort order
    List<Category> findByParentCategoryIsNullAndIsActiveTrueOrderBySortOrderAsc();

    // Get categories by status
    List<Category> findByIsActive(Boolean isActive);
    List<Category> findByParentCategoryIdAndIsActiveTrueOrderBySortOrderAsc(Integer parentId);
    List<Category> findByIsActiveTrueOrderBySortOrderAsc();

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT c FROM Category c LEFT JOIN FETCH c.subCategories WHERE c.parentCategory IS NULL AND c.isActive = true ORDER BY c.sortOrder ASC")
    List<Category> findAllActiveHierarchy();
}
