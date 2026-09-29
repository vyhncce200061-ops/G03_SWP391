package com.petshop.service.impl;

import com.petshop.dto.auth.RegisterRequestDto;
import com.petshop.entity.Cart;
import com.petshop.entity.PasswordResetToken;
import com.petshop.entity.Role;
import com.petshop.entity.User;
import com.petshop.repository.CartRepository;
import com.petshop.repository.PasswordResetTokenRepository;
import com.petshop.repository.RoleRepository;
import com.petshop.repository.UserRepository;
import com.petshop.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CartRepository cartRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public User registerCustomer(RegisterRequestDto request) {
        log.info("Processing registration for email: {}, phone: {}", request.getEmail(), request.getPhone());

        if (request.getEmail() != null && !request.getEmail().isBlank() && userRepository.existsByEmail(request.getEmail().trim())) {
            throw new IllegalArgumentException("Email đã được sử dụng bởi một tài khoản khác.");
        }

        if (request.getPhone() != null && !request.getPhone().isBlank() && userRepository.existsByPhone(request.getPhone().trim())) {
            throw new IllegalArgumentException("Số điện thoại đã được đăng ký trong hệ thống.");
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
        }

        Role customerRole = roleRepository.findByCode("CUSTOMER")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .code("CUSTOMER")
                        .name("Khách hàng")
                        .createdAt(LocalDateTime.now())
                        .build()));

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(request.getEmail() != null ? request.getEmail().trim().toLowerCase() : null)
                .phone(request.getPhone().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(customerRole)
                .status("ACTIVE")
                .build();

        User savedUser = userRepository.save(user);

        // Auto-initialize 1:1 persistent cart for customer
        Cart cart = Cart.builder()
                .user(savedUser)
                .build();
        cartRepository.save(cart);

        log.info("Registered customer account successfully with ID: {}", savedUser.getId());
        return savedUser;
    }

    @Override
    @Transactional
    public void initiatePasswordReset(String email) {
        log.info("Initiating password reset for email: {}", email);
        userRepository.findByEmail(email.trim().toLowerCase()).ifPresent(user -> {
            String rawToken = UUID.randomUUID().toString();
            String tokenHash = hashToken(rawToken);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(tokenHash)
                    .expiresAt(LocalDateTime.now().plusMinutes(30))
                    .build();
            tokenRepository.save(resetToken);
            log.info("Created reset token for user ID {}. Raw token (simulated email delivery): {}", user.getId(), rawToken);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifyPasswordResetToken(String token) {
        String tokenHash = hashToken(token);
        return tokenRepository.findByTokenHash(tokenHash)
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    @Override
    @Transactional
    public void resetPasswordWithToken(String token, String newPassword) {
        String tokenHash = hashToken(token);
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn."));

        if (resetToken.getUsedAt() != null || resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Token đặt lại mật khẩu đã hết hạn hoặc đã được sử dụng.");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsedAt(LocalDateTime.now());
        tokenRepository.save(resetToken);
        log.info("Password successfully reset for user ID: {}", user.getId());
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
