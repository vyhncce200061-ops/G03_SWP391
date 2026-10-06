package com.petshop.controller.guest;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.config.security.JwtAuthenticationFilter;
import com.petshop.config.security.JwtService;
import com.petshop.dto.auth.LoginRequestDto;
import com.petshop.dto.auth.RegisterRequestDto;
import com.petshop.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
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
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @GetMapping("/login")
    public String loginForm(Model model) {
        if (!model.containsAttribute("loginForm")) {
            model.addAttribute("loginForm", new LoginRequestDto());
        }
        return "guest/login";
    }

    @PostMapping("/login")
    public String processLogin(@Valid @ModelAttribute("loginForm") LoginRequestDto loginForm,
                               BindingResult bindingResult,
                               HttpServletResponse response,
                               Model model) {
        if (bindingResult.hasErrors()) {
            return "guest/login";
        }
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginForm.getUsername().trim(), loginForm.getPassword()));
            CustomUserDetails user = (CustomUserDetails) auth.getPrincipal();

            Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwtService.generateToken(user));
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge((int) (jwtService.getExpirationMs() / 1000));
            response.addCookie(cookie);

            log.info("User {} logged in with role {}", user.getUsername(), user.getRoleCode());
            switch (user.getRoleCode()) {
                case "ADMIN": return "redirect:/admin/dashboard";
                case "STAFF": return "redirect:/staff/dashboard";
                default: return "redirect:/";
            }
        } catch (LockedException | DisabledException e) {
            model.addAttribute("errorMessage", "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.");
        } catch (AuthenticationException e) {
            model.addAttribute("errorMessage", "Email, số điện thoại hoặc mật khẩu không chính xác!");
        }
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
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã gửi email xác nhận tới " + registerForm.getEmail().trim()
                    + ". Vui lòng kiểm tra hộp thư (kể cả Spam) và bấm vào liên kết để kích hoạt tài khoản.");
            return "redirect:/login";
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "guest/register";
        }
    }

    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam String token, RedirectAttributes redirectAttributes) {
        try {
            authService.confirmRegistration(token);
            redirectAttributes.addFlashAttribute("successMessage", "Kích hoạt tài khoản thành công! Vui lòng đăng nhập.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/login";
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
