package com.petshop.controller.guest;

import com.petshop.dto.auth.RegisterRequestDto;
import com.petshop.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @GetMapping("/login")
    public String loginForm() {
        return "guest/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        if (!model.containsAttribute("registerForm")) {
            model.addAttribute("registerForm", new RegisterRequestDto());
        }
        return "guest/register";
    }

    @PostMapping("/register")
    public String processRegister(@Valid @ModelAttribute("registerForm") RegisterRequestDto registerForm,
                                  BindingResult bindingResult,
                                  RedirectAttributes redirectAttributes,
                                  Model model) {
        if (bindingResult.hasErrors()) {
            return "guest/register";
        }

        try {
            authService.registerCustomer(registerForm);
            redirectAttributes.addFlashAttribute("successMessage", "Đăng ký tài khoản thành công! Vui lòng đăng nhập.");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "guest/register";
        }
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "guest/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String sendResetToken(@RequestParam String email, RedirectAttributes redirectAttributes) {
        authService.initiatePasswordReset(email);
        redirectAttributes.addFlashAttribute("infoMessage", "Nếu email tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi đi.");
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam String token, Model model) {
        if (!authService.verifyPasswordResetToken(token)) {
            model.addAttribute("errorMessage", "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
            return "guest/login";
        }
        model.addAttribute("token", token);
        return "guest/reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam String token,
                                       @RequestParam String newPassword,
                                       @RequestParam String confirmPassword,
                                       RedirectAttributes redirectAttributes,
                                       Model model) {
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("token", token);
            model.addAttribute("errorMessage", "Mật khẩu xác nhận không khớp.");
            return "guest/reset-password";
        }

        try {
            authService.resetPasswordWithToken(token, newPassword);
            redirectAttributes.addFlashAttribute("successMessage", "Đặt lại mật khẩu thành công! Vui lòng đăng nhập với mật khẩu mới.");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("token", token);
            model.addAttribute("errorMessage", e.getMessage());
            return "guest/reset-password";
        }
    }
}
