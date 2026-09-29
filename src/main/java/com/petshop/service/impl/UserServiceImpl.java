package com.petshop.service.impl;

import com.petshop.dto.auth.AddressCreateDto;
import com.petshop.dto.auth.PasswordChangeRequestDto;
import com.petshop.dto.auth.ProfileUpdateRequestDto;
import com.petshop.dto.auth.RegisterRequestDto;
import com.petshop.entity.Role;
import com.petshop.entity.User;
import com.petshop.entity.UserAddress;
import com.petshop.repository.RoleRepository;
import com.petshop.repository.UserAddressRepository;
import com.petshop.repository.UserRepository;
import com.petshop.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserAddressRepository addressRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng với ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng với email: " + email));
    }

    @Override
    @Transactional
    public void updateProfile(Long userId, ProfileUpdateRequestDto request) {
        User user = findById(userId);
        user.setFullName(request.getFullName().trim());
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            user.setPhone(request.getPhone().trim());
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }
        userRepository.save(user);
        log.info("Updated profile for user ID: {}", userId);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, PasswordChangeRequestDto request) {
        User user = findById(userId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không chính xác.");
        }
        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới xác nhận không khớp.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Changed password for user ID: {}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAddress> getCustomerAddresses(Long customerId) {
        return addressRepository.findByCustomerIdAndIsDeletedFalseOrderByIsDefaultDescCreatedAtDesc(customerId);
    }

    @Override
    @Transactional
    public UserAddress addAddress(Long customerId, AddressCreateDto request) {
        User customer = findById(customerId);
        boolean isFirst = addressRepository.countByCustomerIdAndIsDeletedFalse(customerId) == 0;
        boolean makeDefault = isFirst || Boolean.TRUE.equals(request.getIsDefault());

        if (makeDefault) {
            addressRepository.resetDefaultAddresses(customerId);
        }

        UserAddress address = UserAddress.builder()
                .customer(customer)
                .recipientName(request.getRecipientName().trim())
                .recipientPhone(request.getRecipientPhone().trim())
                .province(request.getProvince().trim())
                .district(request.getDistrict().trim())
                .ward(request.getWard().trim())
                .addressLine(request.getAddressLine().trim())
                .isDefault(makeDefault)
                .isDeleted(false)
                .build();

        return addressRepository.save(address);
    }

    @Override
    @Transactional
    public void setDefaultAddress(Long customerId, Long addressId) {
        UserAddress address = addressRepository.findByIdAndCustomerIdAndIsDeletedFalse(addressId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ hợp lệ."));
        addressRepository.resetDefaultAddresses(customerId);
        address.setIsDefault(true);
        addressRepository.save(address);
    }

    @Override
    @Transactional
    public void deleteAddress(Long customerId, Long addressId) {
        UserAddress address = addressRepository.findByIdAndCustomerIdAndIsDeletedFalse(addressId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ hợp lệ."));
        address.setIsDeleted(true);
        address.setIsDefault(false);
        addressRepository.save(address);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<User> getAllUsers(String keyword, String roleCode, Pageable pageable) {
        if (roleCode != null && !roleCode.isBlank()) {
            return userRepository.findByRoleCode(roleCode.trim(), pageable);
        }
        if (keyword != null && !keyword.isBlank()) {
            return userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(keyword.trim(), keyword.trim(), pageable);
        }
        return userRepository.findAll(pageable);
    }

    @Override
    @Transactional
    public void toggleUserStatus(Long userId, Long adminId) {
        User user = findById(userId);
        if (user.getId().equals(adminId)) {
            throw new IllegalArgumentException("Không thể tự khóa tài khoản của chính mình.");
        }
        String newStatus = "ACTIVE".equalsIgnoreCase(user.getStatus()) ? "LOCKED" : "ACTIVE";
        user.setStatus(newStatus);
        userRepository.save(user);
        log.info("Admin {} changed status of user {} to {}", adminId, userId, newStatus);
    }

    @Override
    @Transactional
    public User createStaffAccount(RegisterRequestDto request) {
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail().trim())) {
            throw new IllegalArgumentException("Email nhân viên đã tồn tại.");
        }
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone().trim())) {
            throw new IllegalArgumentException("Số điện thoại nhân viên đã tồn tại.");
        }

        Role staffRole = roleRepository.findByCode("STAFF")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .code("STAFF")
                        .name("Nhân viên")
                        .createdAt(LocalDateTime.now())
                        .build()));

        User staff = User.builder()
                .fullName(request.getFullName().trim())
                .email(request.getEmail() != null ? request.getEmail().trim().toLowerCase() : null)
                .phone(request.getPhone().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(staffRole)
                .status("ACTIVE")
                .build();

        return userRepository.save(staff);
    }
}
