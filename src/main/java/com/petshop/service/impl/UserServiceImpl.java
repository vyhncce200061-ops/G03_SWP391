package com.petshop.service.impl;

import com.petshop.dto.auth.AddressCreateDto;
import com.petshop.dto.auth.AddressUpdateDto;
import com.petshop.dto.auth.PasswordChangeRequestDto;
import com.petshop.dto.auth.ProfileUpdateRequestDto;
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
    private final jakarta.persistence.EntityManager entityManager;
    private final com.petshop.service.AccountAccessService accountAccessService;
    private final com.petshop.service.PasswordResetMailer resetMailer;
    private final jakarta.validation.Validator validator;
    private final com.petshop.repository.PasswordResetTokenRepository tokenRepository;

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
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu cập nhật hồ sơ không hợp lệ.");
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.iterator().next().getMessage());
        }
        User user = lockUser(userId);
        user.setFullName(request.getFullName().trim());
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            String phone = request.getPhone().trim();
            userRepository.findByPhone(phone).ifPresent(existing -> {
                if (!existing.getId().equals(userId)) {
                    throw new IllegalArgumentException("Số điện thoại đã được dùng bởi tài khoản khác.");
                }
            });
            user.setPhone(phone);
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }
        userRepository.saveAndFlush(user);
        log.info("Updated profile for user ID: {}", userId);
    }

    @Override
    @Transactional
    public void updateAvatar(Long userId, org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024) {
            throw new IllegalArgumentException("Chọn ảnh đại diện tối đa 5 MB.");
        }
        String type = file.getContentType();
        if (type == null || !java.util.Set.of("image/jpeg", "image/jpg", "image/pjpeg", "image/png", "image/gif").contains(type.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Ảnh đại diện cần có định dạng JPG, PNG hoặc GIF.");
        }
        try (var input = file.getInputStream()) {
            var image = javax.imageio.ImageIO.read(input);
            if (image == null) throw new IllegalArgumentException("Nội dung tệp không phải hình ảnh hợp lệ.");
            User user = lockUser(userId);
            var directory = java.nio.file.Path.of("uploads", "avatars").toAbsolutePath().normalize();
            java.nio.file.Files.createDirectories(directory);
            // Re-encode the image; never trust a client-supplied filename or extension.
            String name = "avatar_" + userId + "_" + java.util.UUID.randomUUID() + ".png";
            var target = directory.resolve(name);
            if (!javax.imageio.ImageIO.write(image, "png", target.toFile())) {
                throw new IllegalStateException("Không thể lưu ảnh đại diện.");
            }
            user.setAvatarUrl("/uploads/avatars/" + name);
            userRepository.saveAndFlush(user);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Không thể lưu ảnh đại diện. Vui lòng thử lại.", e);
        }
    }

    @Override
    @Transactional
    public void changePassword(Long userId, PasswordChangeRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu đổi mật khẩu không hợp lệ.");
        }
        User user = lockUser(userId);
        if (request.getCurrentPassword() == null || !passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không chính xác.");
        }
        if (!java.util.Objects.equals(request.getNewPassword(), request.getConfirmNewPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới xác nhận không khớp.");
        }
        if (request.getNewPassword() == null || request.getNewPassword().isBlank() || request.getNewPassword().length() < 6 || request.getNewPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Mật khẩu mới tối thiểu 6 ký tự.");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        user.setMustChangePassword(false);
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.saveAndFlush(user);
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
        if (request == null) {
            throw new IllegalArgumentException("Thông tin địa chỉ không hợp lệ.");
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.iterator().next().getMessage());
        }
        lockUser(customerId);
        boolean isFirst = addressRepository.countByCustomerIdAndIsDeletedFalse(customerId) == 0;
        boolean makeDefault = isFirst || Boolean.TRUE.equals(request.getIsDefault());

        if (makeDefault) {
            addressRepository.resetDefaultAddresses(customerId);
        }

        User customer = userRepository.getReferenceById(customerId);
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

        return addressRepository.saveAndFlush(address);
    }

    @Override
    @Transactional
    public void updateAddress(Long customerId, Long addressId, AddressUpdateDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Thông tin địa chỉ không hợp lệ.");
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.iterator().next().getMessage());
        }
        lockUser(customerId);
        UserAddress address = addressRepository.findByIdAndCustomerIdAndIsDeletedFalse(addressId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ hợp lệ."));

        boolean shouldMakeDefault = Boolean.TRUE.equals(request.getIsDefault()) && !Boolean.TRUE.equals(address.getIsDefault());
        if (shouldMakeDefault) {
            addressRepository.resetDefaultAddresses(customerId);
            address = addressRepository.findByIdAndCustomerIdAndIsDeletedFalse(addressId, customerId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ hợp lệ."));
            address.setIsDefault(true);
        }

        address.setRecipientName(request.getRecipientName().trim());
        address.setRecipientPhone(request.getRecipientPhone().trim());
        address.setProvince(request.getProvince().trim());
        address.setDistrict(request.getDistrict().trim());
        address.setWard(request.getWard().trim());
        address.setAddressLine(request.getAddressLine().trim());

        addressRepository.saveAndFlush(address);
        log.info("Updated address ID {} for customer ID {}", addressId, customerId);
    }

    @Override
    @Transactional
    public void setDefaultAddress(Long customerId, Long addressId) {
        lockUser(customerId);
        if (addressRepository.findByIdAndCustomerIdAndIsDeletedFalse(addressId, customerId).isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy địa chỉ hợp lệ.");
        }
        addressRepository.resetDefaultAddresses(customerId);
        UserAddress address = addressRepository.findByIdAndCustomerIdAndIsDeletedFalse(addressId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ hợp lệ."));
        address.setIsDefault(true);
        addressRepository.saveAndFlush(address);
    }

    @Override
    @Transactional
    public void deleteAddress(Long customerId, Long addressId) {
        lockUser(customerId);
        UserAddress address = addressRepository.findByIdAndCustomerIdAndIsDeletedFalse(addressId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ hợp lệ."));
        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        address.setIsDeleted(true);
        address.setIsDefault(false);
        addressRepository.saveAndFlush(address);

        if (wasDefault) {
            List<UserAddress> remaining = addressRepository.findByCustomerIdAndIsDeletedFalseOrderByIsDefaultDescCreatedAtDesc(customerId);
            if (!remaining.isEmpty() && remaining.stream().noneMatch(a -> Boolean.TRUE.equals(a.getIsDefault()))) {
                UserAddress nextDefault = remaining.get(0);
                nextDefault.setIsDefault(true);
                addressRepository.saveAndFlush(nextDefault);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<User> getAllUsers(String keyword, String roleCode, String status, Pageable pageable) {
        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        String cleanRoleCode = (roleCode != null && !roleCode.isBlank()) ? roleCode.trim() : null;
        return userRepository.searchUsers(cleanKeyword, cleanRoleCode, (status == null || status.isBlank()) ? null : status.trim().toUpperCase(), pageable);
    }

    @Override
    @Transactional
    public void toggleUserStatus(Long userId, Long adminId) {
        roleRepository.lockAdminRole().orElseThrow(() -> new IllegalStateException("Thiếu vai trò ADMIN."));
        User user = lockUser(userId);
        if ("ACTIVE".equals(user.getStatus()) && "ADMIN".equals(user.getRole().getCode())
                && userRepository.countByRoleCodeAndStatus("ADMIN", "ACTIVE") <= 1) {
            throw new IllegalArgumentException("Phải giữ ít nhất một quản trị viên đang hoạt động.");
        }
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
    public User createStaffAccount(com.petshop.dto.admin.StaffCreateRequestDto request) {
        if (!validator.validate(request).isEmpty()) {
            throw new IllegalArgumentException("Họ tên, email và số điện thoại nhân viên phải hợp lệ.");
        }
        resetMailer.requireConfigured();
        String cleanEmail = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmail(cleanEmail)) {
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
                .email(cleanEmail)
                .phone(request.getPhone().trim())
                .passwordHash(passwordEncoder.encode(com.petshop.service.AccountAccessService.randomSecret()))
                .mustChangePassword(true)
                .role(staffRole)
                .status("ACTIVE")
                .build();

        User saved = userRepository.saveAndFlush(staff);
        accountAccessService.sendLink(saved.getId(), true);
        return saved;
    }

    @Override
    @Transactional
    public void updateUserByAdmin(Long userId, com.petshop.dto.admin.UserAdminUpdateDto request, Long currentAdminId) {
        roleRepository.lockAdminRole().orElseThrow(() -> new IllegalStateException("Thiếu vai trò ADMIN."));
        User user = lockUser(userId);
        if (!java.util.Set.of("ACTIVE", "LOCKED").contains(request.getStatus())) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ.");
        }
        if ("ACTIVE".equals(user.getStatus()) && "ADMIN".equals(user.getRole().getCode())
                && !"ACTIVE".equals(request.getStatus())
                && userRepository.countByRoleCodeAndStatus("ADMIN", "ACTIVE") <= 1) {
            throw new IllegalArgumentException("Phải giữ ít nhất một quản trị viên đang hoạt động.");
        }

        if (user.getId().equals(currentAdminId) && !"ACTIVE".equalsIgnoreCase(request.getStatus())) {
            throw new IllegalArgumentException("Không thể tự khóa tài khoản của chính mình.");
        }

        // Check email uniqueness if modified
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String newEmail = request.getEmail().trim().toLowerCase();
            userRepository.findByEmail(newEmail).ifPresent(existing -> {
                if (!existing.getId().equals(user.getId())) {
                    throw new IllegalArgumentException("Email '" + newEmail + "' đã được sử dụng bởi tài khoản khác.");
                }
            });
            if (!newEmail.equals(user.getEmail())) tokenRepository.revokeUnusedForUser(userId, LocalDateTime.now());
            user.setEmail(newEmail);
        }

        // Check phone uniqueness if modified
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            String newPhone = request.getPhone().trim();
            userRepository.findByPhone(newPhone).ifPresent(existing -> {
                if (!existing.getId().equals(user.getId())) {
                    throw new IllegalArgumentException("Số điện thoại '" + newPhone + "' đã được sử dụng bởi tài khoản khác.");
                }
            });
            user.setPhone(newPhone);
        }

        user.setFullName(request.getFullName().trim());
        user.setStatus(request.getStatus().trim().toUpperCase());
        userRepository.save(user);
        log.info("Admin {} updated details for user {}", currentAdminId, userId);
    }

    @Override
    @Transactional
    public void requestStaffPasswordReset(Long userId, Long currentAdminId) {
        User actor = userRepository.findById(currentAdminId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy quản trị viên."));
        if (!"ADMIN".equals(actor.getRole().getCode()) || !"ACTIVE".equals(actor.getStatus())) {
            throw new IllegalArgumentException("Chỉ quản trị viên đang hoạt động được hỗ trợ mật khẩu Staff.");
        }
        User user = lockUser(userId);
        if (!"STAFF".equals(user.getRole().getCode())) {
            throw new IllegalArgumentException("Chỉ hỗ trợ mật khẩu cho tài khoản Staff. Khách hàng tự dùng Quên mật khẩu.");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalArgumentException("Staff đang bị khóa. Hãy mở khóa trước khi hỗ trợ mật khẩu.");
        }
        if (Boolean.TRUE.equals(user.getMustChangePassword())) {
            throw new IllegalArgumentException("Staff chưa nhận tài khoản. Hãy dùng Gửi lại lời mời.");
        }
        accountAccessService.sendLink(user.getId(), false);
        log.info("Admin {} requested password recovery link for staff {}", currentAdminId, userId);
    }

    @Override
    @Transactional
    public void resendStaffInvitation(Long userId) {
        accountAccessService.sendLink(userId, true);
    }

    private User lockUser(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));
        entityManager.refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return user;
    }
}
