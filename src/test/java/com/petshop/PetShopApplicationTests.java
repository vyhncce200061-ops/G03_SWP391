package com.petshop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import com.petshop.entity.Role;
import com.petshop.entity.User;
import com.petshop.repository.RoleRepository;
import com.petshop.repository.UserRepository;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:liveauth;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@ActiveProfiles("test")
class PetShopApplicationTests {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Autowired com.petshop.service.UserService userService;
    @Autowired com.petshop.repository.PasswordResetTokenRepository tokens;
    @org.springframework.boot.test.mock.mockito.MockBean com.petshop.service.EmailService mail;

    @Test
    void freshAnonymousAuthPagesRenderCompleteHtmlAndCreateSessionBeforeCommit() throws Exception {
        for (String path : List.of("/login", "/register", "/forgot-password", "/login?error=true", "/login?logout=true")) {
            var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            var client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
            var page = get(client, path);
            assertEquals(200, page.statusCode(), path);
            assertTrue(page.body().stripTrailing().endsWith("</html>"), "Truncated response: " + path);
            assertFalse(csrf(page.body()).isBlank());
            assertTrue(cookies.getCookieStore().getCookies().stream().anyMatch(c -> "JSESSIONID".equals(c.getName())));
            if (path.equals("/login")) {
                assertTrue(page.body().contains("name=\"username\""));
                var failed = post(client, "/login", "username=missing%40test.local&password=Wrong%40123&_csrf=" + encode(csrf(page.body())));
                assertEquals(302, failed.statusCode());
                assertTrue(failed.headers().firstValue("location").orElseThrow().endsWith("/login?error=true"));
            }
        }
    }

    @Test
    void realHttpLoginAndLogoutWorkForCustomerStaffAndAdmin() throws Exception {
        int index = 0;
        for (String code : List.of("CUSTOMER", "STAFF", "ADMIN")) {
            var role = roles.findByCode(code).orElseGet(() -> roles.save(Role.builder().code(code).name(code).build()));
            var account = users.save(User.builder().role(role).fullName("HTTP " + code).email(code.toLowerCase() + "@liveauth.test")
                    .phone("090100010" + index++).passwordHash(encoder.encode("Login@123")).status("ACTIVE").build());
            var client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                    .connectTimeout(Duration.ofSeconds(5)).build();
            var page = get(client, "/login");
            var loggedIn = post(client, "/login", "username=" + encode(account.getEmail())
                    + "&password=Login%40123&_csrf=" + encode(csrf(page.body())));
            assertEquals(302, loggedIn.statusCode());
            String destination = code.equals("ADMIN") ? "/admin/dashboard" : code.equals("STAFF") ? "/staff/dashboard" : "/";
            assertTrue(loggedIn.headers().firstValue("location").orElseThrow().endsWith(destination));
            var profile = get(client, "/account/profile");
            if (code.equals("CUSTOMER")) {
                assertEquals(302, profile.statusCode());
                assertTrue(profile.headers().firstValue("location").orElseThrow().endsWith("/customer/profile"));
                profile = get(client, "/customer/profile");
            }
            assertEquals(200, profile.statusCode());
            assertTrue(profile.body().stripTrailing().endsWith("</html>"));
            var loggedOut = post(client, "/logout", "_csrf=" + encode(csrf(profile.body())));
            assertEquals(302, loggedOut.statusCode());
            assertTrue(loggedOut.headers().firstValue("location").orElseThrow().endsWith("/login?logout=true"));
            assertEquals(302, get(client, "/account/profile").statusCode());
        }
    }

    @Test
    void staffInvitationSeparatesAdminSessionAndFinishesAtAnonymousLogin() throws Exception {
        var admin = admin("invite.admin@liveauth.test", "0901000201");
        String adminHash = admin.getPasswordHash();
        var staff = userService.createStaffAccount(com.petshop.dto.admin.StaffCreateRequestDto.builder()
                .fullName("Invited staff").email("invite.staff@liveauth.test").phone("0901000202").build());
        var links = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mail).sendStaffInvitation(org.mockito.ArgumentMatchers.eq(staff.getEmail()),
                org.mockito.ArgumentMatchers.anyString(), links.capture());
        String link = localLink(links.getValue());
        var client = client();
        login(client, admin, "Login@123");
        var transition = get(client, link);
        assertEquals(302, transition.statusCode());
        String anonymousLink = localLink(transition.headers().firstValue("location").orElseThrow());
        assertTrue(anonymousLink.startsWith("/reset-password?token="));
        assertEquals(302, get(client, "/admin/dashboard").statusCode());
        var form = get(client, anonymousLink);
        assertEquals(200, form.statusCode());
        assertTrue(form.body().contains("name=\"newPassword\""));
        assertFalse(form.body().contains(admin.getEmail()));
        assertFalse(form.body().contains("/admin/dashboard"));
        assertEquals("no-referrer", form.headers().firstValue("Referrer-Policy").orElseThrow());
        assertTrue(form.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
        String token = URI.create(anonymousLink).getQuery().substring(6);
        var mismatch = post(client, "/reset-password", "token=" + encode(token) + "&newPassword=StaffOwn%40123"
                + "&confirmPassword=Different%40123&_csrf=" + encode(csrf(form.body())));
        assertEquals(200, mismatch.statusCode());
        assertTrue(mismatch.body().contains("Mật khẩu xác nhận không khớp"));
        assertTrue(mismatch.body().contains("name=\"newPassword\""));
        var saved = post(client, "/reset-password", "token=" + encode(token) + "&newPassword=StaffOwn%40123"
                + "&confirmPassword=StaffOwn%40123&_csrf=" + encode(csrf(mismatch.body())));
        assertEquals(302, saved.statusCode());
        assertTrue(saved.headers().firstValue("location").orElseThrow().endsWith("/login"));
        var login = get(client, "/login");
        assertEquals(200, login.statusCode());
        assertTrue(login.body().contains("name=\"username\""));
        assertFalse(login.body().contains(admin.getEmail()));
        assertEquals(adminHash, users.findById(admin.getId()).orElseThrow().getPasswordHash());
        var activated = users.findById(staff.getId()).orElseThrow();
        assertFalse(activated.getMustChangePassword());
        assertTrue(encoder.matches("StaffOwn@123", activated.getPasswordHash()));
        assertEquals(302, get(client, "/admin/dashboard").statusCode());
        login(client, activated, "StaffOwn@123");
        var used = get(client, anonymousLink);
        assertEquals(200, used.statusCode());
        assertFalse(used.body().contains("name=\"newPassword\""));
        assertTrue(used.body().contains("đã hết hạn hoặc đã được sử dụng"));
        assertEquals(200, get(client, "/account/profile").statusCode());
    }

    @Test
    void resetLinksHandleStaleLoginReplacedExpiredAndLockedAccounts() throws Exception {
        var admin = admin("stale.admin@liveauth.test", "0901000203");
        var staff = userService.createStaffAccount(com.petshop.dto.admin.StaffCreateRequestDto.builder()
                .fullName("Recovery staff").email("recovery.staff@liveauth.test").phone("0901000204").build());
        userService.resendStaffInvitation(staff.getId());
        var links = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mail, org.mockito.Mockito.times(2)).sendStaffInvitation(
                org.mockito.ArgumentMatchers.eq(staff.getEmail()), org.mockito.ArgumentMatchers.anyString(), links.capture());
        var client = client();
        login(client, admin, "Login@123");
        admin.setPasswordHash(encoder.encode("Changed@123"));users.save(admin);
        var replaced = get(client, localLink(links.getAllValues().get(0)));
        assertEquals(200, replaced.statusCode());
        assertFalse(replaced.body().contains("name=\"newPassword\""));
        var latest = get(client, localLink(links.getAllValues().get(1)));
        assertEquals(302, latest.statusCode());
        String path = localLink(latest.headers().firstValue("location").orElseThrow());
        assertTrue(path.startsWith("/reset-password?token="));
        assertEquals(200, get(client, path).statusCode());
        String raw = URI.create(path).getQuery().substring(6);
        var token = tokens.findByTokenHash(com.petshop.service.AccountAccessService.hash(raw)).orElseThrow();
        token.setExpiresAt(java.time.LocalDateTime.now().minusNanos(1));tokens.save(token);
        var expired = get(client, path);
        assertEquals(200, expired.statusCode());
        assertFalse(expired.body().contains("name=\"newPassword\""));
        userService.resendStaffInvitation(staff.getId());
        var latestLinks = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mail, org.mockito.Mockito.times(3)).sendStaffInvitation(
                org.mockito.ArgumentMatchers.eq(staff.getEmail()), org.mockito.ArgumentMatchers.anyString(), latestLinks.capture());
        staff = users.findById(staff.getId()).orElseThrow();staff.setStatus("LOCKED");users.save(staff);
        var locked = get(client, localLink(latestLinks.getAllValues().get(2)));
        assertEquals(200, locked.statusCode());
        assertFalse(locked.body().contains("name=\"newPassword\""));
        assertTrue(users.findById(staff.getId()).orElseThrow().getMustChangePassword());
    }

    @Test
    void directAuthenticatedResetPostCannotContinueWithAdminPrivileges() throws Exception {
        var admin = admin("post.admin@liveauth.test", "0901000205");
        var staff = userService.createStaffAccount(com.petshop.dto.admin.StaffCreateRequestDto.builder()
                .fullName("Post staff").email("post.staff@liveauth.test").phone("0901000206").build());
        String originalHash = staff.getPasswordHash();
        var links = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mail).sendStaffInvitation(org.mockito.ArgumentMatchers.eq(staff.getEmail()),
                org.mockito.ArgumentMatchers.anyString(), links.capture());
        String token = URI.create(links.getValue()).getQuery().substring(6);
        var client = client();login(client, admin, "Login@123");
        var profile = get(client, "/account/profile");
        var reset = post(client, "/reset-password", "token=" + encode(token) + "&newPassword=StaffOwn%40123"
                + "&confirmPassword=StaffOwn%40123&_csrf=" + encode(csrf(profile.body())));
        assertEquals(302, reset.statusCode());
        assertTrue(localLink(reset.headers().firstValue("location").orElseThrow()).startsWith("/reset-password?token="));
        assertEquals(originalHash, users.findById(staff.getId()).orElseThrow().getPasswordHash());
        assertEquals(302, get(client, "/admin/dashboard").statusCode());
    }

    private User admin(String email, String phone) {
        var role = roles.findByCode("ADMIN").orElseGet(() -> roles.save(Role.builder().code("ADMIN").name("Admin").build()));
        return users.save(User.builder().role(role).fullName("Recovery admin").email(email).phone(phone)
                .passwordHash(encoder.encode("Login@123")).status("ACTIVE").build());
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private void login(HttpClient client, User account, String password) throws Exception {
        var page = get(client, "/login");
        var result = post(client, "/login", "username=" + encode(account.getEmail()) + "&password=" + encode(password)
                + "&_csrf=" + encode(csrf(page.body())));
        assertEquals(302, result.statusCode());
        assertTrue(result.headers().firstValue("location").orElseThrow().endsWith("/" + account.getRole().getCode().toLowerCase() + "/dashboard"));
    }

    private static String localLink(String url) {
        var uri = URI.create(url);
        return uri.getRawPath() + "?" + uri.getRawQuery();
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(HttpClient client, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String csrf(String html) {
        var match = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertTrue(match.find(), "Missing CSRF field");
        return org.springframework.web.util.HtmlUtils.htmlUnescape(match.group(1));
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
