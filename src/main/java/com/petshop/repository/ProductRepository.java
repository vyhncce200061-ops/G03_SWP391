
package com.petshop.repository;

import com.petshop.entity.Product;
import com.petshop.entity.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>,
        JpaSpecificationExecutor<Product> {

    // Find product by slug
    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByBrand_Id(Integer brandId);

    // Find active products
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    Page<Product> findByStatusAndIsFeaturedTrue(
            ProductStatus status, Pageable pageable);

    // Find all products with category and brand
    @Override
    @EntityGraph(attributePaths = {"category", "brand"})
    Page<Product> findAll(Pageable pageable);

    // Search products by name
    @EntityGraph(attributePaths = {"category", "brand"})
    Page<Product> findByNameContainingIgnoreCase(
            String name, Pageable pageable);

    // Filter products by category
    @EntityGraph(attributePaths = {"category", "brand"})
    Page<Product> findByCategory_Id(
            Integer categoryId, Pageable pageable);

    // Search products by name and category
    @EntityGraph(attributePaths = {"category", "brand"})
    Page<Product> findByNameContainingIgnoreCaseAndCategory_Id(
            String name, Integer categoryId, Pageable pageable);

    // Find all products with category and brand, ordered by newest first
    @EntityGraph(attributePaths = {"category", "brand"})
    @Query(
            value = "SELECT p FROM Product p ORDER BY p.id DESC",
            countQuery = "SELECT COUNT(p) FROM Product p"
    )
    Page<Product> findAllWithDetails(Pageable pageable);

    // Search products by name with category and brand
    @EntityGraph(attributePaths = {"category", "brand"})
    @Query(
            value = "SELECT p FROM Product p " +
                    "WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                    "ORDER BY p.id DESC",
            countQuery = "SELECT COUNT(p) FROM Product p " +
                    "WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))"
    )
    Page<Product> searchWithDetails(
            @Param("keyword") String keyword,
            Pageable pageable);
}
