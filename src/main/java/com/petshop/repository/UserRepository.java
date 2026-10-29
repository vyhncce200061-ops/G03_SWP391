package com.petshop.repository;

import com.petshop.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    Optional<User> findByEmailOrPhone(String email, String phone);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    long countByRoleCodeAndStatus(String roleCode, String status);

    Page<User> findByRoleCode(String roleCode, Pageable pageable);
    Page<User> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE " +
           "(:status IS NULL OR u.status = :status) AND " +
           "(:roleCode IS NULL OR :roleCode = '' OR u.role.code = :roleCode) AND " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "u.phone LIKE CONCAT('%', :keyword, '%'))")
    Page<User> searchUsers(@org.springframework.data.repository.query.Param("keyword") String keyword,
                           @org.springframework.data.repository.query.Param("roleCode") String roleCode,
                           @org.springframework.data.repository.query.Param("status") String status,
                           Pageable pageable);

    long countByRoleCode(String roleCode);
    long countByStatus(String status);
    long countByCreatedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);
}
