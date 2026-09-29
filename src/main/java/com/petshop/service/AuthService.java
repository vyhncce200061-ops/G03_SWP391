package com.petshop.service;

import com.petshop.dto.auth.RegisterRequestDto;
import com.petshop.entity.User;

public interface AuthService {
    User registerCustomer(RegisterRequestDto request);
    void initiatePasswordReset(String email);
    boolean verifyPasswordResetToken(String token);
    void resetPasswordWithToken(String token, String newPassword);
}
