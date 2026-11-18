
package com.petshop.repository;

import com.petshop.entity.Category;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    Optional<Category> findByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Category c WHERE c.id = :id")
    Optional<Category> findByIdForUpdate(@Param("id") Integer id);

    @Query("SELECT parent.id FROM Category c LEFT JOIN c.parentCategory parent WHERE c.id = :id")
    Integer findParentId(@Param("id") Integer id);

    List<Category> findByParentCategory_IdAndIsActiveTrue(Integer parentId);

    @EntityGraph(attributePaths = "parentCategory")
    List<Category> findAllByOrderBySortOrderAscNameAscIdAsc();

    @EntityGraph(attributePaths = "parentCategory")
    @Query("""
            SELECT c FROM Category c LEFT JOIN c.parentCategory parent
            WHERE c.isActive = true AND (parent.id IS NULL OR (
                parent.parentCategory IS NULL AND parent.isActive = true AND EXISTS (
                    SELECT p.id FROM Product p WHERE p.category = c AND p.status = com.petshop.entity.enums.ProductStatus.ACTIVE
                )
            ))
            ORDER BY c.sortOrder, c.name, c.id
            """)
    List<Category> findStorefrontCategories();
}
