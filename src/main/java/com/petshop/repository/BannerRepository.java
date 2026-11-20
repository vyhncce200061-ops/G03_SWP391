package com.petshop.repository;

import com.petshop.entity.Banner;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BannerRepository extends JpaRepository<Banner, Integer> {
    List<Banner> findAllByIsDeletedFalseOrderBySortOrderAscIdAsc();

    java.util.Optional<Banner> findByIdAndIsDeletedFalse(Integer id);

    @Query("""
            SELECT b FROM Banner b
            WHERE b.isDeleted = false
              AND b.isActive = true
              AND (b.startAt IS NULL OR b.startAt <= :now)
              AND (b.endAt IS NULL OR b.endAt > :now)
            ORDER BY b.sortOrder ASC, b.id ASC
            """)
    List<Banner> findDisplayable(@Param("now") LocalDateTime now, Pageable pageable);
}
