package com.petshop.controller;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.auth.PasswordChangeRequestDto;
import com.petshop.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class AccountPasswordController {
    private final UserService users;

    @GetMapping("/account/password")
    public String passwordForm(Model model) {
        model.addAttribute("passwordForm", new PasswordChangeRequestDto());
        return "account/password";
    }

    @PostMapping("/account/password")
    public String changePassword(@AuthenticationPrincipal CustomUserDetails principal,
            @Valid @ModelAttribute("passwordForm") PasswordChangeRequestDto form, BindingResult errors,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        if (errors.hasErrors()) return "account/password";
        try {
            users.changePassword(principal.getId(), form);
            new SecurityContextLogoutHandler().logout(request, response,
                    SecurityContextHolder.getContext().getAuthentication());
            return "redirect:/login?passwordChanged=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "account/password";
        }
    }
}
