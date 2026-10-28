package com.petshop.controller.guest;

import com.petshop.dto.auth.RegisterRequestDto;
import com.petshop.dto.auth.LoginRequestDto;
import com.petshop.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    public String loginForm(Model model) {
        if (!model.containsAttribute("loginForm")) model.addAttribute("loginForm", new LoginRequestDto());
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
            authService.startRegistration(registerForm);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi email xác nhận đăng ký. Vui lòng kiểm tra hộp thư (kể cả Spam) và mở liên kết để kích hoạt tài khoản.");
            return "redirect:/login";
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "guest/register";
        }
    }

    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam(required = false) String token, RedirectAttributes redirectAttributes) {
        try {
            authService.confirmRegistration(token);
            redirectAttributes.addFlashAttribute("successMessage", "Kích hoạt tài khoản thành công! Vui lòng đăng nhập.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/login";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "guest/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String sendResetToken(@RequestParam(required = false) String email, RedirectAttributes redirectAttributes) {
        try {
            authService.initiatePasswordReset(email);
            redirectAttributes.addFlashAttribute("infoMessage", "Nếu email tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi đi.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (IllegalStateException | org.springframework.mail.MailException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Gửi email chưa khả dụng. Vui lòng liên hệ quản trị viên để đặt lại mật khẩu.");
        }
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam(required = false) String token, Model model,
                                    HttpServletRequest request, HttpServletResponse response) {
        secureRecoveryResponse(response);
        if (!authService.verifyPasswordResetToken(token)) {
            return invalidResetLink(model);
        }
        if (hasAuthenticatedUser()) return startAnonymousReset(token, request, response);
        model.addAttribute("token", token);
        model.addAttribute("resetLinkValid", true);
        return "guest/reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam(required = false) String token,
                                       @RequestParam(required = false) String newPassword,
                                       @RequestParam(required = false) String confirmPassword,
                                       RedirectAttributes redirectAttributes,
                                       Model model, HttpServletRequest request, HttpServletResponse response) {
        secureRecoveryResponse(response);
        if (!authService.verifyPasswordResetToken(token)) return invalidResetLink(model);
        if (hasAuthenticatedUser()) return startAnonymousReset(token, request, response);
        if (!java.util.Objects.equals(newPassword, confirmPassword)) {
            model.addAttribute("token", token);
            model.addAttribute("resetLinkValid", true);
            model.addAttribute("errorMessage", "Mật khẩu xác nhận không khớp.");
            return "guest/reset-password";
        }

        try {
            authService.resetPasswordWithToken(token, newPassword);
            clearCurrentSession(request, response);
            redirectAttributes.addFlashAttribute("successMessage", "Đặt lại mật khẩu thành công! Vui lòng đăng nhập với mật khẩu mới.");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            if (!authService.verifyPasswordResetToken(token)) return invalidResetLink(model);
            model.addAttribute("token", token);
            model.addAttribute("resetLinkValid", true);
            model.addAttribute("errorMessage", e.getMessage());
            return "guest/reset-password";
        }
    }

    private static void secureRecoveryResponse(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }

    private static boolean hasAuthenticatedUser() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
    }

    private static void clearCurrentSession(HttpServletRequest request, HttpServletResponse response) {
        new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler().logout(request, response,
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
    }

    private static String startAnonymousReset(String token, HttpServletRequest request, HttpServletResponse response) {
        clearCurrentSession(request, response);
        // Redirect so the next request gets a fresh session and CSRF token before rendering.
        return "redirect:" + org.springframework.web.util.UriComponentsBuilder.fromPath("/reset-password")
                .queryParam("token", token).build().encode().toUriString();
    }

    private static String invalidResetLink(Model model) {
        model.addAttribute("resetLinkValid", false);
        model.addAttribute("errorMessage", "Liên kết không hợp lệ, đã hết hạn hoặc đã được sử dụng. Vui lòng yêu cầu gửi lại liên kết.");
        return "guest/reset-password";
    }
}
