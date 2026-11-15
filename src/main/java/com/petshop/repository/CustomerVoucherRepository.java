package com.petshop.repository;

import com.petshop.entity.CustomerVoucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerVoucherRepository extends JpaRepository<CustomerVoucher, Long> {

    @Query("SELECT cv FROM CustomerVoucher cv JOIN FETCH cv.voucher v WHERE cv.customer.id = :customerId AND v.isDeleted = false ORDER BY cv.savedAt DESC")
    List<CustomerVoucher> findByCustomerIdWithVoucher(@Param("customerId") Long customerId);

    Optional<CustomerVoucher> findByCustomerIdAndVoucherId(Long customerId, Long voucherId);

    boolean existsByCustomerIdAndVoucherId(Long customerId, Long voucherId);

    long countByCustomerIdAndIsUsedFalse(Long customerId);

    void deleteByCustomerIdAndId(Long customerId, Long id);

    @Modifying
    @Query("UPDATE CustomerVoucher cv SET cv.isUsed = true, cv.usedAt = :usedAt WHERE cv.customer.id = :customerId AND cv.voucher.id = :voucherId")
    int markAsUsed(@Param("customerId") Long customerId, @Param("voucherId") Long voucherId, @Param("usedAt") LocalDateTime usedAt);

    @Modifying
    @Query("UPDATE CustomerVoucher cv SET cv.isUsed = false, cv.usedAt = NULL WHERE cv.customer.id = :customerId AND cv.voucher.id = :voucherId")
    int restoreUnused(@Param("customerId") Long customerId, @Param("voucherId") Long voucherId);
}
