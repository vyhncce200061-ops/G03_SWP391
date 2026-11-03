package com.petshop.service;

import com.petshop.dto.auth.RegisterRequestDto;
import com.petshop.entity.User;

public interface AuthService {
    User registerCustomer(RegisterRequestDto request);

    /** Validates the form and emails a confirmation link. The account is created only after confirmation. */
    void startRegistration(RegisterRequestDto request);

    /** Creates the account from a confirmation token. */
    User confirmRegistration(String token);

    void initiatePasswordReset(String email);
    boolean verifyPasswordResetToken(String token);
    void resetPasswordWithToken(String token, String newPassword);
}
