package com.petshop.config;

import com.petshop.config.security.CustomAccessDeniedHandler;
import com.petshop.config.security.CustomAuthenticationSuccessHandler;
import com.petshop.config.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 6 Configuration for PetShop E-Commerce SWP391.
 * Enforces Role-Based Access Control (RBAC) across Guest, Customer, Staff, and Admin routes.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final CustomAuthenticationSuccessHandler authenticationSuccessHandler;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    /**
     * Password encoder bean utilizing BCrypt hashing (compatible with $2a$10$ seed hashes).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * DAO Authentication Provider wiring JPA UserDetailsService and BCrypt PasswordEncoder.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * AuthenticationManager bean for programmatic authentication if needed.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * Main SecurityFilterChain defining authorization matchers, form login, logout, and CSRF rules.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Authentication Provider Registration
            .authenticationProvider(authenticationProvider())

            // 2. CSRF Configuration
            .csrf(csrf -> csrf
                // Exempt server-to-server gateway webhooks from CSRF checks
                .ignoringRequestMatchers("/payment/vnpay-ipn")
            )

            // 3. Authorization Request Matchers
            .authorizeHttpRequests(auth -> auth
                // Static resources & assets (Public)
                .requestMatchers(
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/webjars/**",
                    "/static/**",
                    "/favicon.ico"
                ).permitAll()

                // Error dispatch routes (Public)
                .requestMatchers(
                    "/error",
                    "/error/**"
                ).permitAll()

                // Public browsing & catalog endpoints (Guest & All Users)
                .requestMatchers(
                    "/",
                    "/home",
                    "/about",
                    "/contact",
                    "/products/**",
                    "/categories/**",
                    "/brands/**"
                ).permitAll()

                // Public authentication endpoints (Guest)
                .requestMatchers(
                    "/login",
                    "/register",
                    "/forgot-password",
                    "/reset-password"
                ).permitAll()

                // Payment callbacks (Public)
                .requestMatchers(
                    "/payment/**",
                    "/checkout/vnpay-callback"
                ).permitAll()

                // Customer-exclusive routes
                .requestMatchers(
                    "/customer/**",
                    "/cart/**",
                    "/checkout/**",
                    "/wishlist/**",
                    "/orders/**"
                ).hasRole("CUSTOMER")

                // Staff operations (Accessible by STAFF or ADMIN)
                .requestMatchers(
                    "/staff/**"
                ).hasAnyRole("STAFF", "ADMIN")

                // Admin-exclusive routes
                .requestMatchers(
                    "/admin/**"
                ).hasRole("ADMIN")

                // Any other unmapped route requires authentication
                .anyRequest().authenticated()
            )

            // 4. Form-Based Login Configuration
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username") // Supports Email or Phone
                .passwordParameter("password")
                .successHandler(authenticationSuccessHandler)
                .failureUrl("/login?error=true")
                .permitAll()
            )

            // 5. Logout Handler Configuration
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )

            // 6. Exception & Access Denied Handling
            .exceptionHandling(exceptions -> exceptions
                .accessDeniedHandler(accessDeniedHandler)
            );

        return http.build();
    }
}
