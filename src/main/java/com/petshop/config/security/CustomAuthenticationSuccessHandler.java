package com.petshop.config.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;

/**
 * Handles role-based post-login redirection.
 * Redirects:
 * - ROLE_ADMIN -> /admin/dashboard
 * - ROLE_STAFF -> /staff/dashboard
 * - ROLE_CUSTOMER -> SavedRequest if present, else "/"
 */
@Component
@Slf4j
public class CustomAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        for (GrantedAuthority authority : authorities) {
            String role = authority.getAuthority();
            if ("ROLE_ADMIN".equals(role)) {
                log.info("Admin user logged in. Redirecting to admin dashboard.");
                getRedirectStrategy().sendRedirect(request, response, "/admin/dashboard");
                return;
            } else if ("ROLE_STAFF".equals(role)) {
                log.info("Staff user logged in. Redirecting to staff dashboard.");
                getRedirectStrategy().sendRedirect(request, response, "/staff/dashboard");
                return;
            }
        }

        // For CUSTOMER, respect saved request if available, otherwise redirect to "/"
        log.info("Customer logged in. Redirecting to default / saved URL.");
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
