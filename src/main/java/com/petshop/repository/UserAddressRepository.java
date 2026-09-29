package com.petshop.repository;

import com.petshop.entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    List<UserAddress> findByCustomerIdAndIsDeletedFalseOrderByIsDefaultDescCreatedAtDesc(Long customerId);
    Optional<UserAddress> findByCustomerIdAndIsDefaultTrueAndIsDeletedFalse(Long customerId);
    Optional<UserAddress> findByIdAndCustomerIdAndIsDeletedFalse(Long id, Long customerId);

    @Modifying
    @Query("UPDATE UserAddress ua SET ua.isDefault = false WHERE ua.customer.id = :customerId")
    void resetDefaultAddresses(@Param("customerId") Long customerId);

    long countByCustomerIdAndIsDeletedFalse(Long customerId);
}
