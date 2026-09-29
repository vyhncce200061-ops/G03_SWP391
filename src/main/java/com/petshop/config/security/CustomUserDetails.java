package com.petshop.config.security;

import com.petshop.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Custom Spring Security UserDetails wrapping the domain User entity.
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String phone;
    private final String password;
    private final String fullName;
    private final String avatarUrl;
    private final String roleCode;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.phone = user.getPhone();
        this.password = user.getPasswordHash();
        this.fullName = user.getFullName();
        this.avatarUrl = user.getAvatarUrl();
        this.roleCode = user.getRole() != null ? user.getRole().getCode() : "CUSTOMER";
        this.active = "ACTIVE".equalsIgnoreCase(user.getStatus());
        // Spring Security convention: prefix with "ROLE_"
        this.authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + this.roleCode)
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        // Return email if present, otherwise phone
        return (email != null && !email.isBlank()) ? email : phone;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
