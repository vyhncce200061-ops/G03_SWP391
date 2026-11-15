package com.petshop.config.security;

import com.petshop.entity.User;
import com.petshop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Loads user details from database by Email or Phone.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String clean = username == null ? "" : username.trim();
        log.debug("Attempting authentication for identifier: {}", clean);

        User user = userRepository.findByEmailOrPhone(clean.toLowerCase(Locale.ROOT), clean)
                .orElseThrow(() -> {
                    log.warn("Authentication failed: No user found for '{}'", clean);
                    return new UsernameNotFoundException("Tài khoản hoặc số điện thoại không tồn tại: " + clean);
                });

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            log.warn("Authentication rejected: Account for '{}' is locked", clean);
            throw new LockedException("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }

        return new CustomUserDetails(user);
    }
}
