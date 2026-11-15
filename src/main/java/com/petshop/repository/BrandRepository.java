
package com.petshop.repository;

import com.petshop.entity.Brand;
import com.petshop.entity.enums.ProductStatus;
import com.petshop.entity.enums.ProductVariantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Integer> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Brand b WHERE b.id = :id")
    java.util.Optional<Brand> findByIdForUpdate(@Param("id") Integer id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    List<Brand> findByIsActiveTrueOrderByNameAsc();

    List<Brand> findAllByOrderByNameAsc();

    @Query("""
            select b from Brand b where b.isActive = true and exists (
                select p.id from Product p join p.variants v
                where p.brand = b and p.status = :productStatus and v.status = :variantStatus
            ) order by b.name
            """)
    List<Brand> findStorefrontBrands(@Param("productStatus") ProductStatus productStatus,
                                    @Param("variantStatus") ProductVariantStatus variantStatus);
}
