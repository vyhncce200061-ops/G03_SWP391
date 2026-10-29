package com.petshop;

import com.petshop.dto.admin.StaffCreateRequestDto;
import com.petshop.entity.*;
import com.petshop.repository.*;
import com.petshop.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:mailrollback;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@ActiveProfiles("test")
class StaffMailRollbackTests {
    @Autowired UserService service;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockBean EmailService mail;

    @Test void smtpFailureRollsBackNewAccountAndPreservesExistingAccessLink() {
        jdbc.execute("ALTER TABLE dbo.PasswordResetTokens ADD CONSTRAINT CK_PwdReset_Expires CHECK (ExpiresAt > CreatedAt)");
        var role=roles.save(Role.builder().code("STAFF").name("Staff").build());
        var adminRole=roles.save(Role.builder().code("ADMIN").name("Admin").build());
        var admin=users.save(User.builder().role(adminRole).fullName("Admin").email("admin@rollback.test").phone("0901000010")
                .passwordHash(encoder.encode("Admin@123")).build());
        doThrow(new IllegalStateException("SMTP rejected")).when(mail).sendStaffInvitation(anyString(),anyString(),anyString());
        var dto=StaffCreateRequestDto.builder().fullName("Staff failure").email("failure@rollback.test").phone("0901000008").build();
        assertThrows(IllegalStateException.class,()->service.createStaffAccount(dto));
        assertFalse(users.existsByEmail(dto.getEmail())); assertEquals(0,tokens.count());

        var original=users.save(User.builder().role(role).fullName("Existing staff").email("existing@rollback.test").phone("0901000009")
                .passwordHash(encoder.encode("Old@123")).build());
        doThrow(new IllegalStateException("SMTP rejected")).when(mail).sendPasswordResetEmail(anyString(),anyString(),anyString());
        assertThrows(IllegalStateException.class,()->auth.initiatePasswordReset(original.getEmail()));
        var unchanged=users.findById(original.getId()).orElseThrow();
        assertEquals(original.getPasswordHash(),unchanged.getPasswordHash());
        assertFalse(unchanged.getMustChangePassword()); assertEquals(0,tokens.count());
        // Keep the SQL Server expiry constraint valid even on immediate resend.
        reset(mail);
        var pending=service.createStaffAccount(dto);
        service.resendStaffInvitation(pending.getId());
        assertEquals(2,tokens.count());
        assertEquals(1,tokens.findAll().stream().filter(t->t.getUsedAt()!=null).count());
        assertTrue(tokens.findAll().stream().allMatch(t->t.getExpiresAt().isAfter(t.getCreatedAt())));
        auth.initiatePasswordReset(original.getEmail());
        var previous=tokens.findAll().stream().filter(t->t.getUser().getId().equals(original.getId())).findFirst().orElseThrow();
        doThrow(new IllegalStateException("SMTP rejected")).when(mail).sendPasswordResetEmail(anyString(),anyString(),anyString());
        assertThrows(IllegalStateException.class,()->auth.initiatePasswordReset(original.getEmail()));
        assertEquals(3,tokens.count());
        assertNull(tokens.findById(previous.getId()).orElseThrow().getUsedAt());
        assertEquals(original.getPasswordHash(),users.findById(original.getId()).orElseThrow().getPasswordHash());
    }
}
