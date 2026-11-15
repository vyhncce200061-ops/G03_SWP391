package com.petshop.service;

import com.petshop.entity.PasswordResetToken;
import com.petshop.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Existing token table serves both initial password setup and password recovery. */
@Service
@RequiredArgsConstructor
public class AccountAccessService {
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordResetMailer mailer;
    private final EntityManager em;

    @Transactional
    public void sendLink(Long userId, boolean invitation) {
        mailer.requireConfigured();
        var user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        em.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException("Tài khoản cần có email để nhận liên kết đặt mật khẩu.");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalArgumentException("Tài khoản đang bị khóa. Hãy mở khóa trước khi gửi liên kết.");
        }
        if (invitation && (!"STAFF".equals(user.getRole().getCode()) || !Boolean.TRUE.equals(user.getMustChangePassword()))) {
            throw new IllegalArgumentException("Chỉ gửi lại lời mời cho Staff đang chờ đặt mật khẩu.");
        }
        LocalDateTime now = LocalDateTime.now();
        tokens.revokeUnusedForUser(userId, now);
        String rawToken = randomSecret();
        tokens.saveAndFlush(PasswordResetToken.builder().user(user).tokenHash(hash(rawToken))
                .expiresAt(now.plusMinutes(30)).build());
        // Issuing a link never changes a password or interrupts an existing session.
        // SMTP failure rolls back token changes (and account creation for invitations).
        if (invitation) mailer.sendStaffInvitation(user.getEmail(), user.getFullName(), rawToken);
        else mailer.send(user.getEmail(), rawToken);
    }

    public static String randomSecret() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
