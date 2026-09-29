package com.petshop.controller.customer;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.auth.AddressCreateDto;
import com.petshop.dto.auth.PasswordChangeRequestDto;
import com.petshop.dto.auth.ProfileUpdateRequestDto;
import com.petshop.entity.User;
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
@RequestMapping("/customer")
@RequiredArgsConstructor
public class CustomerProfileController {

    private final UserService userService;

    @GetMapping("/profile")
    public String viewProfile(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User user = userService.findById(userDetails.getId());
        model.addAttribute("user", user);
        return "customer/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@AuthenticationPrincipal CustomUserDetails userDetails,
                                @Valid @ModelAttribute ProfileUpdateRequestDto request,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        if (bindingResult.hasErrors()) {
            User user = userService.findById(userDetails.getId());
            model.addAttribute("user", user);
            return "customer/profile";
        }

        userService.updateProfile(userDetails.getId(), request);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin cá nhân thành công!");
        return "redirect:/customer/profile";
    }

    @PostMapping("/change-password")
    public String changePassword(@AuthenticationPrincipal CustomUserDetails userDetails,
                                 @Valid @ModelAttribute PasswordChangeRequestDto request,
                                 RedirectAttributes redirectAttributes) {
        try {
            userService.changePassword(userDetails.getId(), request);
            redirectAttributes.addFlashAttribute("successMessage", "Đổi mật khẩu thành công!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/customer/profile";
    }

    @GetMapping("/addresses")
    public String listAddresses(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        model.addAttribute("addresses", userService.getCustomerAddresses(userDetails.getId()));
        if (!model.containsAttribute("addressForm")) {
            model.addAttribute("addressForm", new AddressCreateDto());
        }
        return "customer/addresses";
    }

    @PostMapping("/addresses/add")
    public String addAddress(@AuthenticationPrincipal CustomUserDetails userDetails,
                             @Valid @ModelAttribute("addressForm") AddressCreateDto request,
                             RedirectAttributes redirectAttributes) {
        userService.addAddress(userDetails.getId(), request);
        redirectAttributes.addFlashAttribute("successMessage", "Thêm địa chỉ giao hàng thành công!");
        return "redirect:/customer/addresses";
    }

    @PostMapping("/addresses/{id}/set-default")
    public String setDefaultAddress(@AuthenticationPrincipal CustomUserDetails userDetails,
                                    @PathVariable Long id,
                                    RedirectAttributes redirectAttributes) {
        userService.setDefaultAddress(userDetails.getId(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã đặt làm địa chỉ mặc định!");
        return "redirect:/customer/addresses";
    }

    @PostMapping("/addresses/{id}/delete")
    public String deleteAddress(@AuthenticationPrincipal CustomUserDetails userDetails,
                                @PathVariable Long id,
                                RedirectAttributes redirectAttributes) {
        userService.deleteAddress(userDetails.getId(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa địa chỉ thành công!");
        return "redirect:/customer/addresses";
    }
}
