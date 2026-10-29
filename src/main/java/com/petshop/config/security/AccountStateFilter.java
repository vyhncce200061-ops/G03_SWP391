package com.petshop.config.security;

import com.petshop.entity.User;
import com.petshop.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/** Recheck persisted account state before using authorities stored in a session. */
public class AccountStateFilter extends OncePerRequestFilter {
    private final UserRepository users;

    public AccountStateFilter(UserRepository users) { this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("/reset-password".equals(path)) {
            // Recovery authorizes the token's owner, independently of any existing login.
            // The controller checks the token and removes an existing authenticated session.
            chain.doFilter(request, response);
            return;
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails principal) {
            User current = users.findById(principal.getId()).orElse(null);
            if (current == null || !"ACTIVE".equals(current.getStatus())
                    || !Objects.equals(current.getRole().getCode(), principal.getRoleCode())
                    || !Objects.equals(current.getPasswordHash(), principal.getPassword())) {
                new SecurityContextLogoutHandler().logout(request, response, auth);
                response.sendRedirect(request.getContextPath() + "/login?expired=true");
                return;
            }
            if (Boolean.TRUE.equals(current.getMustChangePassword()) && !path.equals("/account/password")
                    && !path.equals("/logout") && !path.startsWith("/css/") && !path.startsWith("/images/")
                    && !path.startsWith("/js/")) {
                response.sendRedirect(request.getContextPath() + "/account/password");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
