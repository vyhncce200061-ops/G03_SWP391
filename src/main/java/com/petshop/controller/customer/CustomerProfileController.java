package com.petshop.controller.customer;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.auth.AddressCreateDto;
import com.petshop.dto.auth.AddressUpdateDto;
import com.petshop.dto.auth.PasswordChangeRequestDto;
import com.petshop.dto.auth.ProfileUpdateRequestDto;
import com.petshop.dto.cart.CartDto;
import com.petshop.entity.Order;
import com.petshop.entity.User;
import com.petshop.entity.UserAddress;
import com.petshop.entity.enums.OrderStatus;
import com.petshop.repository.OrderRepository;
import com.petshop.service.CartService;
import com.petshop.service.OrderService;
import com.petshop.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/customer")
@RequiredArgsConstructor
public class CustomerProfileController {

    private final UserService userService;
    private final OrderService orderService;
    private final CartService cartService;
    private final OrderRepository orderRepository;

    @GetMapping("/profile")
    public String viewProfile(@AuthenticationPrincipal CustomUserDetails userDetails,
                              @RequestParam(value = "tab", required = false, defaultValue = "info") String activeTab,
                              Model model) {
        User user = userService.findById(userDetails.getId());
        model.addAttribute("user", user);
        model.addAttribute("activeTab", activeTab);

        // 1. Sổ địa chỉ
        List<UserAddress> addresses = userService.getCustomerAddresses(userDetails.getId());
        model.addAttribute("addresses", addresses);
        model.addAttribute("addressCount", addresses.size());
        if (!model.containsAttribute("addressForm")) {
            model.addAttribute("addressForm", new AddressCreateDto());
        }

        // 2. Đơn hàng của tôi
        Page<Order> orders = orderService.getCustomerOrders(userDetails.getId(), null, PageRequest.of(0, 10));
        model.addAttribute("orders", orders);
        model.addAttribute("orderCount", orders.getTotalElements());

        long pendingCount = orderRepository.countByCustomerIdAndOrderStatus(userDetails.getId(), OrderStatus.PENDING_CONFIRMATION)
                + orderRepository.countByCustomerIdAndOrderStatus(userDetails.getId(), OrderStatus.PENDING_PAYMENT);
        long shippingCount = orderRepository.countByCustomerIdAndOrderStatus(userDetails.getId(), OrderStatus.SHIPPING)
                + orderRepository.countByCustomerIdAndOrderStatus(userDetails.getId(), OrderStatus.PREPARING);
        long completedCount = orderRepository.countByCustomerIdAndOrderStatus(userDetails.getId(), OrderStatus.COMPLETED);
        long cancelledCount = orderRepository.countByCustomerIdAndOrderStatus(userDetails.getId(), OrderStatus.CANCELLED);

        model.addAttribute("pendingOrderCount", pendingCount);
        model.addAttribute("shippingOrderCount", shippingCount);
        model.addAttribute("completedOrderCount", completedCount);
        model.addAttribute("cancelledOrderCount", cancelledCount);

        // 3. Giỏ hàng hiện tại
        CartDto cart = cartService.getCartForUser(userDetails.getId());
        model.addAttribute("cart", cart);
        model.addAttribute("cartCount", cart != null ? cart.getTotalQuantity() : 0);

        return "customer/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@AuthenticationPrincipal CustomUserDetails userDetails,
                                @Valid @ModelAttribute ProfileUpdateRequestDto request,
                                BindingResult bindingResult,
                                @RequestParam(value = "avatarFile", required = false) org.springframework.web.multipart.MultipartFile avatarFile,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/customer/profile";
        }

        try {
            userService.updateProfile(userDetails.getId(), request);
            if (avatarFile != null && !avatarFile.isEmpty()) {
                userService.updateAvatar(userDetails.getId(), avatarFile);
            }
            refreshPrincipal(userDetails.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin cá nhân thành công!");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/customer/profile";
    }

    @PostMapping("/avatar")
    public String uploadAvatar(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @RequestParam(value = "avatarFile", required = false) org.springframework.web.multipart.MultipartFile avatarFile,
                               RedirectAttributes redirectAttributes) {
        try {
            userService.updateAvatar(userDetails.getId(), avatarFile);
            refreshPrincipal(userDetails.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật ảnh đại diện thành công!");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi tải ảnh đại diện: " + e.getMessage());
        }
        return "redirect:/customer/profile";
    }

    @PostMapping("/change-password")
    public String changePassword(@AuthenticationPrincipal CustomUserDetails userDetails,
                                 @Valid @ModelAttribute PasswordChangeRequestDto request,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/customer/profile";
        }
        try {
            userService.changePassword(userDetails.getId(), request);
            refreshPrincipal(userDetails.getId());
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
                             BindingResult bindingResult,
                             @RequestParam(value = "source", required = false) String source,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return redirectAddressSource(source);
        }
        try {
            userService.addAddress(userDetails.getId(), request);
            redirectAttributes.addFlashAttribute("successMessage", "Thêm địa chỉ giao hàng thành công!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirectAddressSource(source);
    }

    @PostMapping("/addresses/{id}/edit")
    public String updateAddress(@AuthenticationPrincipal CustomUserDetails userDetails,
                                @PathVariable Long id,
                                @Valid @ModelAttribute("addressForm") AddressUpdateDto request,
                                BindingResult bindingResult,
                                @RequestParam(value = "source", required = false) String source,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return redirectAddressSource(source);
        }
        try {
            userService.updateAddress(userDetails.getId(), id, request);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật địa chỉ giao hàng thành công!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirectAddressSource(source);
    }

    @PostMapping("/addresses/{id}/set-default")
    public String setDefaultAddress(@AuthenticationPrincipal CustomUserDetails userDetails,
                                    @PathVariable Long id,
                                    @RequestParam(value = "source", required = false) String source,
                                    RedirectAttributes redirectAttributes) {
        try {
            userService.setDefaultAddress(userDetails.getId(), id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã đặt làm địa chỉ mặc định!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirectAddressSource(source);
    }

    @PostMapping("/addresses/{id}/delete")
    public String deleteAddress(@AuthenticationPrincipal CustomUserDetails userDetails,
                                @PathVariable Long id,
                                @RequestParam(value = "source", required = false) String source,
                                RedirectAttributes redirectAttributes) {
        try {
            userService.deleteAddress(userDetails.getId(), id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa địa chỉ thành công!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirectAddressSource(source);
    }

    private String redirectAddressSource(String source) {
        if ("profile".equalsIgnoreCase(source)) {
            return "redirect:/customer/profile?tab=addresses";
        }
        return "redirect:/customer/addresses";
    }

    private void refreshPrincipal(Long userId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails) {
            User updated = userService.findById(userId);
            CustomUserDetails newDetails = new CustomUserDetails(updated);
            var newAuth = new UsernamePasswordAuthenticationToken(
                    newDetails, newDetails.getPassword(), newDetails.getAuthorities());
            newAuth.setDetails(auth.getDetails());
            SecurityContextHolder.getContext().setAuthentication(newAuth);
        }
    }
}
