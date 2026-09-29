package com.petshop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Guest should be able to access public storefront endpoints")
    void testPublicEndpointsAccessibleByGuest() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/register"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/about"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/contact"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Unauthenticated requests to protected endpoints should redirect to /login")
    void testUnauthenticatedRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/cart"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(get("/staff/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("CUSTOMER role cannot access staff or admin portals")
    void testCustomerDeniedFromStaffAndAdmin() throws Exception {
        mockMvc.perform(get("/staff/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/error/403"));

        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/error/403"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("STAFF role can access staff portal but denied from admin portal")
    void testStaffAccessMatrix() throws Exception {
        mockMvc.perform(get("/staff/dashboard"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/error/403"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN role can access both admin portal and staff portal")
    void testAdminAccessMatrix() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/staff/dashboard"))
                .andExpect(status().isOk());
    }
}
