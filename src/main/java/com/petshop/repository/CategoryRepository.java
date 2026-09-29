package com.petshop.repository;

import com.petshop.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Integer> {
    Optional<Category> findByCode(String code);
    boolean existsByCode(String code);

    List<Category> findByParentCategoryIsNullAndIsActiveTrueOrderBySortOrderAsc();
    List<Category> findByParentCategoryIdAndIsActiveTrueOrderBySortOrderAsc(Integer parentId);
    List<Category> findByIsActiveTrueOrderBySortOrderAsc();

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.subCategories WHERE c.parentCategory IS NULL AND c.isActive = true ORDER BY c.sortOrder ASC")
    List<Category> findAllActiveHierarchy();
}
