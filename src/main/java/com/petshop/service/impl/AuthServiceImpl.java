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

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CartRepository cartRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.petshop.service.PasswordResetMailer resetMailer;
    private final com.petshop.service.AccountAccessService accountAccessService;
    private final jakarta.persistence.EntityManager entityManager;
    private final com.petshop.config.security.JwtService registrationTokens;
    private final com.petshop.service.EmailService emailService;
    private final jakarta.validation.Validator validator;
    @org.springframework.beans.factory.annotation.Value("${petshop.public-base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    @Transactional(readOnly = true)
    public void startRegistration(RegisterRequestDto request) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new IllegalArgumentException(violations.iterator().next().getMessage());
        if (!request.getPassword().equals(request.getConfirmPassword())
                || request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp hoặc vượt quá 72 byte.");
        }
        String email = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        String phone = request.getPhone().trim();
        if (userRepository.existsByEmail(email) || userRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("Email hoặc số điện thoại đã được đăng ký.");
        }
        emailService.requireConfigured();
        String token = registrationTokens.generateRegistrationToken(request.getFullName().trim(), email, phone,
                passwordEncoder.encode(request.getPassword()), 24L * 60 * 60 * 1000);
        String link = org.springframework.web.util.UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/verify-email").queryParam("token", token).build().encode().toUriString();
        emailService.sendRegistrationConfirmation(email, request.getFullName().trim(), link);
    }

    @Override
    @Transactional
    public User confirmRegistration(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Liên kết xác nhận không hợp lệ hoặc đã hết hạn. Vui lòng đăng ký lại.");
        }
        io.jsonwebtoken.Claims claims;
        try {
            claims = registrationTokens.parseRegistrationToken(token);
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Liên kết xác nhận không hợp lệ hoặc đã hết hạn. Vui lòng đăng ký lại.");
        }
        String email = claims.getSubject(), phone = claims.get("phone", String.class);
        if (userRepository.existsByEmail(email) || userRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("Tài khoản đã được kích hoạt hoặc email/SĐT đã được sử dụng. Vui lòng đăng nhập.");
        }
        Role role = roleRepository.findByCode("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("Thiếu vai trò CUSTOMER."));
        User customer = userRepository.saveAndFlush(User.builder().role(role).fullName(claims.get("fullName", String.class))
                .email(email).phone(phone).passwordHash(claims.get("pwd", String.class)).status("ACTIVE").build());
        cartRepository.saveAndFlush(Cart.builder().user(customer).build());
        return customer;
    }

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

        if (request.getPassword() == null || request.getPassword().isBlank() || request.getPassword().length() < 6
                || request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || !request.getPassword().equals(request.getConfirmPassword())) {
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
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập địa chỉ email.");
        }
        resetMailer.requireConfigured();
        log.info("Initiating password reset for email: {}", email);
        userRepository.findByEmail(email.trim().toLowerCase()).filter(user -> "ACTIVE".equals(user.getStatus())).ifPresent(user -> {
            accountAccessService.sendLink(user.getId(), false);
            log.info("Sent password reset instructions for user ID {}", user.getId());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifyPasswordResetToken(String token) {
        return token != null && !token.isBlank() && tokenRepository.isUsable(hashToken(token), LocalDateTime.now());
    }

    @Override
    @Transactional
    public void resetPasswordWithToken(String token, String newPassword) {
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 6
                || newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Mật khẩu tối thiểu 6 ký tự, tối đa 72 byte UTF-8.");
        }
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Liên kết không hợp lệ.");
        String tokenHash = hashToken(token);
        PasswordResetToken candidate = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn."));
        User user = userRepository.findByIdForUpdate(candidate.getUser().getId()).orElseThrow();
        entityManager.refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        PasswordResetToken resetToken = tokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn."));
        entityManager.refresh(resetToken, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        if (resetToken.getUsedAt() != null || !resetToken.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Token đặt lại mật khẩu đã hết hạn hoặc đã được sử dụng.");
        }

        if (!"ACTIVE".equals(user.getStatus())) throw new IllegalArgumentException("Tài khoản đang bị khóa. Vui lòng liên hệ Admin.");
        user.setMustChangePassword(false);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsedAt(LocalDateTime.now());
        tokenRepository.saveAndFlush(resetToken);
        tokenRepository.revokeUnusedForUser(user.getId(), LocalDateTime.now());
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
