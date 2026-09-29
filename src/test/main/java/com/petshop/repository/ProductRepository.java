package com.petshop.repository;

import com.petshop.entity.Product;
import com.petshop.entity.enums.PetType;
import com.petshop.entity.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Optional<Product> findBySlug(String slug);
    boolean existsBySlug(String slug);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);
    Page<Product> findByStatusAndIsFeaturedTrue(ProductStatus status, Pageable pageable);
    Page<Product> findByCategoryIdAndStatus(Integer categoryId, ProductStatus status, Pageable pageable);
    Page<Product> findByBrandIdAndStatus(Integer brandId, ProductStatus status, Pageable pageable);
    Page<Product> findByPetTypeAndStatus(PetType petType, ProductStatus status, Pageable pageable);

    @Query("SELECT p FROM Product p " +
           "LEFT JOIN FETCH p.images " +
           "LEFT JOIN FETCH p.variants v " +
           "LEFT JOIN FETCH p.category " +
           "LEFT JOIN FETCH p.brand " +
           "WHERE p.slug = :slug AND p.status <> com.petshop.entity.enums.ProductStatus.DISCONTINUED")
    Optional<Product> findDetailedBySlug(@Param("slug") String slug);
}
