
package com.petshop.repository;

import com.petshop.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Integer> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    List<Brand> findByIsActiveTrueOrderByNameAsc();

    List<Brand> findAllByOrderByNameAsc();
}
