package com.petshop;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.admin.UserAdminUpdateDto;
import com.petshop.dto.auth.ProfileUpdateRequestDto;
import com.petshop.entity.PasswordResetToken;
import com.petshop.entity.Role;
import com.petshop.entity.User;
import com.petshop.repository.PasswordResetTokenRepository;
import com.petshop.repository.RoleRepository;
import com.petshop.repository.UserRepository;
import com.petshop.service.EmailService;
import com.petshop.service.UserService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:adminAccountPolicy;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.jpa.open-in-view=false", "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=WARN",
        "petshop.browser.auto-open=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminAccountPolicyTests {
    private static final AtomicInteger NEXT_PHONE = new AtomicInteger(12300000);
    private static final String PASSWORD = "Policy@12345";
    private static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);
    @Autowired MockMvc mvc;
    @Autowired UserService service;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired AuthenticationManager authentication;
    @MockBean EmailService mail;
    User admin, staff, customer;

    @BeforeEach void fixture() {
        tokens.deleteAllInBatch();
        users.deleteAllInBatch();
        roles.deleteAllInBatch();
        admin = account("ADMIN");
        staff = account("STAFF");
        customer = account("CUSTOMER");
    }

    private User account(String roleCode) {
        Role role = roles.findByCode(roleCode).orElseGet(() -> roles.saveAndFlush(
                Role.builder().code(roleCode).name(roleCode).build()));
        User created = users.saveAndFlush(User.builder().role(role).email(UUID.randomUUID() + "@policy.test")
                .fullName("Original " + roleCode).phone("09" + NEXT_PHONE.getAndIncrement())
                .passwordHash(HASH).build());
        return users.findById(created.getId()).orElseThrow();
    }

    private User target(String roleCode) { return "ADMIN".equals(roleCode) ? admin : "CUSTOMER".equals(roleCode) ? customer : staff; }
    private String editPath(User target) { return "/admin/users/" + target.getId() + "/edit"; }
    private String statusPath(User target) { return "/admin/users/" + target.getId() + "/toggle-status"; }
    private User saved(User original) { return users.findById(original.getId()).orElseThrow(); }
    private UserAdminUpdateDto name(String value) { return UserAdminUpdateDto.builder().fullName(value).build(); }

    private void assertIdentityUnchanged(User before) {
        User after = saved(before);
        assertEquals(before.getId(), after.getId());
        assertEquals(before.getEmail(), after.getEmail());
        assertEquals(before.getPhone(), after.getPhone());
        assertEquals(before.getPasswordHash(), after.getPasswordHash());
        assertEquals(before.getRole().getCode(), after.getRole().getCode());
        assertEquals(before.getMustChangePassword(), after.getMustChangePassword());
        assertEquals(before.getAvatarUrl(), after.getAvatarUrl());
        assertEquals(before.getCreatedAt(), after.getCreatedAt());
    }

    @Test void staffNameEditPreservesLoginIdentifiersAndPassword() throws Exception {
        mvc.perform(post(editPath(staff)).with(user(new CustomUserDetails(admin))).with(csrf())
                        .param("fullName", "  Nguyễn Thị Cẩm Tú  "))
                .andExpect(redirectedUrl("/admin/users")).andExpect(flash().attributeExists("successMessage"));
        assertEquals("Nguyễn Thị Cẩm Tú", saved(staff).getFullName());
        assertIdentityUnchanged(staff);
        assertEquals("ACTIVE", saved(staff).getStatus());
        for (String identifier : List.of(staff.getEmail(), staff.getPhone())) {
            var authenticated = authentication.authenticate(new UsernamePasswordAuthenticationToken(identifier, PASSWORD));
            assertEquals(staff.getId(), ((CustomUserDetails) authenticated.getPrincipal()).getId());
        }
        verifyNoInteractions(mail);
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "phone", "status", "roleCode", "roleId", "role.code", "password", "passwordHash", "mustChangePassword", "id", "avatarUrl"})
    void forgedProtectedFieldsRejectTheEntireEdit(String field) throws Exception {
        mvc.perform(post(editPath(staff)).with(user(new CustomUserDetails(admin))).with(csrf())
                        .param("fullName", "Must not be saved").param(field, "attacker"))
                .andExpect(status().isOk()).andExpect(view().name("admin/user-edit"))
                .andExpect(model().attributeHasErrors("userForm"));
        assertEquals(staff.getFullName(), saved(staff).getFullName());
        assertEquals("ACTIVE", saved(staff).getStatus());
        assertIdentityUnchanged(staff);
        assertIdentityUnchanged(customer);
        verifyNoInteractions(mail);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "ADMIN"})
    void customerAndAdminAccountsHaveNoAdminEditOperation(String roleCode) throws Exception {
        User target = target(roleCode);
        mvc.perform(get(editPath(target)).with(user(new CustomUserDetails(admin))))
                .andExpect(redirectedUrl("/admin/users/" + target.getId()));
        mvc.perform(post(editPath(target)).with(user(new CustomUserDetails(admin))).with(csrf()).param("fullName", "Forbidden"))
                .andExpect(redirectedUrl("/admin/users/" + target.getId())).andExpect(flash().attributeExists("errorMessage"));
        assertThrows(IllegalArgumentException.class, () -> service.updateUserByAdmin(target.getId(), name("Forbidden"), admin.getId()));
        assertEquals(target.getFullName(), saved(target).getFullName());
        assertIdentityUnchanged(target);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "STAFF", "ADMIN"})
    void accountDetailsAreReadOnlyAndNeverRenderCredentials(String roleCode) throws Exception {
        User target = target(roleCode);
        String html = mvc.perform(get("/admin/users/" + target.getId()).with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk()).andExpect(model().attribute("canEditStaff", false))
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains(target.getEmail()));
        assertTrue(html.contains(target.getPhone()));
        for (String field : List.of("fullName", "email", "phone", "status", "password", "passwordHash", "roleCode")) {
            assertFalse(html.contains("name=\"" + field + "\""));
        }
        assertFalse(html.contains(HASH));
    }

    @Test void listOnlyOffersEditForStaffAndHidesSelfLock() throws Exception {
        String html = mvc.perform(get("/admin/users").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("href=\"/admin/users/" + customer.getId() + "\""));
        assertTrue(html.contains("href=\"" + editPath(staff) + "\""));
        assertFalse(html.contains("href=\"" + editPath(customer) + "\""));
        assertFalse(html.contains("href=\"" + editPath(admin) + "\""));
        assertFalse(html.contains("action=\"" + statusPath(admin) + "\""));
        assertTrue(html.contains("name=\"targetStatus\""));
    }

    @Test void editFormOnlySubmitsStaffNameAndCsrf() throws Exception {
        String html = mvc.perform(get(editPath(staff)).with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk()).andExpect(model().attribute("canEditStaff", true))
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("name=\"fullName\""));
        assertTrue(html.contains("name=\"_csrf\""));
        for (String field : List.of("email", "phone", "status", "targetStatus", "id", "roleId", "passwordHash")) {
            assertFalse(html.contains("name=\"" + field + "\""));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\u00a0", "\u200b", "Tên\nkhông hợp lệ"})
    void invalidNamesCannotMutateStaff(String invalidName) throws Exception {
        mvc.perform(post(editPath(staff)).with(user(new CustomUserDetails(admin))).with(csrf()).param("fullName", invalidName))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("userForm", "fullName"));
        assertThrows(IllegalArgumentException.class, () -> service.updateUserByAdmin(staff.getId(), name(invalidName), admin.getId()));
        assertEquals(staff.getFullName(), saved(staff).getFullName());
        assertIdentityUnchanged(staff);
    }

    @Test void nameLengthMatchesTheDatabaseLimit() throws Exception {
        mvc.perform(post(editPath(staff)).with(user(new CustomUserDetails(admin))).with(csrf()).param("fullName", "N".repeat(151)))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("userForm", "fullName"));
        assertEquals(staff.getFullName(), saved(staff).getFullName());
        mvc.perform(post(editPath(staff)).with(user(new CustomUserDetails(admin))).with(csrf()).param("fullName", "N".repeat(150)))
                .andExpect(redirectedUrl("/admin/users"));
        assertEquals("N".repeat(150), saved(staff).getFullName());
    }

    @Test void savingPreviouslyOpenedNameFormDoesNotUnlockStaff() throws Exception {
        mvc.perform(get(editPath(staff)).with(user(new CustomUserDetails(admin)))).andExpect(status().isOk());
        service.setUserStatus(staff.getId(), "LOCKED", admin.getId());
        mvc.perform(post(editPath(staff)).with(user(new CustomUserDetails(admin))).with(csrf()).param("fullName", "Updated name"))
                .andExpect(redirectedUrl("/admin/users"));
        assertEquals("Updated name", saved(staff).getFullName());
        assertEquals("LOCKED", saved(staff).getStatus());
        assertIdentityUnchanged(staff);
        mvc.perform(post(editPath(staff)).with(user(new CustomUserDetails(admin))).with(csrf())
                        .param("fullName", "Old form").param("status", "ACTIVE"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("userForm"));
        assertEquals("LOCKED", saved(staff).getStatus());
        assertEquals("Updated name", saved(staff).getFullName());
    }

    @Test void editingPendingStaffNameKeepsTheOriginalInvitationToken() {
        staff.setMustChangePassword(true);
        staff = users.saveAndFlush(staff);
        var token = tokens.saveAndFlush(PasswordResetToken.builder().user(staff)
                .tokenHash("a".repeat(64)).expiresAt(LocalDateTime.now().plusMinutes(30)).build());
        service.updateUserByAdmin(staff.getId(), name("Corrected name"), admin.getId());
        assertTrue(tokens.isUsable(token.getTokenHash(), LocalDateTime.now()));
        assertNull(tokens.findById(token.getId()).orElseThrow().getUsedAt());
        assertEquals(1, tokens.count());
        assertIdentityUnchanged(staff);
        verifyNoInteractions(mail);
    }

    @Test void repeatedLockAndUnlockRequestsAreIdempotent() throws Exception {
        for (String targetStatus : List.of("LOCKED", "LOCKED", "ACTIVE", "ACTIVE")) {
            mvc.perform(post(statusPath(customer)).with(user(new CustomUserDetails(admin))).with(csrf()).param("targetStatus", targetStatus))
                    .andExpect(redirectedUrl("/admin/users")).andExpect(flash().attributeExists("successMessage"));
            assertEquals(targetStatus, saved(customer).getStatus());
        }
        assertIdentityUnchanged(customer);
    }

    @Test void invalidOrMissingStatusCannotChangeTheAccount() throws Exception {
        mvc.perform(post(statusPath(customer)).with(user(new CustomUserDetails(admin))).with(csrf()).param("targetStatus", "DELETED"))
                .andExpect(redirectedUrl("/admin/users")).andExpect(flash().attributeExists("errorMessage"));
        mvc.perform(post(statusPath(customer)).with(user(new CustomUserDetails(admin))).with(csrf()))
                .andExpect(redirectedUrl("/admin/users")).andExpect(flash().attributeExists("errorMessage"));
        assertEquals("ACTIVE", saved(customer).getStatus());
        assertThrows(IllegalArgumentException.class, () -> service.setUserStatus(customer.getId(), null, admin.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "STAFF"})
    void nonAdminsCannotViewOrMutateAdminAccounts(String roleCode) throws Exception {
        User actor = target(roleCode);
        for (String path : List.of("/admin/users", "/admin/users/" + customer.getId(), editPath(staff))) {
            mvc.perform(get(path).with(user(new CustomUserDetails(actor)))).andExpect(redirectedUrl("/error/403"));
        }
        mvc.perform(post(editPath(staff)).with(user(new CustomUserDetails(actor))).with(csrf()).param("fullName", "Forbidden"))
                .andExpect(redirectedUrl("/error/403"));
        mvc.perform(post(statusPath(customer)).with(user(new CustomUserDetails(actor))).with(csrf()).param("targetStatus", "LOCKED"))
                .andExpect(redirectedUrl("/error/403"));
        assertThrows(AccessDeniedException.class, () -> service.updateUserByAdmin(staff.getId(), name("Forbidden"), actor.getId()));
        assertThrows(AccessDeniedException.class, () -> service.setUserStatus(customer.getId(), "LOCKED", actor.getId()));
        assertEquals("ACTIVE", saved(customer).getStatus());
        assertEquals(staff.getFullName(), saved(staff).getFullName());
    }

    @Test void lockedOrMissingAdminCannotUseTheService() {
        admin.setStatus("LOCKED");
        users.saveAndFlush(admin);
        for (Long actorId : List.of(admin.getId(), Long.MAX_VALUE)) {
            assertThrows(AccessDeniedException.class, () -> service.updateUserByAdmin(staff.getId(), name("Forbidden"), actorId));
            assertThrows(AccessDeniedException.class, () -> service.setUserStatus(customer.getId(), "LOCKED", actorId));
        }
        assertThrows(AccessDeniedException.class, () -> service.updateUserByAdmin(staff.getId(), name("Forbidden"), null));
        assertEquals("ACTIVE", saved(customer).getStatus());
        assertEquals(staff.getFullName(), saved(staff).getFullName());
    }

    @Test void csrfIsRequiredForBothMutations() throws Exception {
        mvc.perform(post(editPath(staff)).with(user(new CustomUserDetails(admin))).param("fullName", "Forbidden"))
                .andExpect(redirectedUrl("/error/403"));
        mvc.perform(post(statusPath(customer)).with(user(new CustomUserDetails(admin))).param("targetStatus", "LOCKED"))
                .andExpect(redirectedUrl("/error/403"));
        assertEquals(staff.getFullName(), saved(staff).getFullName());
        assertEquals("ACTIVE", saved(customer).getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "STAFF"})
    void accountOwnersStillUpdateTheirOwnProfiles(String roleCode) throws Exception {
        User owner = target(roleCode);
        String path = "CUSTOMER".equals(roleCode) ? "/customer/profile" : "/staff/profile";
        String newPhone = "09" + NEXT_PHONE.getAndIncrement();
        mvc.perform(multipart(path).with(user(new CustomUserDetails(owner))).with(csrf())
                        .param("fullName", "Chủ tài khoản cập nhật").param("phone", newPhone))
                .andExpect(redirectedUrl(path)).andExpect(flash().attributeExists("successMessage"));
        assertEquals("Chủ tài khoản cập nhật", saved(owner).getFullName());
        assertEquals(newPhone, saved(owner).getPhone());
        assertEquals(owner.getEmail(), saved(owner).getEmail());
        assertEquals(owner.getPasswordHash(), saved(owner).getPasswordHash());
        assertEquals("ACTIVE", saved(owner).getStatus());
    }

    @Test void selfLockIsRejectedAndMissingRecordsAreHandled() throws Exception {
        mvc.perform(post(statusPath(admin)).with(user(new CustomUserDetails(admin))).with(csrf()).param("targetStatus", "LOCKED"))
                .andExpect(redirectedUrl("/admin/users")).andExpect(flash().attributeExists("errorMessage"));
        assertEquals("ACTIVE", saved(admin).getStatus());
        mvc.perform(get("/admin/users/" + Long.MAX_VALUE).with(user(new CustomUserDetails(admin))))
                .andExpect(redirectedUrl("/admin/users")).andExpect(flash().attributeExists("errorMessage"));
        mvc.perform(post("/admin/users/" + Long.MAX_VALUE + "/edit").with(user(new CustomUserDetails(admin))).with(csrf()).param("fullName", "Missing"))
                .andExpect(redirectedUrl("/admin/users")).andExpect(flash().attributeExists("errorMessage"));
    }

    @Test void concurrentAdminLocksKeepOneActiveAdmin() throws Exception {
        User otherAdmin = account("ADMIN");
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> tryLock(otherAdmin, admin, start));
            var second = pool.submit(() -> tryLock(admin, otherAdmin, start));
            start.countDown();
            int succeeded = (first.get(20, TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, succeeded);
            assertEquals(1, users.countByRoleCodeAndStatus("ADMIN", "ACTIVE"));
        } finally { pool.shutdownNow(); }
    }

    private boolean tryLock(User target, User actor, CountDownLatch start) throws InterruptedException {
        start.await();
        try { service.setUserStatus(target.getId(), "LOCKED", actor.getId()); return true; }
        catch (IllegalArgumentException | AccessDeniedException rejected) { return false; }
    }

    @Test void concurrentNameEditDoesNotOverwriteStaffsOwnContactUpdate() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        String newPhone = "09" + NEXT_PHONE.getAndIncrement();
        try {
            var adminEdit = pool.submit(() -> {
                start.await();
                service.updateUserByAdmin(staff.getId(), name("Admin corrected name"), admin.getId());
                return true;
            });
            var ownerEdit = pool.submit(() -> {
                start.await();
                service.updateProfile(staff.getId(), ProfileUpdateRequestDto.builder().fullName("Staff edited own profile").phone(newPhone).build());
                return true;
            });
            start.countDown();
            assertTrue(adminEdit.get(20, TimeUnit.SECONDS));
            assertTrue(ownerEdit.get(20, TimeUnit.SECONDS));
            assertEquals(newPhone, saved(staff).getPhone());
            assertEquals(staff.getEmail(), saved(staff).getEmail());
            assertEquals(HASH, saved(staff).getPasswordHash());
            assertTrue(List.of("Admin corrected name", "Staff edited own profile").contains(saved(staff).getFullName()));
        } finally { pool.shutdownNow(); }
    }
}
