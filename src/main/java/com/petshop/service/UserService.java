package com.petshop.service;

import com.petshop.dto.auth.AddressCreateDto;
import com.petshop.dto.auth.PasswordChangeRequestDto;
import com.petshop.dto.auth.ProfileUpdateRequestDto;
import com.petshop.dto.auth.RegisterRequestDto;
import com.petshop.entity.User;
import com.petshop.entity.UserAddress;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService {
    User findById(Long id);
    User findByEmail(String email);
    void updateProfile(Long userId, ProfileUpdateRequestDto request);
    void changePassword(Long userId, PasswordChangeRequestDto request);

    List<UserAddress> getCustomerAddresses(Long customerId);
    UserAddress addAddress(Long customerId, AddressCreateDto request);
    void setDefaultAddress(Long customerId, Long addressId);
    void deleteAddress(Long customerId, Long addressId);

    Page<User> getAllUsers(String keyword, String roleCode, Pageable pageable);
    void toggleUserStatus(Long userId, Long adminId);
    User createStaffAccount(RegisterRequestDto request);
}
