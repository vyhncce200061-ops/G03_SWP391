package com.petshop;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortalLayoutTests {
    @Autowired MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/admin/categories", "/admin/categories/create", "/admin/brands", "/admin/products"})
    void olderAdminPagesRenderExactlyOnePortalAndKeepCsrf(String path) throws Exception {
        String html = mvc.perform(get(path).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        portal(html);
        assertThat(html).contains("href=\"/admin/users\"", "href=\"/admin/reports/revenue\"", "name=\"_csrf\"");
        assertThat(html).doesNotContain("storefront-header");
    }

    @Test
    void staffNavigationKeepsItsOwnLinksAndDoesNotPublishAdminMenu() throws Exception {
        for (String path : List.of("/staff/products", "/staff/products/create", "/staff/stock-imports", "/staff/products/low-stock")) {
            String html = mvc.perform(get(path).with(user("staff").roles("STAFF")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            portal(html);
            assertThat(html).contains("href=\"/staff/products\"", "href=\"/staff/orders\"");
            assertThat(html).doesNotContain("href=\"/admin/users\"", "href=\"/admin/vouchers\"", "href=\"/admin/reviews\"");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "STAFF", "CUSTOMER"})
    void sharedPasswordAndErrorPagesChooseTheAuthenticatedLayout(String role) throws Exception {
        for (String path : List.of("/account/password", "/error/403", "/error/404", "/error/500")) {
            String html = mvc.perform(get(path).with(user("operator").roles(role)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            if (role.equals("CUSTOMER")) {
                assertThat(html).contains("storefront-header").doesNotContain("id=\"sidebar-wrapper\"");
            } else portal(html);
            assertThat(html.split("<main\\b", -1)).hasSize(2);
            if (path.equals("/account/password")) {
                assertThat(html).contains("action=\"/account/password\"", "name=\"currentPassword\"", "name=\"newPassword\"", "name=\"confirmNewPassword\"", "name=\"_csrf\"");
            }
        }
    }

    @Test
    void publicFormsKeepCsrfAndResetPageDoesNotExposeAnExistingAdminIdentity() throws Exception {
        for (String path : List.of("/login", "/register", "/forgot-password")) {
            String html = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(html).contains("name=\"_csrf\"", "/css/ui.css", "/js/vendor/bootstrap.bundle.min.js");
            assertThat(html).doesNotContain("cdn.jsdelivr.net");
        }
        String reset = mvc.perform(get("/reset-password").param("token", "invalid-layout-check").with(user("private-admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(reset).contains("password-shell", "no-referrer");
        assertThat(reset).doesNotContain("private-admin", "sidebar-wrapper", "userDropdown");
    }

    @Test
    void localStylesScriptsFontsAndHeroArePublicAssets() throws Exception {
        for (String path : List.of("/css/ui.css", "/css/vendor/bootstrap.min.css", "/css/vendor/bootstrap-icons.min.css",
                "/css/vendor/fonts/bootstrap-icons.woff2", "/js/ui.js", "/js/vendor/bootstrap.bundle.min.js",
                "/js/vendor/chart.umd.js", "/images/hero-pets.svg")) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    private void portal(String html) {
        assertThat(html.split("id=\"sidebar-wrapper\"", -1)).hasSize(2);
        assertThat(html.split("id=\"mainContent\"", -1)).hasSize(2);
        assertThat(html).contains("id=\"sidebarNav\"", "id=\"sidebarScrollUp\"", "id=\"sidebarScrollDown\"", "id=\"sidebarToggle\"", "/css/ui.css");
    }
}
