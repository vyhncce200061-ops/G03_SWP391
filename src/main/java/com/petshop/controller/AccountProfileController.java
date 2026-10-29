package com.petshop.controller;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.auth.ProfileUpdateRequestDto;
import com.petshop.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    @ModelAttribute("passwordForm")
    public com.petshop.dto.auth.PasswordChangeRequestDto passwordForm() { return new com.petshop.dto.auth.PasswordChangeRequestDto(); }
    @GetMapping
    public String profile(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        if ("CUSTOMER".equals(principal.getRoleCode())) return "redirect:/customer/profile";
        model.addAttribute("user", users.findById(principal.getId()));
        model.addAttribute("profilePath", "/account/profile");
        return "customer/profile";
    }
    @PostMapping
    public String update(@AuthenticationPrincipal CustomUserDetails principal,
            @Valid @ModelAttribute ProfileUpdateRequestDto form, BindingResult errors, Model model,
            @RequestParam(value = "avatarFile", required = false) org.springframework.web.multipart.MultipartFile avatarFile,
            RedirectAttributes redirect) {
        if (errors.hasErrors()) return profile(principal, model);
        try {
            users.updateProfile(principal.getId(), form);
            if (avatarFile != null && !avatarFile.isEmpty()) users.updateAvatar(principal.getId(), avatarFile);
            redirect.addFlashAttribute("successMessage", "Đã cập nhật hồ sơ.");
        }
        catch (IllegalArgumentException e) { redirect.addFlashAttribute("errorMessage", e.getMessage()); }
        return "redirect:/account/profile";
    }

    @PostMapping("/avatar")
    public String avatar(@AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam("avatarFile") org.springframework.web.multipart.MultipartFile file,
            RedirectAttributes redirect) {
        try {
            users.updateAvatar(principal.getId(), file);
            redirect.addFlashAttribute("successMessage", "Đã cập nhật ảnh đại diện.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/account/profile";
    }
}
