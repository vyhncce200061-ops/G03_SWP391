package com.petshop.repository;

import com.petshop.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    Optional<PasswordResetToken> findByUserIdAndUsedAtIsNullAndExpiresAtAfter(Long userId, LocalDateTime now);

    @Modifying
    @Query("DELETE FROM PasswordResetToken prt WHERE prt.expiresAt < :now OR prt.usedAt IS NOT NULL")
    void purgeExpiredOrUsedTokens(@Param("now") LocalDateTime now);
}
