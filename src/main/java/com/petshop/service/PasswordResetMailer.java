package com.petshop.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class PasswordResetMailer {
    private final EmailService emailService;
    @Value("${petshop.public-base-url:http://localhost:8080}") private String baseUrl;

    public void requireConfigured() {
        emailService.requireConfigured();
    }

    public void send(String email, String token) {
        requireConfigured();
        emailService.sendPasswordResetEmail(email, "bạn", link(token));
    }

    public void sendStaffInvitation(String email, String fullName, String token) {
        emailService.sendStaffInvitation(email, fullName, link(token));
    }

    private String link(String token) {
        return UriComponentsBuilder.fromHttpUrl(baseUrl).path("/reset-password").queryParam("token", token).build().encode().toUriString();
    }
}
