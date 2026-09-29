package com.petshop.repository;

import com.petshop.entity.Voucher;
import com.petshop.entity.enums.VoucherStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    Optional<Voucher> findByCode(String code);
    Optional<Voucher> findByCodeAndStatus(String code, VoucherStatus status);

    @Query("SELECT v FROM Voucher v WHERE v.status = com.petshop.entity.enums.VoucherStatus.ACTIVE " +
           "AND :now BETWEEN v.startDate AND v.endDate AND v.usedCount < v.totalUsageLimit")
    List<Voucher> findAvailablePublicVouchers(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Voucher v SET v.usedCount = v.usedCount + 1, v.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE v.id = :id AND v.status = com.petshop.entity.enums.VoucherStatus.ACTIVE AND v.usedCount < v.totalUsageLimit " +
           "AND :now BETWEEN v.startDate AND v.endDate")
    int reserveVoucherAtomic(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Voucher v SET v.usedCount = v.usedCount - 1, v.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE v.id = :id AND v.usedCount > 0")
    int restoreVoucherQuota(@Param("id") Long id);
}
