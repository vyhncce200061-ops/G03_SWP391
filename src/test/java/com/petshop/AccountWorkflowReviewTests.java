package com.petshop;

import com.petshop.config.security.CustomAuthenticationSuccessHandler;
import com.petshop.config.security.CustomUserDetails;
import com.petshop.entity.Role;
import com.petshop.entity.User;
import com.petshop.repository.RoleRepository;
import com.petshop.repository.UserRepository;
import com.petshop.service.EmailService;
import com.petshop.service.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:accountreview;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountWorkflowReviewTests {
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserService service;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired CustomAuthenticationSuccessHandler loginSuccess;
    @MockBean EmailService mail;

    private User account(String roleCode) {
        String tag = UUID.randomUUID().toString();
        Role role = roles.findByCode(roleCode).orElseGet(() -> roles.saveAndFlush(Role.builder().code(roleCode).name(roleCode).build()));
        return users.saveAndFlush(User.builder().role(role).email(tag + "@review.test").fullName("Original name")
                .phone("09" + (10000000 + Math.floorMod(tag.hashCode(), 90000000)))
                .passwordHash("original-hash").build());
    }

    @ParameterizedTest
    @CsvSource({"CUSTOMER,/customer/profile", "STAFF,/staff/profile", "ADMIN,/account/profile"})
    void invalidAvatarDoesNotPartiallySaveProfile(String roleCode, String path) throws Exception {
        User original = account(roleCode);
        mvc.perform(multipart(path).file(new MockMultipartFile("avatarFile", "fake.png", "image/png", "invalid image".getBytes()))
                .param("fullName", "Should roll back").param("phone", original.getPhone())
                .with(user(new CustomUserDetails(original))).with(csrf()))
                .andExpect(redirectedUrl(path)).andExpect(flash().attributeExists("errorMessage"));
        assertEquals("Original name", users.findById(original.getId()).orElseThrow().getFullName());
        assertNull(users.findById(original.getId()).orElseThrow().getAvatarUrl());
    }

    @Test
    void rollbackRemovesNewAvatarFileAndPreservesPreviousUrl() throws Exception {
        User original = account("CUSTOMER");
        var buffer = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", buffer);
        var file = new MockMultipartFile("avatarFile", "photo.png", "image/png", buffer.toByteArray());
        String[] created = new String[1];
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.updateAvatar(original.getId(), file);
            created[0] = users.findById(original.getId()).orElseThrow().getAvatarUrl();
            assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of(created[0].substring(1))));
            tx.setRollbackOnly();
        });
        assertNull(users.findById(original.getId()).orElseThrow().getAvatarUrl());
        assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of(created[0].substring(1))));
    }

    @Test
    void recordingLoginPreservesConcurrentPasswordAndStatusChange() {
        User original = account("CUSTOMER");
        new TransactionTemplate(transactions).executeWithoutResult(outer -> {
            em.find(User.class, original.getId());
            var other = new TransactionTemplate(transactions);
            other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            other.executeWithoutResult(tx -> jdbc.update("UPDATE dbo.Users SET Status='LOCKED', PasswordHash='changed-hash' WHERE Id=?", original.getId()));
            var principal = new CustomUserDetails(original);
            try {
                loginSuccess.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(),
                        new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities()));
            } catch (Exception error) {
                throw new AssertionError(error);
            }
        });
        User saved = users.findById(original.getId()).orElseThrow();
        assertEquals("LOCKED", saved.getStatus());
        assertEquals("changed-hash", saved.getPasswordHash());
        assertNotNull(saved.getLastLoginAt());
    }
}
