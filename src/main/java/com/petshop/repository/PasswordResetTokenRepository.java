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
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PasswordResetToken t WHERE t.tokenHash = :hash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("hash") String hash);
    Optional<PasswordResetToken> findByUserIdAndUsedAtIsNullAndExpiresAtAfter(Long userId, LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.user.id = :userId AND t.usedAt IS NULL AND t.expiresAt > :now")
    int revokeUnusedForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(t) > 0 FROM PasswordResetToken t WHERE t.tokenHash = :hash AND t.usedAt IS NULL AND t.expiresAt > :now AND t.user.status = 'ACTIVE'")
    boolean isUsable(@Param("hash") String hash, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM PasswordResetToken prt WHERE prt.expiresAt < :now OR prt.usedAt IS NOT NULL")
    void purgeExpiredOrUsedTokens(@Param("now") LocalDateTime now);
}
