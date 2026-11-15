package com.petshop.controller.customer;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customer/wishlist")
@RequiredArgsConstructor
public class WishlistController {
    private final WishlistService wishlistService;

    @GetMapping
    public String list(@AuthenticationPrincipal CustomUserDetails user, Model model) {
        model.addAttribute("items", wishlistService.getWishlist(user.getId()));
        return "customer/wishlist";
    }

    @PostMapping("/add/{productId}")
    public Object add(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long productId,
                      @RequestHeader(value = "Accept", required = false) String accept,
                      RedirectAttributes redirect) {
        try {
            wishlistService.add(user.getId(), productId);
            if (accept != null && accept.contains("application/json")) {
                return org.springframework.http.ResponseEntity.ok(java.util.Map.of("saved", true,
                        "message", "Đã lưu sản phẩm vào danh sách yêu thích."));
            }
            redirect.addFlashAttribute("successMessage", "Đã lưu sản phẩm vào danh sách yêu thích.");
        } catch (IllegalArgumentException e) {
            if (accept != null && accept.contains("application/json")) {
                return org.springframework.http.ResponseEntity.badRequest()
                        .body(java.util.Map.of("saved", false, "message", e.getMessage()));
            }
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/customer/wishlist";
    }

    @PostMapping("/remove/{productId}")
    public Object remove(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long productId,
                         @RequestHeader(value = "Accept", required = false) String accept,
                         RedirectAttributes redirect) {
        wishlistService.remove(user.getId(), productId);
        if (accept != null && accept.contains("application/json")) {
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of("saved", false,
                    "message", "Đã gỡ sản phẩm khỏi danh sách yêu thích."));
        }
        redirect.addFlashAttribute("successMessage", "Đã gỡ sản phẩm khỏi danh sách yêu thích.");
        return "redirect:/customer/wishlist";
    }
}
