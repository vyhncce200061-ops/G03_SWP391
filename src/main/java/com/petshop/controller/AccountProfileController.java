package com.petshop.controller;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.controller.customer.CustomerProfileController;
import com.petshop.controller.staff.StaffProfileController;
import com.petshop.dto.auth.ProfileUpdateRequestDto;
import com.petshop.entity.User;
import com.petshop.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/account/profile")
@RequiredArgsConstructor
public class AccountProfileController {
    private final UserService users;
    private final CustomerProfileController customerProfileController;
    private final StaffProfileController staffProfileController;

    @ModelAttribute("passwordForm")
    public com.petshop.dto.auth.PasswordChangeRequestDto passwordForm() {
        return new com.petshop.dto.auth.PasswordChangeRequestDto();
    }

    @GetMapping
    public String profile(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        model.addAttribute("profilePath", "/account/profile");
        if ("STAFF".equals(principal.getRoleCode()) || "ADMIN".equals(principal.getRoleCode())) {
            return staffProfileController.viewProfile(principal, model);
        }
        return customerProfileController.viewProfile(principal, "info", model);
    }

    @PostMapping
    public String update(@AuthenticationPrincipal CustomUserDetails principal,
            @Valid @ModelAttribute ProfileUpdateRequestDto form, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        if (errors.hasErrors()) return profile(principal, model);
        try {
            users.updateProfile(principal.getId(), form);
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof CustomUserDetails) {
                User updated = users.findById(principal.getId());
                CustomUserDetails newDetails = new CustomUserDetails(updated);
                var newAuth = new UsernamePasswordAuthenticationToken(
                        newDetails, newDetails.getPassword(), newDetails.getAuthorities());
                newAuth.setDetails(auth.getDetails());
                SecurityContextHolder.getContext().setAuthentication(newAuth);
            }
            redirect.addFlashAttribute("successMessage", "Đã cập nhật hồ sơ.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/account/profile";
    }
}
