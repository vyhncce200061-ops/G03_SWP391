package com.petshop.repository;

import com.petshop.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Integer> {
    Optional<Brand> findByName(String name);
    boolean existsByName(String name);
    List<Brand> findByIsActiveTrueOrderByNameAsc();
}
